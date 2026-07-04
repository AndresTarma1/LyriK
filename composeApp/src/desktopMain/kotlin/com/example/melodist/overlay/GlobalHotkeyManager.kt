package com.example.melodist.overlay

import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import io.github.aakira.napier.Napier
import java.util.logging.Level
import java.util.logging.Logger

/**
 * A global keyboard shortcut (modifiers + key). Modifiers are persisted as a packed mask
 * (bit0 ctrl, bit1 alt, bit2 shift, bit3 meta) to match [com.example.melodist.data.repository.UserPreferencesRepository].
 */
data class HotkeyCombo(
    val ctrl: Boolean,
    val alt: Boolean,
    val shift: Boolean,
    val meta: Boolean,
    val keyCode: Int,
) {
    val modsMask: Int
        get() = (if (ctrl) 1 else 0) or (if (alt) 2 else 0) or (if (shift) 4 else 0) or (if (meta) 8 else 0)

    fun label(): String = buildList {
        if (ctrl) add("Ctrl")
        if (alt) add("Alt")
        if (shift) add("Shift")
        if (meta) add("Win")
        add(NativeKeyEvent.getKeyText(keyCode))
    }.joinToString(" + ")

    companion object {
        /** Sensible default: Ctrl + Shift + M. */
        val DEFAULT = HotkeyCombo(ctrl = true, alt = false, shift = true, meta = false, keyCode = NativeKeyEvent.VC_M)

        fun fromPrefs(code: Int, mods: Int): HotkeyCombo =
            if (code == 0) DEFAULT
            else HotkeyCombo(
                ctrl = mods and 1 != 0,
                alt = mods and 2 != 0,
                shift = mods and 4 != 0,
                meta = mods and 8 != 0,
                keyCode = code,
            )
    }
}

/**
 * Registers a system-wide keyboard hook (via jnativehook) that fires [onTrigger] when the
 * configured [HotkeyCombo] is pressed — even while another app/game is focused. Also offers a
 * one-shot "capture" mode so the settings screen can record a new combo from the user.
 *
 * Callbacks are marshalled onto the AWT event thread so they can safely touch UI/Compose state.
 */
class GlobalHotkeyManager(
    private val onTrigger: () -> Unit,
) : NativeKeyListener {

    @Volatile private var combo: HotkeyCombo = HotkeyCombo.DEFAULT
    @Volatile private var enabled: Boolean = true
    @Volatile private var captureCallback: ((HotkeyCombo) -> Unit)? = null
    private var started = false

    fun start() {
        if (started) return
        // jnativehook is very chatty on java.util.logging — silence it.
        runCatching {
            val pkg = GlobalScreen::class.java.`package`?.name ?: "com.github.kwhat.jnativehook"
            Logger.getLogger(pkg).apply {
                level = Level.OFF
                useParentHandlers = false
            }
        }
        runCatching {
            GlobalScreen.registerNativeHook()
            GlobalScreen.addNativeKeyListener(this)
            started = true
        }.onFailure { Napier.e("[overlay] failed to register native hook: ${it.message}") }
    }

    fun stop() {
        runCatching {
            if (started) {
                GlobalScreen.removeNativeKeyListener(this)
                GlobalScreen.unregisterNativeHook()
                started = false
            }
        }
    }

    fun updateCombo(c: HotkeyCombo) { combo = c }
    fun setEnabled(value: Boolean) { enabled = value }

    /** Record the next non-modifier key press (with its held modifiers) as a new combo, once. */
    fun beginCapture(onCaptured: (HotkeyCombo) -> Unit) { captureCallback = onCaptured }
    fun cancelCapture() { captureCallback = null }

    override fun nativeKeyPressed(e: NativeKeyEvent) {
        val ctrl = e.modifiers and NativeKeyEvent.CTRL_MASK != 0
        val alt = e.modifiers and NativeKeyEvent.ALT_MASK != 0
        val shift = e.modifiers and NativeKeyEvent.SHIFT_MASK != 0
        val meta = e.modifiers and NativeKeyEvent.META_MASK != 0
        val code = e.keyCode

        val capture = captureCallback
        if (capture != null) {
            if (isModifierKey(code)) return // wait for a real key, not a lone modifier
            captureCallback = null
            val captured = HotkeyCombo(ctrl, alt, shift, meta, code)
            dispatch { capture(captured) }
            return
        }

        if (!enabled) return
        val target = combo
        if (code == target.keyCode &&
            ctrl == target.ctrl && alt == target.alt && shift == target.shift && meta == target.meta
        ) {
            dispatch(onTrigger)
        }
    }

    override fun nativeKeyReleased(e: NativeKeyEvent) {}
    override fun nativeKeyTyped(e: NativeKeyEvent) {}

    private fun isModifierKey(code: Int): Boolean = code == NativeKeyEvent.VC_CONTROL ||
        code == NativeKeyEvent.VC_SHIFT || code == NativeKeyEvent.VC_ALT || code == NativeKeyEvent.VC_META

    private fun dispatch(block: () -> Unit) = java.awt.EventQueue.invokeLater(block)
}
