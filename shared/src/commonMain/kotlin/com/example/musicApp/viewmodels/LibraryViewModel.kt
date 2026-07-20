package com.example.musicApp.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicApp.data.remote.ApiService
import com.example.musicApp.data.repository.AlbumRepository
import com.example.musicApp.data.repository.ArtistRepository
import com.example.musicApp.data.account.AccountManager
import com.example.musicApp.data.repository.PlaylistRepository
import com.example.musicApp.data.repository.SongRepository
import com.example.musicApp.data.repository.UserPreferencesRepository
import com.example.musicApp.data.repository.savedAlbumToAlbumItem
import com.example.musicApp.data.repository.savedArtistToArtistItem
import com.example.musicApp.data.repository.savedPlaylistToPlaylistItem
import com.example.musicApp.data.repository.savedSongToSongItem
import com.example.musicApp.db.MusicDatabase
import com.example.musicApp.platform.CsvFilePicker
import com.example.musicApp.utils.CsvPlaylistParser
import com.example.musicApp.utils.CsvSongRow
import com.example.musicApp.utils.withMissingMetadataResolved
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi

enum class LibraryTab {
    LIBRARY, ALBUMS, ARTISTS, PLAYLISTS
}

enum class LibrarySortOrder {
    NAME_ASC, NAME_DESC, DATE_ADDED
}

enum class YtmLibraryFilter(val filter: YouTube.LibraryFilter) {
    RECENT_ACTIVITY(YouTube.LibraryFilter.FILTER_RECENT_ACTIVITY),
    RECENTLY_PLAYED(YouTube.LibraryFilter.FILTER_RECENTLY_PLAYED),
    PLAYLISTS_AZ(YouTube.LibraryFilter.FILTER_PLAYLISTS_ALPHABETICAL),
    PLAYLISTS_RECENT(YouTube.LibraryFilter.FILTER_PLAYLISTS_RECENTLY_SAVED),
}

// Estado para el contenido remoto de YTM
sealed class YtmLibraryState {
    data object Idle : YtmLibraryState()
    data object Loading : YtmLibraryState()
    data class Success(
        val playlists: List<PlaylistItem> = emptyList(),
        val likedSongs: List<SongItem> = emptyList(),
        val albums: List<AlbumItem> = emptyList(),
        val artists: List<ArtistItem> = emptyList(),
    ) : YtmLibraryState()
    data class Error(val message: String) : YtmLibraryState()
}

class LibraryViewModel(
    private val apiService: ApiService,
    private val albumRepository: AlbumRepository,
    private val artistRepository: ArtistRepository,
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    loginState: StateFlow<Boolean>? = null
) : ViewModel() {

    private val _selectedTab = MutableStateFlow<LibraryTab?>(LibraryTab.LIBRARY)
    val selectedTab = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow<LibrarySortOrder>(LibrarySortOrder.NAME_ASC)
    val sortOrder = _sortOrder.asStateFlow()

    private val _selectedYtmFilter = MutableStateFlow<YtmLibraryFilter?>(null)
    val selectedYtmFilter = _selectedYtmFilter.asStateFlow()

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setSortOrder(order: LibrarySortOrder) { _sortOrder.value = order }
    fun clearSearch() { _searchQuery.value = "" }
    fun setYtmFilter(filter: YtmLibraryFilter?) {
        _selectedYtmFilter.value = filter
        if (filter != null) loadYtmLibraryWithFilter(filter)
    }

    val savedSongs = songRepository.getSavedSongs().map { it.map(::savedSongToSongItem) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val savedAlbums = albumRepository.getSavedAlbums().map { it.map(::savedAlbumToAlbumItem) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val savedArtists = artistRepository.getSavedArtists().map { it.map(::savedArtistToArtistItem) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val savedPlaylists = playlistRepository.getSavedPlaylists().map { it.map(::savedPlaylistToPlaylistItem) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val continuation = MutableStateFlow<String?>(null)

    // ── Remote YTM (cuenta) ─────────────────────────────────

    private val _ytmState = MutableStateFlow<YtmLibraryState>(YtmLibraryState.Idle)
    val ytmState: StateFlow<YtmLibraryState> = _ytmState.asStateFlow()

    // ── Combined (local + YTM) → filtered → sorted ──────────

    val filteredSongs = searchQuery.combine(savedSongs) { query, songs ->
        if (query.isBlank()) songs else songs.filter { it.title.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val ytmAlbumsFlow = ytmState.map { (it as? YtmLibraryState.Success)?.albums.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val ytmArtistsFlow = ytmState.map { (it as? YtmLibraryState.Success)?.artists.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val ytmPlaylistsFlow = ytmState.map { (it as? YtmLibraryState.Success)?.playlists.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val combinedAlbums = savedAlbums.combine(ytmAlbumsFlow) { local, ytm ->
        (local + ytm).distinctBy { it.id }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val combinedArtists = savedArtists.combine(ytmArtistsFlow) { local, ytm ->
        (local + ytm).distinctBy { it.id }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val combinedPlaylists = savedPlaylists.combine(ytmPlaylistsFlow) { local, ytm ->
        (local + ytm).distinctBy { it.id }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val filteredAlbums = searchQuery.combine(combinedAlbums) { query, albums ->
        if (query.isBlank()) albums else albums.filter { it.title.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val filteredArtists = searchQuery.combine(combinedArtists) { query, artists ->
        if (query.isBlank()) artists else artists.filter { it.title.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val filteredPlaylists = searchQuery.combine(combinedPlaylists) { query, playlists ->
        if (query.isBlank()) playlists else playlists.filter { it.title.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val sortedFilteredAlbums = sortOrder.combine(filteredAlbums) { order, albums ->
        when (order) {
            LibrarySortOrder.NAME_ASC -> albums.sortedBy { it.title }
            LibrarySortOrder.NAME_DESC -> albums.sortedByDescending { it.title }
            LibrarySortOrder.DATE_ADDED -> albums
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val sortedFilteredArtists = sortOrder.combine(filteredArtists) { order, artists ->
        when (order) {
            LibrarySortOrder.NAME_ASC -> artists.sortedBy { it.title }
            LibrarySortOrder.NAME_DESC -> artists.sortedByDescending { it.title }
            LibrarySortOrder.DATE_ADDED -> artists
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val sortedFilteredPlaylists = sortOrder.combine(filteredPlaylists) { order, playlists ->
        when (order) {
            LibrarySortOrder.NAME_ASC -> playlists.sortedBy { it.title }
            LibrarySortOrder.NAME_DESC -> playlists.sortedByDescending { it.title }
            LibrarySortOrder.DATE_ADDED -> playlists
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val sortedFilteredSongs = sortOrder.combine(filteredSongs) { order, songs ->
        when (order) {
            LibrarySortOrder.NAME_ASC -> songs.sortedBy { it.title }
            LibrarySortOrder.NAME_DESC -> songs.sortedByDescending { it.title }
            LibrarySortOrder.DATE_ADDED -> songs
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        // Cargar al inicio si ya hay sesión activa
        loginState?.onEach { isLoggedIn ->
                if (isLoggedIn) loadYtmLibrary()
                else _ytmState.value = YtmLibraryState.Idle
            }?.launchIn(viewModelScope)
    }

    /** Carga la biblioteca remota de YouTube Music:
     *  - Playlists propias (FEmusic_liked_playlists)
     *  - Canciones que le gustan (FEmusic_liked_videos → tabIndex 0)
     *  - Álbumes guardados (FEmusic_library_corpus_track_artists → tabIndex 1)
     *  - Artistas suscritos (FEmusic_library_corpus_track_artists → tabIndex 2)
     */
    fun loadYtmLibrary() {
        _ytmState.value = YtmLibraryState.Loading
        viewModelScope.launch {
            try {
                // Playlists
                val playlists = YouTube.library("FEmusic_liked_playlists")
                    .getOrNull()?.items?.filterIsInstance<PlaylistItem>() ?: emptyList()


                // Álbumes guardados (tabIndex 1)
                val ytmAlbums = YouTube.library("FEmusic_liked_albums", tabIndex = 0)
                    .getOrNull()?.items?.filterIsInstance<AlbumItem>() ?: emptyList()

                // Artistas suscritos (tabIndex 2)
                val ytmArtists = YouTube.library("FEmusic_library_corpus_artists", tabIndex = 0)
                    .getOrNull()?.items?.filterIsInstance<ArtistItem>() ?: emptyList()

                _ytmState.value = YtmLibraryState.Success(
                    playlists = playlists,
                    likedSongs = emptyList(),
                    albums = ytmAlbums,
                    artists = ytmArtists,
                )
            } catch (e: Exception) {
                _ytmState.value = YtmLibraryState.Error(e.message ?: "Error al cargar biblioteca")
            }
        }
    }

    /** Carga la biblioteca remota usando un filtro específico de innertube */
    fun loadYtmLibraryWithFilter(filter: YtmLibraryFilter) {
        _ytmState.value = YtmLibraryState.Loading
        viewModelScope.launch {
            try {
                val result = apiService.getLibraryWithFilter(filter.filter)
                val items = result.getOrNull()?.items.orEmpty()

                val playlists = items.filterIsInstance<PlaylistItem>()
                val albums = items.filterIsInstance<AlbumItem>()
                val artists = items.filterIsInstance<ArtistItem>()
                val songs = items.filterIsInstance<SongItem>()

                _ytmState.value = YtmLibraryState.Success(
                    playlists = playlists,
                    likedSongs = songs,
                    albums = albums,
                    artists = artists,
                )
            } catch (e: Exception) {
                _ytmState.value = YtmLibraryState.Error(e.message ?: "Error al cargar biblioteca")
            }
        }
    }

    // ── Tabs / local actions ────────────────────────────────

    fun selectTab(tab: LibraryTab) { _selectedTab.value = tab }

    fun selectMixedTab() { _selectedTab.value = LibraryTab.LIBRARY }

    fun removeSong(id: String) { viewModelScope.launch { songRepository.removeSong(id) } }
    fun removeAlbum(browseId: String) { viewModelScope.launch { albumRepository.removeAlbum(browseId) } }
    fun removeArtist(id: String) { viewModelScope.launch { artistRepository.removeArtist(id) } }
    fun removePlaylist(id: String) { viewModelScope.launch { playlistRepository.removePlaylist(id) } }

    /**
     * Crea una nueva playlist local
     */
    @OptIn(ExperimentalUuidApi::class)
    fun createLocalPlaylist(name: String) {
        viewModelScope.launch {
            val id = "LOCAL_${kotlin.uuid.Uuid.random()}"
            val playlist = PlaylistItem(
                id = id,
                title = name,
                author = Artist(name = "Local", id = null),
                songCountText = null,
                thumbnail = null,
                playEndpoint = null,
                shuffleEndpoint = null,
                radioEndpoint = null
            )
            playlistRepository.savePlaylist(playlist)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun createLocalPlaylistWithSong(name: String, song: SongItem) {
        viewModelScope.launch {
            val id = "LOCAL_${kotlin.uuid.Uuid.random()}"
            val playlist = PlaylistItem(
                id = id,
                title = name,
                author = Artist(name = "Local", id = null),
                songCountText = null,
                thumbnail = song.thumbnail,
                playEndpoint = null,
                shuffleEndpoint = null,
                radioEndpoint = null
            )
            playlistRepository.savePlaylistWithSongs(playlist, listOf(song))
        }
    }

    fun refreshYtmLibrary() = loadYtmLibrary()

    fun resolveAlbumSongsForPlayback(
        browseId: String,
        onResolved: (List<SongItem>) -> Unit,
        onFallback: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val songs = YouTube.album(browseId).getOrNull()?.songs.orEmpty()
                if (songs.isNotEmpty()) onResolved(songs) else onFallback()
            } catch (_: Exception) {
                onFallback()
            }
        }
    }

    fun resolvePlaylistSongsForPlayback(
        playlistId: String,
        onResolved: (List<SongItem>) -> Unit,
        onFallback: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val songs = YouTube.playlist(playlistId).getOrNull()?.songs.orEmpty()
                if (songs.isNotEmpty()) onResolved(songs) else onFallback()
            } catch (_: Exception) {
                onFallback()
            }
        }
    }

    /** Resuelve las canciones en caché local de una playlist LOCAL_ (usado por el overlay del juego). */
    fun resolveLocalPlaylistSongs(
        playlistId: String,
        onResolved: (List<SongItem>) -> Unit,
        onFallback: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val songs = playlistRepository.getCachedPlaylistSongs(playlistId).orEmpty()
            if (songs.isNotEmpty()) onResolved(songs) else onFallback()
        }
    }
}


class LibrarySongsViewModel(
    private val songRepository: SongRepository,
    private val albumRepository: AlbumRepository,
    private val playlistRepository: PlaylistRepository,
    private val artistRepository: ArtistRepository,
) : ViewModel() {
    val savedSongs = songRepository.getSavedSongs().map { it.map(::savedSongToSongItem) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

class LibraryAlbumsViewModel(
    private val albumRepository: AlbumRepository,
) : ViewModel() {
    val savedAlbums = albumRepository.getSavedAlbums().map { it.map(::savedAlbumToAlbumItem) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

class LibraryArtistsViewModel(
    private val artistRepository: ArtistRepository,
) : ViewModel() {
    val savedArtists = artistRepository.getSavedArtists().map { it.map(::savedArtistToArtistItem) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

sealed class CsvImportState {
    data object Idle : CsvImportState()
    data class Ready(
        val suggestedName: String,
        val songs: List<CsvSongRow>,
        val totalCount: Int,
    ) : CsvImportState()
    data class Searching(
        val currentTitle: String,
        val found: Int,
        val total: Int,
    ) : CsvImportState()
    data class Done(
        val playlistName: String,
        val foundCount: Int,
        val totalCount: Int,
    ) : CsvImportState()
    data class Error(val message: String) : CsvImportState()
}

class LibraryPlaylistsViewModel(
    private val playlistRepository: PlaylistRepository,
    private val userPreferences: UserPreferencesRepository,
) : ViewModel() {
    val savedPlaylists = playlistRepository.getSavedPlaylists().map { it.map(::savedPlaylistToPlaylistItem) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val localPlaylists = savedPlaylists.map { playlists ->
        playlists.filter { it.id.startsWith("LOCAL_") }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _csvImportState = MutableStateFlow<CsvImportState>(CsvImportState.Idle)
    val csvImportState: StateFlow<CsvImportState> = _csvImportState.asStateFlow()

    private var importJob: kotlinx.coroutines.Job? = null

    @OptIn(ExperimentalUuidApi::class)
    fun createLocalPlaylist(name: String, song: SongItem? = null) {
        viewModelScope.launch {
            val id = "LOCAL_${kotlin.uuid.Uuid.random()}"
            val playlist = PlaylistItem(
                id = id,
                title = name,
                author = Artist(name = "Local", id = null),
                songCountText = null,
                thumbnail = song?.thumbnail,
                playEndpoint = null,
                shuffleEndpoint = null,
                radioEndpoint = null
            )
            if (song == null) {
                playlistRepository.savePlaylist(playlist)
            } else {
                playlistRepository.savePlaylistWithSongs(playlist, listOf(song))
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun createLocalPlaylist(name: String, songs: List<SongItem>) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            val id = "LOCAL_${kotlin.uuid.Uuid.random()}"
            val playlist = PlaylistItem(
                id = id,
                title = name,
                author = Artist(name = "Local", id = null),
                songCountText = "${songs.size} canciones",
                thumbnail = songs.firstOrNull()?.thumbnail,
                playEndpoint = null,
                shuffleEndpoint = null,
                radioEndpoint = null
            )
            playlistRepository.savePlaylistWithSongs(playlist, songs)
        }
    }

    fun addSongToLocalPlaylist(playlistId: String, song: SongItem) {
        viewModelScope.launch {
            val resolvedSong = withContext(Dispatchers.IO){ song.withMissingMetadataResolved()}
            playlistRepository.addSongToPlaylist(playlistId, resolvedSong)
            mirrorAddToYtm(playlistId, resolvedSong)
        }
    }

    fun removeSongFromLocalPlaylist(playlistId: String, songId: String) {
        viewModelScope.launch {
            // Leer el setVideoId antes de eliminar la fila local; necesario para la eliminación remota.
            val setVideoId = playlistRepository.getSetVideoId(playlistId, songId)
            playlistRepository.removeSongFromPlaylist(playlistId, songId)
            mirrorRemoveFromYtm(playlistId, songId, setVideoId)
        }
    }

    // ── Sincronización con YouTube Music (experimental) ────────────────────────────────────
    // Refleja las ediciones de playlists locales en una playlist de YTM en la cuenta iniciada.
    // Es optimista y mejor esfuerzo: el cambio local nunca se revierte si la llamada remota falla.

    private suspend fun ytmSyncActive(): Boolean =
        userPreferences.ytmSyncEnabled.first() && AccountManager.loginState.value

    private suspend fun mirrorAddToYtm(localPlaylistId: String, song: SongItem) {
        if (!ytmSyncActive()) return
        // Las playlists que ya tienen un browseId son playlists reales de YouTube (guardadas desde YTM);
        // PlaylistRepository.addSongToPlaylist ya las sincroniza incondicionalmente. Reflejar aquí
        // también crearía una SEGUNDA playlist duplicada (resolveOrCreateRemotePlaylist no tiene
        // forma de saber que esta ya existe remotamente) y añadiría la canción dos veces.
        if (playlistRepository.getBrowseId(localPlaylistId) != null) return
        runCatching {
            withContext(Dispatchers.IO) {
                val remoteId = resolveOrCreateRemotePlaylist(localPlaylistId) ?: return@withContext
                val setVideoId = YouTube.addToPlaylist(remoteId, song.id).getOrNull()
                if (setVideoId != null) {
                    playlistRepository.updateSetVideoId(localPlaylistId, song.id, setVideoId)
                }
            }
        }.onFailure { Napier.e("[ytm-sync] add failed", it) }
    }

    private suspend fun mirrorRemoveFromYtm(localPlaylistId: String, songId: String, setVideoId: String?) {
        if (setVideoId == null || !ytmSyncActive()) return
        // Misma lógica que mirrorAddToYtm: las playlists vinculadas con browseId ya son
        // gestionadas por PlaylistRepository.removeSongFromPlaylist.
        if (playlistRepository.getBrowseId(localPlaylistId) != null) return
        val remoteId = userPreferences.getRemotePlaylistId(localPlaylistId) ?: return
        runCatching {
            withContext(Dispatchers.IO) { YouTube.removeFromPlaylist(remoteId, songId, setVideoId) }
        }.onFailure { Napier.e("[ytm-sync] remove failed", it) }
    }

    /** La playlist remota de YTM que refleja [localPlaylistId], creándola en el primer uso. */
    private suspend fun resolveOrCreateRemotePlaylist(localPlaylistId: String): String? {
        userPreferences.getRemotePlaylistId(localPlaylistId)?.let { return it }
        val name = playlistRepository.getCachedPlaylistItem(localPlaylistId)?.title ?: return null
        val remoteId = runCatching { YouTube.createPlaylist(name) }.getOrNull() ?: return null
        userPreferences.setRemotePlaylistId(localPlaylistId, remoteId)
        return remoteId
    }

    fun importCsvFile() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = CsvFilePicker.pickAndReadCsvFile() ?: return@launch
            val songs = CsvPlaylistParser.parse(result.content)
            withContext(Dispatchers.Main) {
                if (songs.isEmpty()) {
                    _csvImportState.value = CsvImportState.Error("No se encontraron canciones válidas en el archivo")
                } else {
                    _csvImportState.value = CsvImportState.Ready(
                        suggestedName = result.fileName,
                        songs = songs,
                        totalCount = songs.size,
                    )
                }
            }
        }
    }

    fun initiateCsvImport(suggestedName: String, songs: List<CsvSongRow>) {
        if (songs.isEmpty()) {
            _csvImportState.value = CsvImportState.Error("No songs to import")
            return
        }
        _csvImportState.value = CsvImportState.Ready(
            suggestedName = suggestedName,
            songs = songs,
            totalCount = songs.size,
        )
    }

    fun confirmCsvImport(playlistName: String) {
        val current = _csvImportState.value as? CsvImportState.Ready ?: return
        val songs = current.songs
        if (songs.isEmpty()) return

        importJob = viewModelScope.launch {
            _csvImportState.value = CsvImportState.Searching(
                currentTitle = songs.first().title,
                found = 0,
                total = songs.size,
            )

            val foundSongs = mutableListOf<SongItem>()
            for ((_, row) in songs.withIndex()) {
                _csvImportState.value = CsvImportState.Searching(
                    currentTitle = row.title,
                    found = foundSongs.size,
                    total = songs.size,
                )

                val query = "${row.title} ${row.artist}"
                val searchResult = withContext(Dispatchers.IO) {
                    YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
                }
                searchResult.getOrNull()?.items?.firstOrNull { it is SongItem }
                    ?.let { foundSongs.add(it as SongItem) }
            }

            if (foundSongs.isEmpty()) {
                _csvImportState.value = CsvImportState.Error("No se encontraron canciones en YouTube Music")
                return@launch
            }

            val id = "LOCAL_${kotlin.uuid.Uuid.random()}"
            val playlist = PlaylistItem(
                id = id,
                title = playlistName,
                author = Artist(name = "Local", id = null),
                songCountText = null,
                thumbnail = foundSongs.firstOrNull()?.thumbnail,
                playEndpoint = null,
                shuffleEndpoint = null,
                radioEndpoint = null,
            )
            playlistRepository.savePlaylistWithSongs(playlist, foundSongs)

            _csvImportState.value = CsvImportState.Done(
                playlistName = playlistName,
                foundCount = foundSongs.size,
                totalCount = songs.size,
            )
        }
    }

    fun dismissCsvImportResult() {
        _csvImportState.value = CsvImportState.Idle
    }

    fun cancelCsvImport() {
        importJob?.cancel()
        _csvImportState.value = CsvImportState.Idle
    }
}

class LibraryMixedViewModel(
    musicDatabase: MusicDatabase
) : ViewModel() {
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    val database = musicDatabase.database

    var albums = database.savedAlbumQueries.selectAll()
    var playlists = database.playlistQueries.playlistsByNameAsc()
}
