package com.example.melodist.data.account

import com.metrolist.innertube.YouTube
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.logging.Logger

/**
 * Persiste la cookie de YouTube Music en almacenamiento local de preferencias.
 */
actual object AccountManager {

    private val log = Logger.getLogger("AccountManager")
    private val cookieKey = stringPreferencesKey("yt_music_cookie")
    private lateinit var dataStore: DataStore<Preferences>

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

    actual fun init(dataStore: DataStore<Preferences>) {
        this.dataStore = dataStore
        val saved = readFromLocalStorage()
        if (!saved.isNullOrBlank()) {
            applyToYouTube(saved)
            _loginState.value = true
            log.info("AccountManager: sesion restaurada desde DataStore (${saved.length} chars)")
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
        log.info("AccountManager: cookie guardada en DataStore (${trimmed.length} chars)")
    }

    actual fun clearCookie() {
        clearLocalStorage()
        YouTube.cookie = null
        YouTube.useLoginForBrowse = false
        _loginState.value = false
        log.info("AccountManager: sesion cerrada")
    }

    actual fun getCookie(): String? = readFromLocalStorage()?.takeIf { it.isNotBlank() }

    actual val isLoggedIn: Boolean get() = getCookie() != null

    private fun saveToLocalStorage(cookie: String) {
        val store = getDataStoreOrNull() ?: return
        runBlocking(Dispatchers.IO) {
            try {
                store.edit { prefs ->
                    prefs[cookieKey] = cookie
                }
            } catch (e: Exception) {
                log.severe("AccountManager: no se pudo guardar la cookie en DataStore: ${e.message}")
            }
        }
    }

    private fun readFromLocalStorage(): String? {
        val store = getDataStoreOrNull() ?: return null
        return runBlocking(Dispatchers.IO) {
            try {
                store.data.first()[cookieKey]?.trim()?.ifBlank { null }
            } catch (e: Exception) {
                log.warning("AccountManager: no se pudo leer la cookie de DataStore: ${e.message}")
                null
            }
        }
    }

    private fun clearLocalStorage() {
        val store = getDataStoreOrNull() ?: return
        runBlocking(Dispatchers.IO) {
            try {
                store.edit { prefs ->
                    prefs.remove(cookieKey)
                }
            } catch (e: Exception) {
                log.warning("AccountManager: no se pudo limpiar DataStore: ${e.message}")
            }
        }
    }

    private fun getDataStoreOrNull(): DataStore<Preferences>? {
        return if (this::dataStore.isInitialized) dataStore else {
            log.warning("AccountManager: DataStore no inicializado; omitiendo persistencia de cookie")
            null
        }
    }

    private fun applyToYouTube(cookie: String) {
        YouTube.cookie = cookie
        YouTube.useLoginForBrowse = true
    }
}
