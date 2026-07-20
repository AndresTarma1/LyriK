package com.example.musicApp.utils

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import io.github.aakira.napier.Napier

/**
 * Solicita a Windows reducir el working set del proceso (RAM residente) al mínimo, devolviendo las
 * páginas inactivas al sistema operativo — el mismo efecto que produce un "limpiador de RAM", pero
 * ejecutado por nosotros. Las páginas se cargan de nuevo bajo demanda, por lo que esto solo debe
 * llamarse cuando la app está inactiva (ej. minimizada en la bandeja) para evitar la leve
 * interrupción de volver a cargarlas. Gran parte del RSS de una app de Compose Desktop es memoria
 * nativa comprometida pero inactiva (superficies de Skia, pilas de hilos, buffers); esto evita que
 * permanezca residente mientras está oculta.
 *
 * No hace nada en plataformas que no sean Windows o si la llamada falla.
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

    /** Reduce el working set. Pasar (-1, -1) le indica a Windows que recupere todo lo posible. */
    fun trim() {
        val lib = kernel32 ?: return
        runCatching {
            lib.SetProcessWorkingSetSize(lib.GetCurrentProcess(), -1L, -1L)
            Napier.d("[mem] working-set trimmed")
        }.onFailure { Napier.w("[mem] working-set trim failed: ${it.message}") }
    }
}
