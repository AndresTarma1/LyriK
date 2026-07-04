package com.example.melodist.windows

import com.sun.jna.Function
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.Structure
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import io.github.aakira.napier.Napier
import java.awt.Window
import java.io.File

/**
 * Windows taskbar thumbnail toolbar (ITaskbarList3) — adds Previous / Play-Pause / Next buttons to
 * the taskbar thumbnail preview, Spotify-style. Implemented with raw JNA COM (jna-platform doesn't
 * ship ITaskbarList3), invoking the interface vtable directly.
 *
 * Everything is wrapped in try/catch: if anything fails the app keeps working without the thumb bar.
 */
class WindowsThumbBar(
    private val onPrevious: () -> Unit,
    private val onPlayPause: () -> Unit,
    private val onNext: () -> Unit,
) {
    companion object {
        private const val ID_PREV = 1
        private const val ID_PLAYPAUSE = 2
        private const val ID_NEXT = 3

        private const val WM_COMMAND = 0x0111
        private const val THBN_CLICKED = 0x1800
        private const val GWLP_WNDPROC = -4

        private const val THB_ICON = 0x2
        private const val THB_TOOLTIP = 0x4
        private const val THB_FLAGS = 0x8
        private const val THBF_ENABLED = 0x0

        private const val IMAGE_ICON = 1
        private const val LR_LOADFROMFILE = 0x10
        private const val LR_DEFAULTSIZE = 0x40

        // ITaskbarList3 vtable indices (IUnknown 0-2, ITaskbarList 3-7, ITaskbarList2 8, ITaskbarList3 9+).
        private const val V_HRINIT = 3
        private const val V_THUMBBARADDBUTTONS = 15
        private const val V_THUMBBARUPDATEBUTTONS = 16

        private const val CLSCTX_INPROC_SERVER = 0x1
        private const val COINIT_APARTMENTTHREADED = 0x2
        private val CLSID_TaskbarList = Guid.GUID.fromString("{56FDF344-FD6D-11D0-958A-006097C9A090}")
        private val IID_ITaskbarList3 = Guid.GUID.fromString("{EA1AFB91-9E28-4B86-90E9-9E9F8A5EEFAF}")
    }

    private interface User32X : StdCallLibrary {
        fun LoadImageW(hinst: Pointer?, name: WString, type: Int, cx: Int, cy: Int, fuLoad: Int): Pointer?
        fun SetWindowLongPtrW(hWnd: Pointer, nIndex: Int, dwNewLong: WndProc): Pointer?
        fun CallWindowProcW(prev: Pointer, hWnd: Pointer, msg: Int, wParam: Pointer?, lParam: Pointer?): Pointer?
        fun RegisterWindowMessageW(lpString: WString): Int
        companion object {
            val INSTANCE: User32X = Native.load("user32", User32X::class.java)
        }
    }

    /** Subclassed window procedure: catch thumb-button clicks, forward everything else. */
    interface WndProc : StdCallLibrary.StdCallCallback {
        fun callback(hWnd: Pointer, uMsg: Int, wParam: Pointer?, lParam: Pointer?): Pointer?
    }

    @Structure.FieldOrder("dwMask", "iId", "iBitmap", "hIcon", "szTip", "dwFlags")
    class THUMBBUTTON : Structure() {
        @JvmField var dwMask: Int = 0
        @JvmField var iId: Int = 0
        @JvmField var iBitmap: Int = 0
        @JvmField var hIcon: Pointer? = null
        @JvmField var szTip: CharArray = CharArray(260)
        @JvmField var dwFlags: Int = 0
    }

    private var taskbar: Pointer? = null
    private var hwnd: Pointer? = null
    private var oldWndProc: Pointer? = null
    private var taskbarButtonCreatedMsg = 0
    @Volatile private var isPlaying = false
    @Volatile private var buttonsAdded = false
    private val icons = HashMap<String, Pointer>()

    // Held as a field so the native function pointer isn't GC'd while installed.
    private val wndProc = object : WndProc {
        override fun callback(hWnd: Pointer, uMsg: Int, wParam: Pointer?, lParam: Pointer?): Pointer? {
            // Windows creates the taskbar button asynchronously; only then do thumb buttons stick.
            if (uMsg != 0 && uMsg == taskbarButtonCreatedMsg) {
                Napier.i("[thumbbar] TaskbarButtonCreated received")
                // The taskbar button is destroyed when the window hides to tray and recreated on
                // restore, losing its thumb buttons. Reset so we re-ADD (not update) the fresh button.
                buttonsAdded = false
                safe { addButtons() }
            }
            if (uMsg == WM_COMMAND && wParam != null) {
                val w = Pointer.nativeValue(wParam)
                if (((w ushr 16) and 0xFFFF).toInt() == THBN_CLICKED) {
                    when ((w and 0xFFFF).toInt()) {
                        ID_PREV -> safe(onPrevious)
                        ID_PLAYPAUSE -> safe(onPlayPause)
                        ID_NEXT -> safe(onNext)
                    }
                    return Pointer(0)
                }
            }
            return User32X.INSTANCE.CallWindowProcW(oldWndProc!!, hWnd, uMsg, wParam, lParam)
        }
    }

    private fun safe(block: () -> Unit) = runCatching { block() }

    /** Initialise on the AWT/UI (STA) thread, after the window has a native handle. */
    fun init(window: Window) {
        try {
            val handle = Native.getWindowPointer(window) ?: run {
                Napier.w("[thumbbar] no window handle"); return
            }
            hwnd = handle
            Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, COINIT_APARTMENTTHREADED)
            val ref = PointerByReference()
            val hr = Ole32.INSTANCE.CoCreateInstance(CLSID_TaskbarList, Pointer.NULL, CLSCTX_INPROC_SERVER, IID_ITaskbarList3, ref)
            if (hr.toInt() != 0 || ref.value == null) {
                Napier.w("[thumbbar] CoCreateInstance failed hr=${hr.toInt()}"); return
            }
            taskbar = ref.value
            val hrInit = vtable(V_HRINIT).invokeInt(arrayOf(taskbar))

            for (n in listOf("play", "pause", "prev", "next")) loadIcon(n)?.let { icons[n] = it }
            Napier.i("[thumbbar] hrInit=$hrInit, icons=${icons.size}/4")

            // Register the message Windows posts once the taskbar button exists, then subclass the
            // window proc to catch it (and the button clicks).
            taskbarButtonCreatedMsg = User32X.INSTANCE.RegisterWindowMessageW(WString("TaskbarButtonCreated"))
            oldWndProc = User32X.INSTANCE.SetWindowLongPtrW(handle, GWLP_WNDPROC, wndProc)
            Napier.i("[thumbbar] msg=$taskbarButtonCreatedMsg, subclassed=${oldWndProc != null}")

            // Do NOT add buttons here: ThumbBarAddButtons only works once the taskbar button exists,
            // and can be called only once. We add when the TaskbarButtonCreated message arrives.
            Napier.i("[thumbbar] installed; waiting for TaskbarButtonCreated")
        } catch (e: Throwable) {
            Napier.e("[thumbbar] init failed: ${e.message}")
        }
    }

    /** Toggle the middle button between Play and Pause glyphs. */
    fun setPlaying(playing: Boolean) {
        if (playing == isPlaying) return
        isPlaying = playing
        if (taskbar == null || hwnd == null) return
        runCatching { addButtons() }.onFailure { Napier.w("[thumbbar] update failed: ${it.message}") }
    }

    private fun addButtons() {
        val tb = taskbar ?: return
        val h = hwnd ?: return
        val buttons = buildButtons()
        // If already added, an "add" call is rejected (E_INVALIDARG) — use update instead.
        val index = if (buttonsAdded) V_THUMBBARUPDATEBUTTONS else V_THUMBBARADDBUTTONS
        val hr = vtable(index).invokeInt(arrayOf(tb, h, buttons.size, buttons.first().pointer))
        Napier.i("[thumbbar] ${if (buttonsAdded) "update" else "add"}Buttons hr=$hr (0=OK)")
        if (hr == 0) buttonsAdded = true
    }

    private fun buildButtons(): List<THUMBBUTTON> {
        @Suppress("UNCHECKED_CAST")
        val arr = (THUMBBUTTON().toArray(3) as Array<THUMBBUTTON>)
        configure(arr[0], ID_PREV, icons["prev"], "Anterior")
        configure(arr[1], ID_PLAYPAUSE, icons[if (isPlaying) "pause" else "play"], "Reproducir/Pausar")
        configure(arr[2], ID_NEXT, icons["next"], "Siguiente")
        arr.forEach { it.write() }
        return arr.toList()
    }

    private fun configure(b: THUMBBUTTON, id: Int, icon: Pointer?, tip: String) {
        b.iId = id
        b.dwMask = THB_ICON or THB_TOOLTIP or THB_FLAGS
        b.hIcon = icon
        b.dwFlags = THBF_ENABLED
        val chars = tip.take(258).toCharArray()
        java.util.Arrays.fill(b.szTip, ' ')
        System.arraycopy(chars, 0, b.szTip, 0, chars.size)
    }

    private fun vtable(index: Int): Function {
        val obj = taskbar!!
        val vt = obj.getPointer(0)
        val fn = vt.getPointer(index.toLong() * Native.POINTER_SIZE)
        return Function.getFunction(fn)
    }

    private fun loadIcon(name: String): Pointer? = runCatching {
        val tmp = File.createTempFile("lyrik-thb-$name", ".ico").apply { deleteOnExit() }
        javaClass.getResourceAsStream("/thumbbar/$name.ico")?.use { input ->
            tmp.outputStream().use { input.copyTo(it) }
        } ?: return@runCatching null
        User32X.INSTANCE.LoadImageW(null, WString(tmp.absolutePath), IMAGE_ICON, 0, 0, LR_LOADFROMFILE or LR_DEFAULTSIZE)
    }.getOrNull()
}
