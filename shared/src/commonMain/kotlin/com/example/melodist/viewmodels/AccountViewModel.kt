package com.example.melodist.viewmodels

import androidx.lifecycle.viewModelScope
import com.example.melodist.data.account.AccountManager
import com.example.melodist.data.repository.UserPreferencesRepository
import com.example.melodist.db.DatabaseDao
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AccountInfo
import com.metrolist.innertube.models.PlaylistItem
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ─── UI States ────────────────────────────────────────────────

sealed class AccountState {
    data object NotLoggedIn : AccountState()
    data object Loading : AccountState()
    data class LoggedIn(
        val accountInfo: AccountInfo,
        val playlists: List<PlaylistItem> = emptyList(),
        val isLoadingPlaylists: Boolean = false,
    ) : AccountState()
    data class Error(val message: String) : AccountState()
    /** Cookie guardada pero expirada — pedir al usuario que la renueve */
    data object CookieExpired : AccountState()
}

// ─── ViewModel ────────────────────────────────────────────────

class AccountViewModel(
    private val databaseDao: DatabaseDao,
    private val userPreferences: UserPreferencesRepository,
) : MelodistViewModel() {

    /**
     * Detects a YouTube account switch and wipes the previous account's library so it doesn't
     * bleed into the new one (e.g. the old account's saved "Liked Music"). Keeps local playlists,
     * downloads and local history. No-op on first login or when the account is unchanged.
     */
    private suspend fun handleAccountChange(info: AccountInfo) {
        val currentId = info.email ?: info.channelHandle ?: info.name
        if (currentId.isBlank()) return
        val lastId = userPreferences.lastAccountId.first()
        if (lastId.isNotBlank() && lastId != currentId) {
            runCatching { databaseDao.clearAccountLibrary() }
                .onSuccess { Napier.i("Account changed; cleared previous account's library") }
                .onFailure { Napier.w("Failed clearing account library on switch: ${it.message}") }
        }
        if (lastId != currentId) userPreferences.setLastAccountId(currentId)
    }

    private val _uiState = MutableStateFlow<AccountState>(AccountState.NotLoggedIn)
    val uiState: StateFlow<AccountState> = _uiState.asStateFlow()

    private val _cookieInput = MutableStateFlow("")
    val cookieInput: StateFlow<String> = _cookieInput.asStateFlow()

    /** Advertencias de diagnóstico sobre la cookie pegada (claves faltantes, longitud, etc.) */
    private val _cookieWarnings = MutableStateFlow<List<String>>(emptyList())
    val cookieWarnings: StateFlow<List<String>> = _cookieWarnings.asStateFlow()

    init {
        if (AccountManager.isLoggedIn) {
            loadAccountInfo()
        }
    }

    fun onCookieInputChange(value: String) {
        _cookieInput.value = value
        _cookieWarnings.value = if (value.isBlank()) emptyList() else AccountManager.diagnose(value)
    }

    fun login() {
        val cookie = _cookieInput.value.trim()
        if (cookie.isBlank()) return
        _cookieWarnings.value = AccountManager.diagnose(cookie)
        loginInternal(cookie)
    }

    fun loginWithCookie(cookie: String) {
        if (cookie.isBlank()) return
        _cookieInput.value = cookie
        _cookieWarnings.value = emptyList()
        loginInternal(cookie)
    }

    private fun loginInternal(cookie: String) {
        _uiState.value = AccountState.Loading

        AccountManager.setCookie(cookie)

        viewModelScope.launch {
            if (YouTube.visitorData == null) {
                YouTube.visitorData().onSuccess { YouTube.visitorData = it }
            }

            YouTube.accountInfo()
                .onSuccess { info ->
                    _uiState.value = AccountState.LoggedIn(
                        accountInfo = info, isLoadingPlaylists = true
                    )
                    handleAccountChange(info)
                    loadPlaylists()
                }
                .onFailure { error ->
                    AccountManager.clearCookie()
                    _cookieInput.value = cookie
                    val msg = error.message ?: ""
                    val isNetworkError = msg.contains("timeout", true) ||
                            msg.contains("unreachable", true) ||
                            msg.contains("Unable to resolve host", true) ||
                            msg.contains("Network is unreachable", true) ||
                            msg.contains("connect", true)
                    _uiState.value = AccountState.Error(
                        if (isNetworkError) "Sin conexión a Internet. Verifica tu conexión e intenta de nuevo."
                        else "Cookie inválida o expirada. Verifica que la cookie sea correcta."
                    )
                }
        }
    }

    fun logout() {
        AccountManager.clearCookie()
        _cookieInput.value = ""
        _cookieWarnings.value = emptyList()
        _uiState.value = AccountState.NotLoggedIn
    }

    fun reset() {
        AccountManager.clearCookie()
        _cookieInput.value = ""
        _cookieWarnings.value = emptyList()
        _uiState.value = AccountState.NotLoggedIn
    }

    fun retry() {
        if (AccountManager.isLoggedIn) loadAccountInfo()
    }

    /** Llamado desde cualquier parte de la app cuando detecta un 401/403 */
    fun onAuthError() {
        if (_uiState.value is AccountState.LoggedIn) {
            _uiState.value = AccountState.CookieExpired
        }
    }

    private fun loadAccountInfo() {
        _uiState.value = AccountState.Loading
        viewModelScope.launch {
            YouTube.accountInfo()
                .onSuccess { info ->
                    _uiState.value = AccountState.LoggedIn(
                        accountInfo = info,
                        isLoadingPlaylists = true
                    )
                    handleAccountChange(info)
                    loadPlaylists()
                }
                .onFailure { err ->
                    val isAuthError = isAuthenticationError(err)
                    _uiState.value = if (isAuthError) {
                        AccountState.CookieExpired
                    } else {
                        AccountState.Error(err.message ?: "No se pudo obtener la información de la cuenta")
                    }
                }
        }
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            YouTube.library("FEmusic_liked_playlists")
                .onSuccess { page ->
                    val currentState = _uiState.value
                    if (currentState is AccountState.LoggedIn) {
                        _uiState.value = currentState.copy(
                            playlists = page.items.filterIsInstance<PlaylistItem>(),
                            isLoadingPlaylists = false
                        )
                    }
                }
                .onFailure { err ->
                    val currentState = _uiState.value
                    if (currentState is AccountState.LoggedIn) {
                        if (isAuthenticationError(err)) {
                            _uiState.value = AccountState.CookieExpired
                        } else {
                            _uiState.value = currentState.copy(isLoadingPlaylists = false)
                        }
                    }
                }
        }
    }

    fun refreshPlaylists() {
        val currentState = _uiState.value
        if (currentState !is AccountState.LoggedIn) return
        _uiState.value = currentState.copy(isLoadingPlaylists = true)
        loadPlaylists()
    }

    companion object {
        fun isAuthenticationError(err: Throwable): Boolean {
            val msg = err.message ?: ""
            return msg.contains("401") || msg.contains("Unauthorized", ignoreCase = true)
        }
    }
}
