package com.example.melodist.platform

/**
 * Contrato multiplataforma para rutas de la aplicacion.
 *
 * commonMain depende solo de Strings para evitar acoplarse a java.io.File.
 */
expect object AppPaths {
    val appName: String

    val dataRoot: String
    val configRoot: String
    val databaseDir: String
    val cacheDir: String
    val imageCacheDir: String
    val songsDir: String
    val tmpDir: String
    val logsDir: String

    val preferencesFile: String
    val cookieFile: String

    fun ensureDirectories()
}

