package com.example.melodist.data

import com.example.melodist.platform.AppPaths
import java.io.File

/**
 * Wrapper JVM para mantener compatibilidad con java.io.File.
 *
 * Las rutas reales se resuelven via [AppPaths] (expect/actual) para escalar
 * a otros targets sin acoplar commonMain a APIs JVM.
 */
object AppDirs {

    val dataRoot: File by lazy { File(AppPaths.dataRoot) }

    // Mantener config dentro de la misma raiz para concentrar todo en .melodist
    val configRoot: File get() = File(AppPaths.configRoot)

    // Subdirectorios
    val databaseDir: File get() = File(AppPaths.databaseDir)
    val cacheDir: File get() = File(AppPaths.cacheDir)
    val imageCacheDir: File get() = File(AppPaths.imageCacheDir)
    val songsDir: File get() = File(AppPaths.songsDir)
    val tmpDir: File get() = File(AppPaths.tmpDir)
    val logsDir: File get() = File(AppPaths.logsDir)

    val preferencesFile: File get() = File(AppPaths.preferencesFile)
    val cookieFile: File get() = File(AppPaths.cookieFile)

    /**
     * Crea todas las carpetas necesarias de forma idempotente.
     */
    fun ensureDirectories() {
        AppPaths.ensureDirectories()
    }
}
