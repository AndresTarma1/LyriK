package com.example.musicApp.player

import com.example.musicApp.platform.Platform
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.io.File
import java.util.logging.Logger

interface MpvLib : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(handle: Pointer): Int
    fun mpv_command(handle: Pointer, args: Array<String?>): Int
    fun mpv_terminate_destroy(handle: Pointer)
    fun mpv_set_property_string(handle: Pointer, name: String, value: String): Int
    fun mpv_get_property_string(handle: Pointer, name: String): Pointer
    fun mpv_free(data: Pointer)

    // --- API de eventos ---
    /** Suscribirse a eventos de cambio para [name]; los eventos devuelven [replyUserdata]. */
    fun mpv_observe_property(handle: Pointer, replyUserdata: Long, name: String, format: Int): Int
    /** Bloquea hasta [timeout] segundos (negativo = indefinidamente) para el siguiente evento.
     *  Devuelve un puntero a un mpv_event estático poseído por mpv (válido hasta el siguiente
     *  mpv_wait_event en este handle). */
    fun mpv_wait_event(handle: Pointer, timeout: Double): Pointer?
    /** Interrumpe un mpv_wait_event bloqueante (se usa para desbloquear el hilo de eventos al cerrar). */
    fun mpv_wakeup(handle: Pointer)

    companion object {
        // mpv_event_id (client.h) — identificadores de eventos de mpv
        const val MPV_EVENT_NONE = 0
        const val MPV_EVENT_SHUTDOWN = 1
        const val MPV_EVENT_END_FILE = 7
        const val MPV_EVENT_FILE_LOADED = 8
        const val MPV_EVENT_PLAYBACK_RESTART = 21
        const val MPV_EVENT_PROPERTY_CHANGE = 22

        // mpv_end_file_reason — razones de fin de archivo
        const val MPV_END_FILE_REASON_EOF = 0
        const val MPV_END_FILE_REASON_STOP = 2
        const val MPV_END_FILE_REASON_QUIT = 3
        const val MPV_END_FILE_REASON_ERROR = 4
        const val MPV_END_FILE_REASON_REDIRECT = 5

        // mpv_format — formatos de datos de mpv
        const val MPV_FORMAT_NONE = 0
        const val MPV_FORMAT_FLAG = 3
        const val MPV_FORMAT_INT64 = 4
        const val MPV_FORMAT_DOUBLE = 5

        private val log = Logger.getLogger("MpvLib")

        val INSTANCE: MpvLib by lazy {
            if (Platform.isWindows) loadWindows() else loadUnix()
        }

        /**
         * Windows: libmpv está empaquetado como `libmpv-2.dll` junto a la aplicación; se apunta
         * JNA a ese directorio y se carga por el nombre corto de JNA (se mapea a `libmpv-2.dll`).
         */
        private fun loadWindows(): MpvLib {
            val userDir = File(System.getProperty("user.dir"))
            val rootDir = userDir.parentFile ?: userDir
            val resProp = System.getProperty("compose.application.resources.dir")
            val possibleDirs = mutableListOf(
                File(userDir, "resources"),
                File(userDir, "app/resources"),
                File(userDir, "app/app/resources"),
                File(userDir, "mpv-resources/windows"),
                File(userDir, "mpv-resources"),
                File(rootDir, "mpv-resources/windows"),
                File(rootDir, "mpv-resources"),
                File(rootDir, "resources"),
            )
            if (resProp != null) {
                possibleDirs.add(File(resProp))
                possibleDirs.add(File(resProp, "windows"))
            }

            val libraryDir = possibleDirs.firstOrNull { File(it, "libmpv-2.dll").exists() }
            if (libraryDir != null) {
                System.setProperty("jna.library.path", libraryDir.absolutePath)
                log.info("MpvLib: cargando libmpv desde ${libraryDir.absolutePath}")
            } else {
                log.warning(
                    "MpvLib: no se encontró libmpv-2.dll en: ${possibleDirs.joinToString { it.absolutePath }}"
                )
            }

            return Native.load("libmpv-2", MpvLib::class.java)
        }

        /**
         * Linux/macOS: se prefiere el libmpv del sistema (mpv está empaquetado en cada distro;
         * empaquetarlo es frágil debido a diferencias de glibc/dependencias). Las distribuciones
         * incluyen `libmpv.so.2` versionado y solo añaden el enlace simbólico `libmpv.so` sin
         * versión con el paquete `-dev`, por lo que se buscan primero los nombres versionados por
         * ruta absoluta y luego se recurre a la resolución de nombres de JNA (que encuentra
         * `libmpv.so` / `libmpv.dylib` cuando están presentes, por ejemplo vía LD_LIBRARY_PATH).
         */
        private fun loadUnix(): MpvLib {
            val soNames = if (Platform.isMac)
                listOf("libmpv.2.dylib", "libmpv.dylib")
            else
                listOf("libmpv.so.2", "libmpv.so.1", "libmpv.so")

            val searchDirs = listOf(
                "/usr/lib/x86_64-linux-gnu", // Multiarquitectura Debian/Ubuntu
                "/usr/lib64",                // Fedora/RHEL/openSUSE
                "/usr/lib",
                "/usr/local/lib",
                "/opt/homebrew/lib",         // macOS arm64
                "/usr/local/opt/mpv/lib",    // Keg de mpv en Homebrew de macOS
                "/app/lib",                  // Flatpak
            )

            for (dir in searchDirs) {
                for (name in soNames) {
                    val f = File(dir, name)
                    if (f.exists()) {
                        log.info("MpvLib: cargando libmpv del sistema desde ${f.absolutePath}")
                        return Native.load(f.absolutePath, MpvLib::class.java)
                    }
                }
            }

            // Último recurso: dejar que JNA resuelva "mpv" mediante el enlazador (LD_LIBRARY_PATH, rutas predeterminadas).
            log.info("MpvLib: libmpv del sistema no encontrada en rutas conocidas; probando resolución por JNA")
            return Native.load("mpv", MpvLib::class.java)
        }
    }
}
