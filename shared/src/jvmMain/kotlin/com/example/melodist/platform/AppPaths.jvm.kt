package com.example.melodist.platform

import java.io.File
import java.util.logging.Logger

actual object AppPaths {
    actual val appName: String = "Melodist"

    private val log = Logger.getLogger("AppPaths")

    private fun localAppDataBase(): File {
        val fromEnv = System.getenv("LOCALAPPDATA")
        return when {
            !fromEnv.isNullOrBlank() -> File(fromEnv)
            else -> File(File(System.getProperty("user.home"), "AppData"), "Local")
        }
    }

    private val appRootFile: File by lazy { File(localAppDataBase(), appName) }

    actual val dataRoot: String get() = appRootFile.absolutePath
    actual val configRoot: String get() = appRootFile.absolutePath
    actual val databaseDir: String get() = appRootFile.absolutePath
    actual val cacheDir: String get() = File(appRootFile, "cache").absolutePath
    actual val imageCacheDir: String get() = File(File(appRootFile, "cache"), "image_cache").absolutePath
    actual val songsDir: String get() = File(File(appRootFile, "cache"), "songs").absolutePath
    actual val tmpDir: String get() = File(appRootFile, "tmp").absolutePath
    actual val logsDir: String get() = File(appRootFile, "logs").absolutePath

    actual val preferencesFile: String get() = File(appRootFile, "settings.properties").absolutePath
    actual val cookieFile: String get() = File(appRootFile, "yt_cookie.txt").absolutePath

    actual fun ensureDirectories() {
        listOf(
            dataRoot,
            configRoot,
            databaseDir,
            cacheDir,
            imageCacheDir,
            songsDir,
            tmpDir,
            logsDir,
        ).forEach { path ->
            val dir = File(path)
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

