package com.example.melodist.viewmodels


import androidx.lifecycle.ViewModel

import androidx.lifecycle.viewModelScope

import com.example.melodist.data.repository.SongRepository
import com.example.melodist.db.DatabaseDao

import com.example.melodist.download.DownloadService

import com.example.melodist.download.DownloadState

import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.SharingStarted

import kotlinx.coroutines.flow.StateFlow

import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.flow.firstOrNull

import kotlinx.coroutines.flow.map

import kotlinx.coroutines.flow.stateIn

import kotlinx.coroutines.flow.Flow

import kotlinx.coroutines.flow.distinctUntilChanged

import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.collections.emptyList


/**
 * ViewModel that exposes download state to the UI.
 * Registered as a singleton in KOIN so download state is shared app-wide.
 */

class DownloadViewModel(

    private val downloadService: DownloadService,

    private val databaseDao: DatabaseDao,

    private val songRepository: SongRepository

) : ViewModel() {


    /** Per-song download states (songId → DownloadState). */

    val downloadStates: StateFlow<Map<String, DownloadState>> =
        downloadService.downloadStates


    /** Total cache size as human-readable string. */

    private val _cacheSizeText = MutableStateFlow("Calculando...")
    val cacheSizeText: StateFlow<String> = _cacheSizeText.asStateFlow()

    private val _downloadedSongs = MutableStateFlow<List<SongItem>>(emptyList())
    private var lastCompletedSongIds: Set<String> = emptySet()


    /** Downloaded songs from DB as SongItems (for the Library Downloads tab). */
    val downloadedSongs: StateFlow<List<SongItem>> = _downloadedSongs.asStateFlow()


    /** Initialization state for the downloads tab content. */

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()


    init {

        viewModelScope.launch {
            refreshDownloadedSongs()
            _isLoading.value = false
        }

        viewModelScope.launch {
            downloadStates.collect { states ->
                val completedIds = states
                    .filterValues { it is DownloadState.Completed }
                    .keys

                if (completedIds != lastCompletedSongIds) {
                    lastCompletedSongIds = completedIds
                    refreshDownloadedSongs()
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            refreshCacheSize()
        }

    }


    /** Total downloaded song count. */

    val downloadedCount: StateFlow<Int> = downloadedSongs
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)


    /** Songs currently being downloaded (metadata for UI display). */

    val pendingSongItems: StateFlow<Map<String, SongItem>> =
        downloadService.pendingSongItems
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())


    /**

     * Albums that are fully downloaded (all songs present).

     * Groups downloaded songs by albumId, then checks if the count matches the album's songCount in DB.

     * Only includes albums that exist in the DB with a valid songCount > 0.

     */

    data class DownloadedAlbumInfo(
        val albumId: String,
        val albumName: String,
        val thumbnail: String?,
        val songs: List<SongItem>,
        val totalSongCount: Int
    )

    private val _fullyDownloadedAlbums = MutableStateFlow<List<DownloadedAlbumInfo>>(emptyList())
    val fullyDownloadedAlbums: StateFlow<List<DownloadedAlbumInfo>> = _fullyDownloadedAlbums.asStateFlow()


    /**

     * Playlists that are fully downloaded (all mapped songs downloaded).

     */

    data class DownloadedPlaylistInfo(

        val playlistId: String,

        val playlistName: String,

        val thumbnail: String?,

        val downloadedSongCount: Int,

        val totalSongCount: Int

    )

    private val _fullyDownloadedPlaylists = MutableStateFlow<List<DownloadedPlaylistInfo>>(emptyList())
    val fullyDownloadedPlaylists: StateFlow<List<DownloadedPlaylistInfo>> = _fullyDownloadedPlaylists.asStateFlow()


// ─── Actions ───────────────────────────────────────────


    fun downloadSong(song: SongItem) {
        downloadService.downloadSong(song)
    }


    fun downloadAll(songs: List<SongItem>) {
        val notDownloaded = songs.filterNot { isDownloaded(it.id) }
        if (notDownloaded.isNotEmpty()) {
            downloadService.downloadAll(notDownloaded)
        }
    }


    fun cancelDownload(songId: String) {
        downloadService.cancelDownload(songId)
    }


    fun removeDownload(songId: String) {
        downloadService.removeDownload(songId)
        _downloadedSongs.value = _downloadedSongs.value.filterNot { it.id == songId }
        viewModelScope.launch { refreshDerivedDownloadedCollections() }
        refreshCacheSize()
    }

    fun removeDownloads(songIds: List<String>) {
        downloadService.removeDownloads(songIds)
        val ids = songIds.toSet()
        _downloadedSongs.value = _downloadedSongs.value.filterNot { it.id in ids }
        viewModelScope.launch { refreshDerivedDownloadedCollections() }
        refreshCacheSize()
    }


    fun clearCache() {
        downloadService.clearCache()
        _downloadedSongs.value = emptyList()
        viewModelScope.launch { refreshDerivedDownloadedCollections() }
        refreshCacheSize()
    }


    fun isDownloaded(songId: String): Boolean {
        return downloadService.isDownloaded(songId)
    }


    fun getState(songId: String): DownloadState? {
        return downloadStates.value[songId]
    }

    fun refreshCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            val size = DownloadService.getCacheSizeBytes()
            _cacheSizeText.value = DownloadService.formatSize(size)
        }
    }

    private suspend fun refreshDownloadedSongs() = withContext(Dispatchers.IO) {
        _downloadedSongs.value = songRepository.getDownloadedSongs()
        refreshDerivedDownloadedCollections()
        refreshCacheSize()
    }

    private suspend fun refreshDerivedDownloadedCollections() = withContext(Dispatchers.IO) {
        val songs = _downloadedSongs.value

        val albumIds = songs.mapNotNull { it.album?.id }.distinct()
        _fullyDownloadedAlbums.value = albumIds.mapNotNull { albumId ->
            val albumEntity = databaseDao.albumById(albumId).firstOrNull() ?: return@mapNotNull null
            val totalCount = albumEntity.songCount
            if (totalCount <= 0) return@mapNotNull null
            val downloadedCount = databaseDao.countDownloadedByAlbum(albumId)
            if (downloadedCount >= totalCount.toLong()) {
                DownloadedAlbumInfo(
                    albumId = albumId,
                    albumName = albumEntity.title,
                    thumbnail = albumEntity.thumbnailUrl ?: songs.firstOrNull { it.album?.id == albumId }?.thumbnail,
                    songs = songs.filter { it.album?.id == albumId },
                    totalSongCount = totalCount
                )
            } else null
        }.sortedBy { it.albumName }

        val playlists = databaseDao.allPlaylists().firstOrNull() ?: emptyList()
        _fullyDownloadedPlaylists.value = playlists.mapNotNull { playlist ->
            val total = databaseDao.countByPlaylist(playlist.id)
            if (total == 0L) return@mapNotNull null
            val downloaded = databaseDao.countDownloadedByPlaylist(playlist.id)
            if (total == downloaded) {
                DownloadedPlaylistInfo(
                    playlistId = playlist.browseId ?: playlist.id,
                    playlistName = playlist.name,
                    thumbnail = playlist.thumbnailUrl,
                    downloadedSongCount = downloaded.toInt(),
                    totalSongCount = playlist.remoteSongCount ?: total.toInt()
                )
            } else null
        }
    }


    /** Returns a flow for a single song's download state, distinct until changed. */

    fun downloadStateFlow(songId: String): Flow<DownloadState?> =
        downloadStates.map { it[songId] }.distinctUntilChanged()


    fun isAnyDownloadingFlow(songIds: List<String>): Flow<Boolean> = downloadStates.map { states ->
        songIds.any { id ->
            val state = states[id]
            state is DownloadState.Queued || state is DownloadState.Downloading
        }
    }.distinctUntilChanged()


    /** Returns a flow that emits true if ALL the provided songs are completely downloaded. */
    fun isFullyDownloadedFlow(songIds: List<String>): Flow<Boolean> =

        downloadStates.map { states ->

            if (songIds.isEmpty()) false
            else songIds.all { id -> states[id] is DownloadState.Completed }

        }.distinctUntilChanged()

}

