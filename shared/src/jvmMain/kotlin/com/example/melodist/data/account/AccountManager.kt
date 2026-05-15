package com.example.melodist.data.account

import com.example.melodist.data.AppDirs
import com.metrolist.innertube.YouTube
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.logging.Logger

/**
 * Persiste la cookie de YouTube Music en un archivo de texto plano en AppData/Local.
 */
actual object AccountManager {

    private val log = Logger.getLogger("AccountManager")

    /** Directorio de datos: %APPDATA%\melodist  (o ~/.config/melodist en Linux/macOS) */
    private val dataDir: File by lazy {
        AppDirs.configRoot.also { it.mkdirs() }
    }

    private val cookieFile: File get() = File(dataDir, "yt_cookie.txt")

    private val _loginState = MutableStateFlow(false)
    /** Emite `true` cuando hay sesión activa, `false` cuando no. */
    actual val loginState: StateFlow<Boolean> = _loginState.asStateFlow()

    // ── Diagnóstico ──────────────────────────────────────────────────────────

    /**
     * Verifica que la cookie contiene las claves mínimas necesarias para la
     * autenticación: SAPISID (para generar SAPISIDHASH) y al menos una de las
     * cookies de sesión de Google.
     * Devuelve una lista de advertencias (vacía = todo correcto).
     */
    actual fun diagnose(cookie: String): List<String> {
        val warnings = mutableListOf<String>()
        val keys = cookie.split(";").mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq == -1) null else part.substring(0, eq).trim()
        }.toSet()

        if ("SAPISID" !in keys) {
            warnings += "Falta SAPISID — la autenticación fallará. Copia la cookie completa desde el header 'cookie' de una petición a music.youtube.com."
        }
        if ("__Secure-3PAPISID" !in keys && "APISID" !in keys) {
            warnings += "Faltan __Secure-3PAPISID / APISID — la cookie puede estar incompleta."
        }
        if ("LOGIN_INFO" !in keys && "HSID" !in keys) {
            warnings += "Falta LOGIN_INFO / HSID — es posible que la sesión no esté activa."
        }
        if (cookie.length < 200) {
            warnings += "La cookie parece demasiado corta (${cookie.length} chars). Asegúrate de copiar TODOS los valores."
        }
        return warnings
    }

    // ── API pública ──────────────────────────────────────────────────────────

    /** Inicializa: si hay cookie guardada en disco, la carga en YouTube. */
    actual fun init() {
        // Migración desde java.util.prefs (sistema anterior, límite 8 KB)
        migrateFromPrefsIfNeeded()

        val saved = readFromDisk()
        if (!saved.isNullOrBlank()) {
            applyToYouTube(saved)
            _loginState.value = true
            log.info("AccountManager: sesión restaurada desde disco (${saved.length} chars, archivo: ${cookieFile.absolutePath})")
            val warnings = diagnose(saved)
            warnings.forEach { log.warning("AccountManager [init]: $it") }
        } else {
            log.info("AccountManager: no hay sesión guardada (archivo: ${cookieFile.absolutePath})")
        }
    }

    /**
     * Migra la cookie del registro de Windows (java.util.prefs) al archivo nuevo.
     * Solo se ejecuta una vez — si el archivo ya existe, no hace nada.
     */
    private fun migrateFromPrefsIfNeeded() {
        if (cookieFile.exists()) return  // ya migrado o configurado manualmente
        try {
            @Suppress("DEPRECATION")
            val prefs = java.util.prefs.Preferences.userRoot().node("com/example/melodist/account")
            val old = prefs.get("yt_music_cookie", null)
            if (!old.isNullOrBlank()) {
                log.info("AccountManager: migrando cookie desde Preferences al archivo (${old.length} chars)")
                saveToDisk(old)
                prefs.remove("yt_music_cookie")
                prefs.flush()
            }
        } catch (e: Exception) {
            log.warning("AccountManager: migración desde Preferences falló (normal si no hay cookie previa): ${e.message}")
        }
    }

    /** Guarda la cookie en disco y la aplica a YouTube. */
    actual fun setCookie(cookie: String) {
        val trimmed = cookie.trim()
        val warnings = diagnose(trimmed)
        warnings.forEach { log.warning("AccountManager [setCookie]: $it") }

        saveToDisk(trimmed)
        applyToYouTube(trimmed)
        _loginState.value = true
        log.info("AccountManager: cookie guardada (${trimmed.length} chars)")
    }

    /** Elimina la cookie de disco y de YouTube. */
    actual fun clearCookie() {
        try { cookieFile.delete() } catch (e: Exception) { log.warning("No se pudo borrar cookie: ${e.message}") }
        YouTube.cookie = null
        YouTube.useLoginForBrowse = false
        _loginState.value = false
        log.info("AccountManager: sesión cerrada")
    }

    /** Devuelve la cookie almacenada, o null. */
    actual fun getCookie(): String? = readFromDisk()?.takeIf { it.isNotBlank() }

    /** true si hay sesión activa. */
    actual val isLoggedIn: Boolean get() = getCookie() != null

    // ── Helpers privados ─────────────────────────────────────────────────────

    private fun saveToDisk(cookie: String) {
        try {
            cookieFile.writeText(cookie, Charsets.UTF_8)
        } catch (e: Exception) {
            log.severe("AccountManager: no se pudo guardar la cookie en disco: ${e.message}")
        }
    }

    private fun readFromDisk(): String? {
        return try {
            if (cookieFile.exists()) cookieFile.readText(Charsets.UTF_8).trim().ifBlank { null }
            else null
        } catch (e: Exception) {
            log.warning("AccountManager: no se pudo leer la cookie de disco: ${e.message}")
            null
        }
    }

    private fun applyToYouTube(cookie: String) {
        YouTube.cookie = cookie
        YouTube.useLoginForBrowse = true
    }
}
