package com.example.melodist.viewmodels

import androidx.lifecycle.viewModelScope
import com.example.melodist.data.account.AccountManager
import com.example.melodist.data.remote.ApiService
import com.example.melodist.data.repository.PlaylistRepository
import com.example.melodist.data.repository.SongRepository
import com.example.melodist.platform.ImageFilePicker
import com.example.melodist.viewmodels.queues.YouTubePlaylistQueue
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.pages.PlaylistPage
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.logging.Logger
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers

sealed class PlaylistState {
    object Loading : PlaylistState()
    data class Success(
        val playlistPage: PlaylistPage,
        val isFromCache: Boolean = false,
        val isSaved: Boolean = false,
        val isSaving: Boolean = false,
        val isLoadingForPlay: Boolean = false,
    ) : PlaylistState()
    data class Error(val message: String) : PlaylistState()
}

class PlaylistViewModel(
    private val apiService: ApiService,
    private val repository: PlaylistRepository,
    private val songRepository: SongRepository,
    private val playerCoordinator: PlayerCoordinator,
) : MelodistViewModel() {

    private val log = Logger.getLogger("PlaylistViewModel")

    private val _uiState = MutableStateFlow<PlaylistState>(PlaylistState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _currentPlaylistId = MutableStateFlow<String?>(null)

    /** Canciones acumuladas de todas las páginas cargadas */
    private val _songs = MutableStateFlow<List<SongItem>>(emptyList())
    val songs: StateFlow<List<SongItem>> = _songs.asStateFlow()

    private val _continuation = MutableStateFlow<String?>(null)

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    val hasMoreSongs: StateFlow<Boolean> = _continuation
        .map { token -> token != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /** Actualiza un campo de PlaylistState.Success de forma segura; no-op si el estado no es Success */
    private inline fun updateSuccess(transform: PlaylistState.Success.() -> PlaylistState.Success) {
        _uiState.update { state ->
            if (state is PlaylistState.Success) state.transform() else state
        }
    }

    fun loadPlaylist(playlistId: String) {
        if (_currentPlaylistId.value == playlistId && playlistId != "LOCAL_DOWNLOADS") return
        _currentPlaylistId.value = playlistId

        viewModelScope.launch {
            _uiState.value = PlaylistState.Loading
            _songs.value = emptyList()
            _continuation.value = null

            // Special case: local downloads playlist
            if (playlistId == "LOCAL_DOWNLOADS") {
                val downloadedSongs = songRepository.getDownloadedSongs()
                _songs.value = downloadedSongs
                _continuation.value = null
                _uiState.value = PlaylistState.Success(
                    playlistPage = PlaylistPage(
                        playlist = PlaylistItem(
                            id = playlistId,
                            title = "Descargas",
                            author = null,
                            songCountText = "${downloadedSongs.size} canciones",
                            thumbnail = downloadedSongs.firstOrNull()?.thumbnail,
                            playEndpoint = null,
                            shuffleEndpoint = null,
                            radioEndpoint = null
                        ),
                        songs = downloadedSongs,
                        songsContinuation = null,
                        continuation = null
                    ),
                    isFromCache = true,
                    isSaved = true,
                )
                return@launch
            }

            // Special case: local playlists created in the app
            if (playlistId.startsWith("LOCAL_")) {
                val localPlaylist = repository.getCachedPlaylistItem(playlistId)
                val localSongs = repository.getCachedPlaylistSongs(playlistId) ?: emptyList()

                if (localPlaylist != null) {
                    _songs.value = localSongs
                    _continuation.value = null
                    _uiState.value = PlaylistState.Success(
                        playlistPage = PlaylistPage(
                            playlist = localPlaylist,
                            songs = localSongs,
                            songsContinuation = null,
                            continuation = null
                        ),
                        isFromCache = true,
                        isSaved = true,
                    )
                    return@launch
                }
            }

            // 1. Intentar cargar desde caché local
            val cachedSongs = repository.getCachedPlaylistSongs(playlistId)
            val cachedPlaylist = repository.getCachedPlaylistItem(playlistId)

            if (cachedSongs != null && cachedPlaylist != null) {
                log.info("Cargando playlist desde caché local: $playlistId (${cachedSongs.size} canciones)")
                _songs.value = cachedSongs
                _continuation.value = null
                val saved = repository.isPlaylistSavedOnce(playlistId)
                _uiState.value = PlaylistState.Success(
                    playlistPage = PlaylistPage(
                        playlist = cachedPlaylist,
                        songs = cachedSongs,
                        songsContinuation = null,
                        continuation = null
                    ),
                    isFromCache = true,
                    isSaved = saved,
                )
                return@launch
            }

            // 2. No está en caché → buscar en YouTube
            log.info("Playlist no cacheada, cargando desde YouTube: $playlistId")
            YouTube.playlist(playlistId)
                .onSuccess { page ->
                    if(page.songs.isNotEmpty()){

                    _songs.value = page.songs
                    _continuation.value = if (page.songsContinuation != null) page.songsContinuation else null
                    val saved = repository.isPlaylistSavedOnce(playlistId)
                    _uiState.value = PlaylistState.Success(
                        playlistPage = page,
                        isFromCache = false,
                        isSaved = saved,
                    )
                    }
                }
                .onFailure {
                    _uiState.value = PlaylistState.Error(it.message ?: "Error desconocido")
                }
        }
    }

    fun refreshLocalDownloadsPlaylist() {
        if (_currentPlaylistId.value != "LOCAL_DOWNLOADS") return

        viewModelScope.launch {
            val downloadedSongs = songRepository.getDownloadedSongs()
            _songs.value = downloadedSongs
            updateSuccess {
                val updatedPlaylist = playlistPage.playlist.copy(
                    songCountText = "${downloadedSongs.size} canciones",
                    thumbnail = downloadedSongs.firstOrNull()?.thumbnail
                )
                copy(
                    playlistPage = playlistPage.copy(
                        playlist = updatedPlaylist,
                        songs = downloadedSongs,
                    ),
                    isFromCache = true,
                    isSaved = true,
                )
            }
        }
    }

    fun loadMoreSongs() {
        val token = _continuation.value ?: return
        if (_isLoadingMore.value) return

        viewModelScope.launch {
            _isLoadingMore.value = true
            var success = false

            repeat(3) { attempt ->
                if (success) return@repeat
                YouTube.playlistContinuation(token)
                .onSuccess { page ->
                        if (page.songs.isNotEmpty()) {
                            _songs.value += page.songs
                        }
                        _continuation.value = page.continuation?.takeIf { it.isNotBlank() }
                        success = true
                    }
                    .onFailure {
                        delay((500L * (attempt + 1)).milliseconds)
                    }
            }

            if (!success) log.warning("Falló la carga de más canciones tras 3 intentos")
            _isLoadingMore.value = false
        }
    }

    suspend fun fetchAllRemainingPages(): List<SongItem> {
        var token = _continuation.value
        val accumulatedSongs = _songs.value.toMutableList()

        while (token != null) {
            var fetched = false
            repeat(3) { attempt ->
                if (fetched) return@repeat

                YouTube.playlistContinuation(token!!)
                    .onSuccess { page ->
                        if (page.songs.isNotEmpty()) {
                            accumulatedSongs.addAll(page.songs)
                            // Actualizamos el flujo de canciones para que el usuario vea progreso
                            _songs.value = accumulatedSongs.toList()
                        }
                        token = page.continuation?.takeIf { it.isNotBlank() }
                        _continuation.value = token
                        fetched = true
                    }
                    .onFailure {
                        delay((500L * (attempt + 1)).milliseconds)
                    }
            }
            if (!fetched) break
        }
        return _songs.value
    }

    fun playAllSongs(shuffle: Boolean = false) {
        val state = _uiState.value as? PlaylistState.Success ?: return
        if (state.isLoadingForPlay) return
        // Siempre reproducir la lista finita de canciones (sin recomendaciones/automix)
        loadAndPlayAll(shuffle, state)
    }

    private fun loadAndPlayAll(shuffle: Boolean, state: PlaylistState.Success) {
        viewModelScope.launch {
            val queue = YouTubePlaylistQueue(
                playlistId = state.playlistPage.playlist.id,
                playlistTitle = state.playlistPage.playlist.title,
                initialSongs = _songs.value,
                initialContinuation = _continuation.value,
            )
            playerCoordinator.playPlaylistWithQueue(queue, shuffle)
        }
    }

    fun playSongFromPlaylist(index: Int, shuffle: Boolean = false) {
        val state = _uiState.value as? PlaylistState.Success ?: return
        viewModelScope.launch {
            val queue = YouTubePlaylistQueue(
                playlistId = state.playlistPage.playlist.id,
                playlistTitle = state.playlistPage.playlist.title,
                initialSongs = _songs.value,
                initialContinuation = _continuation.value,
                startIndex = index,
            )
            playerCoordinator.playPlaylistWithQueue(queue, shuffle)
        }
    }

    fun toggleSave() {
        val playlistId = _currentPlaylistId.value ?: return
        val state = _uiState.value as? PlaylistState.Success ?: return
        if (state.isSaving) return

        viewModelScope.launch {
            if (state.isSaved) {
                repository.removePlaylist(playlistId)
                updateSuccess { copy(isSaved = false) }
                log.info("Playlist eliminada de guardados: $playlistId")
            } else {
                updateSuccess { copy(isSaving = true) }
                try {
                    val allSongs = if (_continuation.value != null) {
                        log.info("Cargando todas las páginas antes de guardar...")
                        fetchAllRemainingPages()
                    } else {
                        _songs.value
                    }

                    repository.savePlaylistWithSongs(
                        playlist = state.playlistPage.playlist,
                        songs = allSongs
                    )
                    updateSuccess { copy(isSaved = true) }
                    log.info("Playlist guardada localmente: $playlistId (${allSongs.size} canciones)")
                } finally {
                    updateSuccess { copy(isSaving = false) }
                }
            }
        }
    }

    fun downloadPlaylist() {
        viewModelScope.launch(Dispatchers.IO) {
            val songs = fetchAllRemainingPages()
            playerCoordinator.downloadSongs(songs)
        }
    }

    fun removeSongFromPlaylist(songId: String) {
        val playlistId = _currentPlaylistId.value ?: return
        val state = _uiState.value as? PlaylistState.Success ?: return
        if (!playlistId.startsWith("LOCAL_")) return

        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
            val updatedSongs = _songs.value.filterNot { it.id == songId }
            _songs.value = updatedSongs
            val updatedPlaylist = state.playlistPage.playlist.copy(
                songCountText = "${updatedSongs.size} canciones"
            )
            updateSuccess {
                copy(
                    playlistPage = state.playlistPage.copy(
                        playlist = updatedPlaylist,
                        songs = updatedSongs,
                    ),
                )
            }
        }
    }

    fun pickAndSetCustomThumbnail() {
        val playlistId = _currentPlaylistId.value ?: return
        if (!AccountManager.isLoggedIn) return

        viewModelScope.launch(Dispatchers.IO) {
            val result = ImageFilePicker.pickImageFile() ?: return@launch
            YouTube.uploadCustomThumbnailLink(playlistId, result.bytes)
                .onSuccess { newThumbnailUrl ->
                    if (newThumbnailUrl != null) {
                        updateSuccess {
                            val updated = playlistPage.playlist.copy(thumbnail = newThumbnailUrl)
                            copy(playlistPage = playlistPage.copy(playlist = updated))
                        }
                    }
                }
                .onFailure {
                    Napier.e("Failed to upload playlist thumbnail: ${it.message}")
                }
        }
    }
}
