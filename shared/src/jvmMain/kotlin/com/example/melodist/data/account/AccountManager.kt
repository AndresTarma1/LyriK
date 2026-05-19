package com.example.melodist.data.account

import com.example.melodist.data.AppDirs
import com.metrolist.innertube.YouTube
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.prefs.Preferences
import java.util.logging.Logger

/**
 * Persiste la cookie de YouTube Music en almacenamiento local de preferencias.
 */
actual object AccountManager {

    private val log = Logger.getLogger("AccountManager")
    private const val PREF_NODE = "com/example/melodist/account"
    private const val COOKIE_CHUNK_PREFIX = "yt_music_cookie_chunk_"
    private const val COOKIE_CHUNK_COUNT = "yt_music_cookie_chunk_count"
    private const val LEGACY_COOKIE_KEY = "yt_music_cookie"
    private const val CHUNK_SIZE = 3_500

    private val prefs: Preferences by lazy {
        Preferences.userRoot().node(PREF_NODE)
    }

    private val legacyCookieFile: File get() = AppDirs.cookieFile

    private val _loginState = MutableStateFlow(false)
    actual val loginState: StateFlow<Boolean> = _loginState.asStateFlow()

    actual fun diagnose(cookie: String): List<String> {
        val warnings = mutableListOf<String>()
        val keys = cookie.split(";").mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq == -1) null else part.substring(0, eq).trim()
        }.toSet()

        if ("SAPISID" !in keys) {
            warnings += "Falta SAPISID; la autenticacion fallara. Copia la cookie completa desde el header 'cookie' de una peticion a music.youtube.com."
        }
        if ("__Secure-3PAPISID" !in keys && "APISID" !in keys) {
            warnings += "Faltan __Secure-3PAPISID / APISID; la cookie puede estar incompleta."
        }
        if ("LOGIN_INFO" !in keys && "HSID" !in keys) {
            warnings += "Falta LOGIN_INFO / HSID; es posible que la sesion no este activa."
        }
        if (cookie.length < 200) {
            warnings += "La cookie parece demasiado corta (${cookie.length} chars). Asegurate de copiar todos los valores."
        }
        return warnings
    }

    actual fun init() {
        migrateLegacyStorageIfNeeded()

        val saved = readFromLocalStorage()
        if (!saved.isNullOrBlank()) {
            applyToYouTube(saved)
            _loginState.value = true
            log.info("AccountManager: sesion restaurada desde almacenamiento local (${saved.length} chars)")
            diagnose(saved).forEach { log.warning("AccountManager [init]: $it") }
        } else {
            log.info("AccountManager: no hay sesion guardada")
        }
    }

    actual fun setCookie(cookie: String) {
        val trimmed = cookie.trim()
        diagnose(trimmed).forEach { log.warning("AccountManager [setCookie]: $it") }

        saveToLocalStorage(trimmed)
        applyToYouTube(trimmed)
        _loginState.value = true
        log.info("AccountManager: cookie guardada en almacenamiento local (${trimmed.length} chars)")
    }

    actual fun clearCookie() {
        clearLocalStorage()
        try {
            if (legacyCookieFile.exists()) legacyCookieFile.delete()
        } catch (e: Exception) {
            log.warning("No se pudo borrar cookie legada: ${e.message}")
        }
        YouTube.cookie = null
        YouTube.useLoginForBrowse = false
        _loginState.value = false
        log.info("AccountManager: sesion cerrada")
    }

    actual fun getCookie(): String? = readFromLocalStorage()?.takeIf { it.isNotBlank() }

    actual val isLoggedIn: Boolean get() = getCookie() != null

    private fun migrateLegacyStorageIfNeeded() {
        if (!readFromLocalStorage().isNullOrBlank()) return

        val fromFile = readLegacyFile()
        if (!fromFile.isNullOrBlank()) {
            log.info("AccountManager: migrando cookie desde archivo legado (${fromFile.length} chars)")
            saveToLocalStorage(fromFile)
            try {
                legacyCookieFile.delete()
            } catch (e: Exception) {
                log.warning("No se pudo eliminar archivo legado de cookie: ${e.message}")
            }
            return
        }

        try {
            val old = prefs.get(LEGACY_COOKIE_KEY, null)
            if (!old.isNullOrBlank()) {
                log.info("AccountManager: migrando cookie desde Preferences legado (${old.length} chars)")
                saveToLocalStorage(old)
                prefs.remove(LEGACY_COOKIE_KEY)
                prefs.flush()
            }
        } catch (e: Exception) {
            log.warning("AccountManager: migracion desde Preferences legado fallo: ${e.message}")
        }
    }

    private fun saveToLocalStorage(cookie: String) {
        try {
            clearLocalStorage(flush = false)
            val chunks = cookie.chunked(CHUNK_SIZE)
            prefs.putInt(COOKIE_CHUNK_COUNT, chunks.size)
            chunks.forEachIndexed { index, chunk ->
                prefs.put("$COOKIE_CHUNK_PREFIX$index", chunk)
            }
            prefs.flush()
        } catch (e: Exception) {
            log.severe("AccountManager: no se pudo guardar la cookie en almacenamiento local: ${e.message}")
        }
    }

    private fun readFromLocalStorage(): String? {
        return try {
            val count = prefs.getInt(COOKIE_CHUNK_COUNT, 0)
            if (count <= 0) return null

            buildString {
                repeat(count) { index ->
                    append(prefs.get("$COOKIE_CHUNK_PREFIX$index", ""))
                }
            }.trim().ifBlank { null }
        } catch (e: Exception) {
            log.warning("AccountManager: no se pudo leer la cookie de almacenamiento local: ${e.message}")
            null
        }
    }

    private fun clearLocalStorage(flush: Boolean = true) {
        try {
            val count = prefs.getInt(COOKIE_CHUNK_COUNT, 0)
            repeat(count.coerceAtLeast(0)) { index ->
                prefs.remove("$COOKIE_CHUNK_PREFIX$index")
            }
            prefs.remove(COOKIE_CHUNK_COUNT)
            prefs.remove(LEGACY_COOKIE_KEY)
            if (flush) prefs.flush()
        } catch (e: Exception) {
            log.warning("AccountManager: no se pudo limpiar almacenamiento local: ${e.message}")
        }
    }

    private fun readLegacyFile(): String? {
        return try {
            if (legacyCookieFile.exists()) legacyCookieFile.readText(Charsets.UTF_8).trim().ifBlank { null }
            else null
        } catch (e: Exception) {
            log.warning("AccountManager: no se pudo leer cookie legada: ${e.message}")
            null
        }
    }

    private fun applyToYouTube(cookie: String) {
        YouTube.cookie = cookie
        YouTube.useLoginForBrowse = true
    }
}
