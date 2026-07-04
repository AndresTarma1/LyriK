package com.example.melodist.data

import com.example.melodist.platform.AppPaths
import java.io.File

/**
 * Wrapper JVM para mantener compatibilidad con java.io.File.
 *
 * Las rutas reales se resuelven via [AppPaths] (expect/actual) para escalar
 * a otros targets sin acoplar commonMain a APIs JVM.
 *
 * Dos raíces:
 * - [roamingRoot]: %APPDATA%\Tarma\LyriK\  — DB, preferencias (persistentes)
 * - [localRoot]:   %LOCALAPPDATA%\Tarma\LyriK\  — cachés, logs, tmp (limpiable)
 */
object AppDirs {

    val roamingRoot: File by lazy { File(AppPaths.roamingRoot) }
    val localRoot: File by lazy { File(AppPaths.localRoot) }

    // Persistentes
    val dataRoot: File get() = roamingRoot
    val configRoot: File get() = roamingRoot
    val databaseDir: File get() = File(AppPaths.databaseDir)
    val preferencesFile: File get() = File(AppPaths.preferencesFile)
    val cookieFile: File get() = File(AppPaths.cookieFile)

    // Volátiles
    val cacheDir: File get() = File(AppPaths.cacheDir)
    val imageCacheDir: File get() = File(AppPaths.imageCacheDir)
    val songsDir: File get() = File(AppPaths.songsDir)
    val tmpDir: File get() = File(AppPaths.tmpDir)
    val logsDir: File get() = File(AppPaths.logsDir)

    fun ensureDirectories() {
        AppPaths.ensureDirectories()
    }
}
