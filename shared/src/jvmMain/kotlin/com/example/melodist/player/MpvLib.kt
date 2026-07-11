package com.example.melodist.player

import com.example.melodist.platform.Platform
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

    // --- Event API ---
    /** Subscribe to change events for [name]; events carry [replyUserdata] back. */
    fun mpv_observe_property(handle: Pointer, replyUserdata: Long, name: String, format: Int): Int
    /** Blocks up to [timeout] seconds (negative = forever) for the next event. Returns a pointer
     *  to a static mpv_event owned by mpv (valid until the next mpv_wait_event on this handle). */
    fun mpv_wait_event(handle: Pointer, timeout: Double): Pointer?
    /** Interrupts a blocking mpv_wait_event (used to unblock the event thread on shutdown). */
    fun mpv_wakeup(handle: Pointer)

    companion object {
        // mpv_event_id (client.h)
        const val MPV_EVENT_NONE = 0
        const val MPV_EVENT_SHUTDOWN = 1
        const val MPV_EVENT_END_FILE = 7
        const val MPV_EVENT_FILE_LOADED = 8
        const val MPV_EVENT_PLAYBACK_RESTART = 21
        const val MPV_EVENT_PROPERTY_CHANGE = 22

        // mpv_end_file_reason
        const val MPV_END_FILE_REASON_EOF = 0
        const val MPV_END_FILE_REASON_STOP = 2
        const val MPV_END_FILE_REASON_QUIT = 3
        const val MPV_END_FILE_REASON_ERROR = 4
        const val MPV_END_FILE_REASON_REDIRECT = 5

        // mpv_format
        const val MPV_FORMAT_NONE = 0
        const val MPV_FORMAT_FLAG = 3
        const val MPV_FORMAT_INT64 = 4
        const val MPV_FORMAT_DOUBLE = 5

        private val log = Logger.getLogger("MpvLib")

        val INSTANCE: MpvLib by lazy {
            if (Platform.isWindows) loadWindows() else loadUnix()
        }

        /**
         * Windows: libmpv is bundled as `libmpv-2.dll` next to the app; point JNA at that dir and
         * load by JNA's short name (it maps to `libmpv-2.dll`).
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
         * Linux/macOS: prefer the system libmpv (mpv is packaged by every distro; bundling it is
         * fragile because of glibc/dependency skew). Distros ship the versioned `libmpv.so.2` and
         * only add the unversioned `libmpv.so` symlink with the `-dev` package, so search the
         * versioned names by absolute path first, then fall back to JNA's own name resolution
         * (which finds `libmpv.so` / `libmpv.dylib` when present, e.g. via LD_LIBRARY_PATH).
         */
        private fun loadUnix(): MpvLib {
            val soNames = if (Platform.isMac)
                listOf("libmpv.2.dylib", "libmpv.dylib")
            else
                listOf("libmpv.so.2", "libmpv.so.1", "libmpv.so")

            val searchDirs = listOf(
                "/usr/lib/x86_64-linux-gnu", // Debian/Ubuntu multiarch
                "/usr/lib64",                // Fedora/RHEL/openSUSE
                "/usr/lib",
                "/usr/local/lib",
                "/opt/homebrew/lib",         // macOS arm64
                "/usr/local/opt/mpv/lib",    // macOS Homebrew mpv keg
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

            // Last resort: let JNA resolve "mpv" via the linker (LD_LIBRARY_PATH, default paths).
            log.info("MpvLib: libmpv del sistema no encontrada en rutas conocidas; probando resolución por JNA")
            return Native.load("mpv", MpvLib::class.java)
        }
    }
}
