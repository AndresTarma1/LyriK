package com.example.melodist.utils

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import io.github.aakira.napier.Napier

/**
 * Asks Windows to trim the process working set (resident RAM) back to the minimum, returning idle
 * pages to the OS — the same effect a "RAM cleaner" produces, but done by us. Pages fault back in as
 * needed, so this should only be called when the app is idle (e.g. minimized to tray) to avoid the
 * brief stutter of re-faulting. Much of a Compose Desktop app's RSS is committed-but-idle native
 * memory (Skia surfaces, thread stacks, buffers); this keeps it from sitting resident while hidden.
 *
 * No-op on non-Windows platforms or if the call fails.
 */
object WorkingSetTrimmer {
    private interface Kernel32 : StdCallLibrary {
        fun GetCurrentProcess(): Pointer
        fun SetProcessWorkingSetSize(hProcess: Pointer, min: Long, max: Long): Boolean
    }

    private val kernel32: Kernel32? by lazy {
        runCatching { Native.load("kernel32", Kernel32::class.java) }
            .onFailure { Napier.w("[mem] kernel32 unavailable: ${it.message}") }
            .getOrNull()
    }

    /** Trim the working set. Passing (-1, -1) tells Windows to reclaim as much as possible. */
    fun trim() {
        val lib = kernel32 ?: return
        runCatching {
            lib.SetProcessWorkingSetSize(lib.GetCurrentProcess(), -1L, -1L)
            Napier.d("[mem] working-set trimmed")
        }.onFailure { Napier.w("[mem] working-set trim failed: ${it.message}") }
    }
}
