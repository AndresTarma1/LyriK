package com.example.musicApp.bootstrap

import com.example.musicApp.data.AppDirs
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.time.LocalDateTime

object AppEnvironment {

    fun initialize() {
        AppDirs.ensureDirectories()
        // redirectStandardStreams()
        Napier.base(DebugAntilog())
        cleanupOrphanedTrayIcons()
        val tmpDir = AppDirs.tmpDir.also { it.mkdirs() }

        System.setProperty("org.sqlite.tmpdir", tmpDir.absolutePath)
        System.setProperty("java.io.tmpdir", tmpDir.absolutePath)
        System.setProperty("compose.swing.render.on.graphics", "true")

    }

    /**
     * La biblioteca de la bandeja del sistema (composenativetray) vuelve a renderizar cada icono del menú en un archivo completamente nuevo
     * "tray_icon_*.ico/.png" en cada reconstrucción del menú (cambio de canción, reproducir/pausar, ...), ignora nuestra
     * anulación de tmpdir a continuación y se basa únicamente en deleteOnExit(), que nunca se ejecuta en caso de un
     * cierre forzado. Elimina los restos de ejecuciones anteriores antes de que se acumulen indefinidamente en el directorio temporal real del sistema operativo;
     * debe ejecutarse antes de que se anule la propiedad tmpdir, ya que lee la real.
     */
    private fun cleanupOrphanedTrayIcons() {
        runCatching {
            File(System.getProperty("java.io.tmpdir"))
                .listFiles { f -> f.isFile && f.name.startsWith("tray_icon_") }
                ?.forEach { it.delete() }
        }
    }

    private fun redirectStandardStreams() {
        try {
            val sysOutFile = File(AppDirs.logsDir, "sysout.log")
            val sysErrFile = File(AppDirs.logsDir, "syserr.log")
            System.setOut(PrintStream(FileOutputStream(sysOutFile, true), true))
            System.setErr(PrintStream(FileOutputStream(sysErrFile, true), true))
            println("[${LocalDateTime.now()}] --- Application Starting ---")
            System.err.println("[${LocalDateTime.now()}] --- Application Starting ---")
        } catch (_: Exception) {
        }
    }
}

