package com.example.melodist.utils

import com.example.melodist.data.repository.AlbumRepository
import com.example.melodist.data.repository.ArtistRepository
import com.example.melodist.data.repository.PlaylistRepository
import com.example.melodist.data.repository.SongRepository
import com.example.melodist.db.DatabaseDao
import com.example.melodist.db.entities.PlaylistEntity
import com.example.melodist.models.toMediaMetadata
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.utils.completed
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

sealed class SyncOperation {
    data object FullSync : SyncOperation()
    data object SavedPlaylists : SyncOperation()
    /** Obtiene la lista completa de canciones de una playlist desde YouTube y sobrescribe la copia local. */
    data class SinglePlaylist(val playlistId: String, val browseId: String) : SyncOperation()
    /** Ejecuta [SinglePlaylist] para cada playlist local marcada con `isAutoSync`. */
    data object AutoSyncPlaylists : SyncOperation()
    /** Solo local: colapsa playlists que comparten el mismo `browseId`, conservando la más completa. */
    data object CleanupDuplicates : SyncOperation()
}

sealed class SyncStatus {
    data object Idle : SyncStatus()
    data object Syncing : SyncStatus()
    data object Completed : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

data class SyncState(
    val overallStatus: SyncStatus = SyncStatus.Idle,
    val playlists: SyncStatus = SyncStatus.Idle,
    val currentOperation: String = ""
)

class SyncUtils(
    private val database: DatabaseDao,
    private val songRepository: SongRepository,
    private val albumRepository: AlbumRepository,
    private val artistRepository: ArtistRepository,
    private val playlistRepository: PlaylistRepository,
) {
    private val syncJob = SupervisorJob()
    private val syncScope = CoroutineScope(Dispatchers.IO + syncJob)
    private val syncChannel = Channel<SyncOperation>(Channel.BUFFERED)
    private var processingJob: Job? = null

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    init { startProcessingQueue() }

    private fun startProcessingQueue() {
        processingJob = syncScope.launch {
            for (operation in syncChannel) {
                try {
                    when (operation) {
                        SyncOperation.FullSync -> executeFullSync()
                        SyncOperation.SavedPlaylists -> executeSavedPlaylists()
                        is SyncOperation.SinglePlaylist -> executeSinglePlaylist(operation.playlistId, operation.browseId)
                        SyncOperation.AutoSyncPlaylists -> executeAutoSyncPlaylists()
                        SyncOperation.CleanupDuplicates -> executeCleanupDuplicates()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Napier.e("Error processing sync operation: $operation", e)
                }
            }
        }
    }

    private fun updateState(update: SyncState.() -> SyncState) {
        _syncState.value = _syncState.value.update()
    }

    fun syncSavedPlaylists() { syncScope.launch { syncChannel.send(SyncOperation.SavedPlaylists) } }
    fun performFullSync() { syncScope.launch { syncChannel.send(SyncOperation.FullSync) } }
    fun syncPlaylist(playlistId: String, browseId: String) {
        syncScope.launch { syncChannel.send(SyncOperation.SinglePlaylist(playlistId, browseId)) }
    }
    fun syncAutoSyncPlaylists() { syncScope.launch { syncChannel.send(SyncOperation.AutoSyncPlaylists) } }
    fun cleanupDuplicatePlaylists() { syncScope.launch { syncChannel.send(SyncOperation.CleanupDuplicates) } }

    private suspend fun executeFullSync() {
        updateState { copy(overallStatus = SyncStatus.Syncing, currentOperation = "Syncing full library") }
        try {
            executeLikedSongs()
            executeLibrarySongs()
            executeUploadedSongs()
            executeLikedAlbums()
            executeUploadedAlbums()
            executeArtistsSubscriptions()
            executeSavedPlaylists()
            updateState { copy(overallStatus = SyncStatus.Completed, currentOperation = "") }
        } catch (e: Exception) {
            updateState { copy(overallStatus = SyncStatus.Error(e.message ?: "Unknown error"), currentOperation = "") }
        }
    }

    /**
     * Obtiene "LM" (Liked Music) y reconcilia la bandera real `Song.liked` — la que el botón de
     * like/corazón y `PlayerViewModel.toggleLike()` leen/escriben — no solo la tabla separada de
     * caché `SavedSong` (que solo alimenta la lista de "canciones" de la Biblioteca). Sin esto,
     * una canción marcada como favorita desde tu teléfono/web de YTM nunca se mostraba como
     * favorita dentro del propio LyriK.
     */
    private suspend fun executeLikedSongs() = withContext(Dispatchers.IO) {
        updateState { copy(currentOperation = "Syncing liked songs") }
        try {
            val remoteSongs = YouTube.playlist("LM").completed().getOrNull()?.songs.orEmpty()
            val remoteIds = remoteSongs.map { it.id }.toSet()

            // Refleja los des-likes hechos desde el propio YouTube.
            database.likedSongs().first()
                .filterNot { it.id in remoteIds }
                .forEach { database.insertSong(it.localToggleLike()) }

            val now = java.time.LocalDateTime.now()
            remoteSongs.forEachIndexed { index, song ->
                songRepository.saveSong(song)

                val timestamp = now.minusSeconds(index.toLong())
                val existing = database.songById(song.id).first()
                if (existing == null) {
                    database.insertSong(
                        song.toMediaMetadata().toSongEntity().copy(liked = true, likedDate = timestamp)
                    )
                } else if (!existing.liked || existing.likedDate != timestamp) {
                    database.insertSong(existing.copy(liked = true, likedDate = timestamp))
                }
            }
        } catch (e: Exception) {
            Napier.e("Failed syncing liked songs", e)
        }
    }

    private suspend fun executeLibrarySongs() = withContext(Dispatchers.IO) {
        updateState { copy(currentOperation = "Syncing library songs") }
        try {
            val remoteSongs = YouTube.library("FEmusic_liked_videos").completed().getOrNull()?.items?.filterIsInstance<com.metrolist.innertube.models.SongItem>().orEmpty()
            remoteSongs.forEach { song ->
                songRepository.saveSong(song)
            }
        } catch (e: Exception) {
            Napier.e("Failed syncing library songs", e)
        }
    }

    private suspend fun executeUploadedSongs() = withContext(Dispatchers.IO) {
        updateState { copy(currentOperation = "Syncing uploaded songs") }
        try {
            val remoteSongs = YouTube.library("FEmusic_library_privately_owned_tracks", tabIndex = 1).completed().getOrNull()?.items?.filterIsInstance<com.metrolist.innertube.models.SongItem>().orEmpty()
            remoteSongs.forEach { song ->
                songRepository.saveSong(song)
            }
        } catch (e: Exception) {
            Napier.e("Failed syncing uploaded songs", e)
        }
    }

    private suspend fun executeLikedAlbums() = withContext(Dispatchers.IO) {
        updateState { copy(currentOperation = "Syncing liked albums") }
        try {
            val remoteAlbums = YouTube.library("FEmusic_liked_albums").completed().getOrNull()?.items?.filterIsInstance<com.metrolist.innertube.models.AlbumItem>().orEmpty()
            remoteAlbums.forEach { album ->
                albumRepository.saveAlbum(album)
            }
        } catch (e: Exception) {
            Napier.e("Failed syncing liked albums", e)
        }
    }

    private suspend fun executeUploadedAlbums() = withContext(Dispatchers.IO) {
        updateState { copy(currentOperation = "Syncing uploaded albums") }
        try {
            val remoteAlbums = YouTube.library("FEmusic_library_privately_owned_releases").completed().getOrNull()?.items?.filterIsInstance<com.metrolist.innertube.models.AlbumItem>().orEmpty()
            remoteAlbums.forEach { album ->
                albumRepository.saveAlbum(album)
            }
        } catch (e: Exception) {
            Napier.e("Failed syncing uploaded albums", e)
        }
    }

    private suspend fun executeArtistsSubscriptions() = withContext(Dispatchers.IO) {
        updateState { copy(currentOperation = "Syncing artists") }
        try {
            val remoteArtists = YouTube.library("FEmusic_library_corpus_artists").completed().getOrNull()?.items?.filterIsInstance<com.metrolist.innertube.models.ArtistItem>().orEmpty()
            val remoteIds = remoteArtists.map { it.id }.toSet()

            // Refleja las des-suscripciones hechas desde el propio YouTube: elimina artistas guardados
            // localmente que ya no están en la lista remota de suscripciones (refleja la misma
            // reconciliación de executeSavedPlaylists).
            artistRepository.getSavedArtists().first()
                .map { it.id }
                .filterNot { it in remoteIds }
                .forEach { artistRepository.removeArtist(it) }

            remoteArtists.forEach { artist ->
                artistRepository.saveArtist(artist)
            }
        } catch (e: Exception) {
            Napier.e("Failed syncing artists", e)
        }
    }

    private suspend fun executeSavedPlaylists() = withContext(Dispatchers.IO) {
        updateState { copy(playlists = SyncStatus.Syncing, currentOperation = "Syncing saved playlists") }

        try {
            val remotePlaylists = YouTube.library("FEmusic_liked_playlists")
                .completed()
                .getOrNull()
                ?.items
                ?.filterIsInstance<PlaylistItem>()
                ?.filterNot { it.id == "LM" || it.id == "SE" }
                ?.reversed()
                .orEmpty()

            val remoteIds = remotePlaylists.map { it.id }.toSet()
            val localPlaylists = database.playlistsByNameAsc().first()

            localPlaylists
                .filter { it.browseId != null }
                .filterNot { it.browseId in remoteIds }
                .forEach { playlist ->
                    playlistRepository.removePlaylist(playlist.id)
                    delay(50.milliseconds)
                }

            remotePlaylists.forEach { playlist ->
                try {
                    val existing = localPlaylists.firstOrNull { it.browseId == playlist.id }
                    val entity = PlaylistEntity(
                        id = existing?.id ?: playlist.id,
                        name = playlist.title,
                        browseId = playlist.id,
                        createdAt = existing?.createdAt,
                        lastUpdateTime = existing?.lastUpdateTime,
                        isEditable = playlist.isEditable,
                        bookmarkedAt = existing?.bookmarkedAt,
                        remoteSongCount = playlist.songCountText?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() },
                        playEndpointParams = playlist.playEndpoint?.params,
                        thumbnailUrl = playlist.thumbnail,
                        shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                        radioEndpointParams = playlist.radioEndpoint?.params,
                        isLocal = existing?.isLocal ?: false,
                        isAutoSync = existing?.isAutoSync ?: false
                    )
                    database.insertPlaylist(entity)
                } catch (e: Exception) {
                    Napier.e("Failed to sync playlist ${playlist.title}", e)
                }
            }

            updateState { copy(playlists = SyncStatus.Completed, currentOperation = "") }
        } catch (e: Exception) {
            updateState { copy(playlists = SyncStatus.Error(e.message ?: "Unknown error"), currentOperation = "") }
        }
    }

    /**
     * Obtiene la lista completa de canciones de una playlist y sobrescribe la copia local. Esta es la
     * contraparte de [PlaylistRepository.addSongToPlaylist]/`removeSongFromPlaylist`, que envían las
     * ediciones locales a YouTube inmediatamente — para cuando esto se ejecuta, YouTube ya debería
     * reflejar cualquier cambio local, por lo que una obtención y reemplazo completos aquí es segura
     * (no una sobrescritura con pérdida de ediciones locales pendientes).
     *
     * Limitación: solo se obtiene la primera página de canciones (coincide con el resto de la carga
     * de playlists de la aplicación); una playlist con más canciones de las que caben en una página
     * no se reflejará completamente.
     */
    private suspend fun executeSinglePlaylist(playlistId: String, browseId: String) = withContext(Dispatchers.IO) {
        try {
            val page = YouTube.playlist(browseId).getOrNull() ?: return@withContext
            playlistRepository.savePlaylistWithSongs(page.playlist, page.songs)
        } catch (e: Exception) {
            Napier.e("Failed syncing playlist $playlistId ($browseId)", e)
        }
    }

    /**
     * Vuelve a obtener cada playlist que está vinculada a una playlist real de YouTube (tiene un `browseId`),
     * reconciliando cualquier edición hecha desde la aplicación/sitio de YouTube desde que LyriK la vio
     * por última vez. Los llamadores controlan esto con la configuración "Sincronizar con YouTube Music" —
     * no hay un interruptor separado por playlist (la columna `isAutoSync` existe en el esquema pero nada
     * la establece; esto opera en TODAS las playlists vinculadas en lugar de requerir una por una).
     */
    private suspend fun executeAutoSyncPlaylists() = withContext(Dispatchers.IO) {
        updateState { copy(currentOperation = "Syncing auto-sync playlists") }
        try {
            database.allPlaylists().first()
                .filter { it.browseId != null }
                .forEach { playlist ->
                    executeSinglePlaylist(playlist.id, playlist.browseId!!)
                    delay(50.milliseconds)
                }
        } catch (e: Exception) {
            Napier.e("Failed syncing auto-sync playlists", e)
        } finally {
            updateState { copy(currentOperation = "") }
        }
    }

    /** Colapsa playlists que comparten un `browseId` (puede ocurrir después de fallos de cuenta/biblioteca),
     * conservando la que tiene más canciones en caché local y eliminando el resto. Solo local, sin
     * llamadas de red. */
    private suspend fun executeCleanupDuplicates() = withContext(Dispatchers.IO) {
        try {
            database.allPlaylists().first()
                .filter { it.browseId != null }
                .groupBy { it.browseId }
                .values
                .filter { it.size > 1 }
                .forEach { duplicates ->
                    val keep = duplicates.maxByOrNull { database.countByPlaylist(it.id) }
                    duplicates.filterNot { it.id == keep?.id }.forEach { database.deletePlaylist(it.id) }
                }
        } catch (e: Exception) {
            Napier.e("Failed cleaning up duplicate playlists", e)
        }
    }

    fun cancelAllSyncs() {
        processingJob?.cancel()
        syncJob.cancel()
        updateState { SyncState() }
    }
}
