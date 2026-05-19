package com.example.melodist.platform

import java.io.File
import java.util.logging.Logger

actual object AppPaths {
    actual val appName: String = "Melodist"

    private val log = Logger.getLogger("AppPaths")
    private const val VENDOR_NAME = "Tarma"

    private fun localAppDataBase(): File {
        val fromEnv = System.getenv("LOCALAPPDATA")
        return when {
            !fromEnv.isNullOrBlank() -> File(fromEnv)
            else -> File(File(System.getProperty("user.home"), "AppData"), "Local")
        }
    }

    private val legacyAppRootFile: File by lazy { File(localAppDataBase(), appName) }
    private val appRootFile: File by lazy {
        File(File(localAppDataBase(), VENDOR_NAME), appName).also { migrateLegacyDataIfNeeded(it) }
    }

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

    private fun migrateLegacyDataIfNeeded(newRoot: File) {
        val legacyRoot = legacyAppRootFile
        if (legacyRoot.absolutePath == newRoot.absolutePath || !legacyRoot.exists()) return

        try {
            if (!newRoot.exists()) {
                val created = newRoot.mkdirs()
                log.info("Creada nueva carpeta de datos: ${newRoot.absolutePath} (ok=$created)")
            }

            listOf(
                "melodist.db",
                "melodist.db-wal",
                "melodist.db-shm",
                "schema_version",
                "settings.properties",
                "yt_cookie.txt",
            ).forEach { name ->
                copyFileIfMissing(File(legacyRoot, name), File(newRoot, name))
            }

            listOf("data", "cache", "logs").forEach { name ->
                copyDirectoryIfMissing(File(legacyRoot, name), File(newRoot, name))
            }
        } catch (e: Exception) {
            log.warning("No se pudo migrar datos antiguos desde ${legacyRoot.absolutePath}: ${e.message}")
        }
    }

    private fun copyFileIfMissing(source: File, target: File) {
        if (!source.isFile || target.exists()) return
        target.parentFile?.mkdirs()
        source.copyTo(target, overwrite = false)
        log.info("Migrado archivo de datos: ${source.name}")
    }

    private fun copyDirectoryIfMissing(source: File, target: File) {
        if (!source.isDirectory) return
        source.walkTopDown().forEach { current ->
            val relativePath = current.relativeTo(source).path
            val destination = File(target, relativePath)

            if (current.isDirectory) {
                if (!destination.exists()) destination.mkdirs()
            } else {
                copyFileIfMissing(current, destination)
            }
        }
    }
}

