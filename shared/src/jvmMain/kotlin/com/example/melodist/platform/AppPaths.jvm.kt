package com.example.melodist.platform

import java.io.File
import java.util.logging.Logger

actual object AppPaths {
    actual val appName: String = "LyriK"

    private val log = Logger.getLogger("AppPaths")
    private const val VENDOR_NAME = "Tarma"

    // --- Resolución de carpetas base del sistema ---

    private fun roamingBase(): File {
        val fromEnv = System.getenv("APPDATA")
        return when {
            !fromEnv.isNullOrBlank() -> File(fromEnv)
            else -> File(File(System.getProperty("user.home"), "AppData"), "Roaming")
        }
    }

    private fun localBase(): File {
        val fromEnv = System.getenv("LOCALAPPDATA")
        return when {
            !fromEnv.isNullOrBlank() -> File(fromEnv)
            else -> File(File(System.getProperty("user.home"), "AppData"), "Local")
        }
    }

    private val roamingRootFile: File by lazy {
        File(File(roamingBase(), VENDOR_NAME), appName)
    }

    private val localRootFile: File by lazy {
        File(File(localBase(), VENDOR_NAME), appName)
    }

    // --- Puntos de montaje ---

    actual val roamingRoot: String get() = roamingRootFile.absolutePath
    actual val localRoot: String get() = localRootFile.absolutePath

    // Persistentes (roaming) — %APPDATA%\Tarma\LyriK\
    actual val dataRoot: String get() = roamingRootFile.absolutePath
    actual val configRoot: String get() = roamingRootFile.absolutePath
    actual val databaseDir: String get() = File(roamingRootFile, "db").absolutePath
    actual val preferencesFile: String get() = File(roamingRootFile, "settings.properties").absolutePath
    actual val cookieFile: String get() = File(roamingRootFile, "yt_cookie.txt").absolutePath

    // Volátiles (local) — %LOCALAPPDATA%\Tarma\LyriK\
    actual val cacheDir: String get() = File(localRootFile, "cache").absolutePath
    actual val imageCacheDir: String get() = File(File(localRootFile, "cache"), "image_cache").absolutePath
    actual val songsDir: String get() = File(File(localRootFile, "cache"), "songs").absolutePath
    actual val tmpDir: String get() = File(localRootFile, "tmp").absolutePath
    actual val logsDir: String get() = File(localRootFile, "logs").absolutePath

    actual fun ensureDirectories() {
        listOf(
            roamingRootFile,
            localRootFile,
            // Persistentes
            File(roamingRootFile, "db"),
            // Volátiles
            File(localRootFile, "cache"),
            File(localRootFile, "cache/image_cache"),
            File(localRootFile, "cache/songs"),
            File(localRootFile, "tmp"),
            File(localRootFile, "logs"),
        ).forEach { dir ->
            try {
                if (!dir.exists()) {
                    val created = dir.mkdirs()
                    log.info("Creada carpeta: ${dir.absolutePath} (ok=$created)")
                }
            } catch (e: Exception) {
                log.warning("Error creando ${dir.absolutePath}: ${e.message}")
            }
        }
    }

}

