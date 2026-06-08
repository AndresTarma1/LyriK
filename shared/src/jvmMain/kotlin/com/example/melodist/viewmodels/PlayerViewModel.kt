package com.example.melodist.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.melodist.data.remote.ApiService
import com.example.melodist.data.repository.UserPreferencesRepository
import com.example.melodist.db.DatabaseDao
import com.example.melodist.db.entities.ArtistEntity
import com.example.melodist.download.DownloadService
import com.example.melodist.models.MediaMetadata
import com.example.melodist.models.toMediaMetadata
import com.example.melodist.player.*
import com.example.melodist.utils.withMissingMetadataResolved
import com.example.melodist.viewmodels.queues.YouTubePlaylistQueue
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import io.github.aakira.napier.Napier
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.HttpURLConnection
import java.net.URI
import java.util.logging.Logger

class PlayerViewModel(
    private val playerService: PlayerService,
    private val streamResolver: AudioStreamResolver,
    private val mediaSession: WindowsMediaSession,
    private val apiService: ApiService,
    private val userPreferences: UserPreferencesRepository,
    private val databaseDao: DatabaseDao,
    private val queueManager: QueueManager,
) : ViewModel() {

    val highResCoverArt = userPreferences.highResCoverArt

    private val log = Logger.getLogger("PlayerViewModel")

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _progressState = MutableStateFlow(PlayerProgressState())
    val progressState: StateFlow<PlayerProgressState> = _progressState.asStateFlow()

    private var resolveJob: Job? = null
    private var fetchMoreJob: Job? = null
    private var playRequestId = 0L
    private var currentQueue: Queue? = null

    /**
     * ✅ Inicialización diferida de PlayerService y MediaSession.
     * Se llama desde main.kt solo cuando el ViewModel se crea por primera vez,
     * evitando la inicialización pesada al arrancar la app.
     */
    fun initialize() {
        playerService.init()
        mediaSession.initialize()
    }

    init {
        viewModelScope.launch {
            userPreferences.equalizerBands.collect { bands ->
                playerService.setEqualizer(bands)
            }
        }

        viewModelScope.launch {
            playerService.playbackState.collect { state ->
                _uiState.update { it.copy(playbackState = state) }

                mediaSession.setPlaybackStatus(
                    isPlaying = state == PlaybackState.PLAYING,
                    isPaused = state == PlaybackState.PAUSED
                )

                when (state) {
                    PlaybackState.ENDED -> onTrackEnded()
                    PlaybackState.ERROR -> log.warning("Playback error; user can retry manually")
                    else -> Unit
                }
            }
        }

        viewModelScope.launch {
            playerService.position
                .combine(playerService.duration) { pos, dur -> pos to dur }
                .distinctUntilChanged()
                .collect { (pos, dur) ->
                    _progressState.update { it.copy(positionMs = pos, durationMs = dur) }
                }
        }

        viewModelScope.launch {
            playerService.volume.collect { vol ->
                _uiState.update { it.copy(volume = vol) }
            }
        }

        viewModelScope.launch {
            _uiState
                .map { it.currentSong }
                .distinctUntilChanged()
                .collectLatest { song ->
                    if (song != null) {
                        val thumbUri = withContext(Dispatchers.IO) {
                            downloadThumbToTemp(song.thumbnailUrl)
                        }

                        mediaSession.updateMetadata(
                            title = song.title,
                            artist = song.artists.joinToString(", ") { it.name },
                            album = song.album?.title ?: "",
                            thumbnailUrl = thumbUri
                        )
                    } else {
                        mediaSession.resetToIdle()
                    }
                }
        }

    }

    suspend fun downloadThumbToTemp(url: String?): String? = withContext(Dispatchers.IO)  {
        if (url.isNullOrBlank()) return@withContext null
        return@withContext try {
            val hash = url.hashCode()
            val tmpFile = java.io.File(System.getProperty("java.io.tmpdir"), "melodist_smtc_thumb_$hash.jpg")

            if (tmpFile.exists()) {
                return@withContext "file:///${tmpFile.absolutePath.replace('\\', '/')}"
            }

            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connect()
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()

            tmpFile.writeBytes(bytes)
            "file:///${tmpFile.absolutePath.replace('\\', '/')}"
        } catch (e: Exception) {
            log.fine("SMTC thumb download failed: ${e.message}")
            url
        }
    }

    fun toggleLike() {
        val song = _uiState.value.currentSong ?: return
        val newLiked = !song.liked
        val newLikedDate = if (newLiked) java.time.LocalDateTime.now() else null

        _uiState.update { state ->
            state.currentSong?.let { current ->
                state.copy(currentSong = current.copy(liked = newLiked, likedDate = newLikedDate))
            } ?: state
        }

        viewModelScope.launch(Dispatchers.IO) {
            val entity = databaseDao.songById(song.id).firstOrNull()
            if (entity != null) {
                databaseDao.insertSong(entity.localToggleLike())
            } else {
                databaseDao.insertSong(song.toSongEntity().localToggleLike())
                song.artists.forEachIndexed { i, artist ->
                    artist.id?.let { id ->
                        val artistEntity = ArtistEntity(
                            id = id,
                            name = artist.name,
                            lastUpdateTime = java.time.LocalDateTime.now()
                        )
                        databaseDao.insertArtist(artistEntity)
                        databaseDao.insertSongArtistMap(song.id, id, i)
                    }
                }
                song.album?.let { album ->
                    databaseDao.insertSongAlbumMap(song.id, album.id, 0)
                }
            }
        }
    }

    fun playSingle(song: SongItem) = playSingle(song.toMediaMetadata())

    fun playSingle(song: MediaMetadata) {
        val queue = LocalQueue(QueueSource.Single(song.id), listOf(song), 0)
        currentQueue = queue
        val uiState = queueManager.buildUiState(queue, false)
        
        _uiState.update { current ->
            current.copy(
                currentSong = uiState.currentSong,
                queue = uiState.queue,
                currentIndex = uiState.currentIndex,
                queueSource = uiState.queueSource,
                error = null,
                isShuffled = uiState.isShuffled,
                queueSession = uiState.queueSession,
            )
        }
        resolveAndPlay(song)
    }

    fun playAlbumFromBrowseId(
        browseId: String,
        playlistId: String? = null,
        title: String,
        startIndex: Int = 0,
        shuffle: Boolean = false,
        onEmpty: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            val songs = apiService.getAlbum(browseId).getOrNull()?.songs.orEmpty()
            if (songs.isNotEmpty()) {
                playAlbum(songs, startIndex, browseId, title)
                if (shuffle) _uiState.update(PlayerQueueCoordinator::shuffleFromStart)
            } else {
                onEmpty?.invoke()
            }
        }
    }

    fun playPlaylistFromId(
        playlistId: String,
        endpoint: WatchEndpoint? = null,
        title: String,
        startIndex: Int = 0,
        shuffle: Boolean = false,
        onEmpty: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            val page = YouTube.playlist(playlistId).getOrNull()
            if (page == null || page.songs.isEmpty()) {
                onEmpty?.invoke()
                return@launch
            }

            val queue = YouTubePlaylistQueue(
                playlistId = playlistId,
                playlistTitle = title,
                initialSongs = page.songs,
                initialContinuation = page.songsContinuation?.takeIf { it.isNotBlank() },
                startIndex = startIndex,
            )
            playPlaylistWithQueue(queue, shuffle)
        }
    }

    @JvmName("playAlbumFromSongItems")
    fun playAlbum(songs: List<SongItem>, startIndex: Int = 0, browseId: String, title: String) =
        playAlbum(songs.map { it.toMediaMetadata() }, startIndex, browseId, title)

    fun playAlbum(songs: List<MediaMetadata>, startIndex: Int = 0, browseId: String, title: String) {
        if (songs.isEmpty()) return
        val queue = LocalQueue(QueueSource.Album(browseId, title), songs, startIndex)
        currentQueue = queue
        val uiState = queueManager.buildUiState(queue, false)
        
        _uiState.update { current ->
            current.copy(
                currentSong = uiState.currentSong,
                queue = uiState.queue,
                currentIndex = uiState.currentIndex,
                queueSource = uiState.queueSource,
                error = null,
                isShuffled = uiState.isShuffled,
                queueSession = uiState.queueSession,
            )
        }
        uiState.currentSong?.let(::resolveAndPlay)
    }

    @JvmName("playPlaylistFromSongItems")
    fun playPlaylist(songs: List<SongItem>, startIndex: Int = 0, playlistId: String, title: String) =
        playPlaylist(songs.map { it.toMediaMetadata() }, startIndex, playlistId, title)

    fun playPlaylist(songs: List<MediaMetadata>, startIndex: Int = 0, playlistId: String, title: String) {
        if (songs.isEmpty()) return
        val queue = LocalQueue(QueueSource.Playlist(playlistId, title), songs, startIndex)
        currentQueue = queue
        val uiState = queueManager.buildUiState(queue, false)
        
        _uiState.update { current ->
            current.copy(
                currentSong = uiState.currentSong,
                queue = uiState.queue,
                currentIndex = uiState.currentIndex,
                queueSource = uiState.queueSource,
                error = null,
                isShuffled = uiState.isShuffled,
                queueSession = uiState.queueSession,
            )
        }
        uiState.currentSong?.let(::resolveAndPlay)
    }

    fun playPlaylistWithQueue(queue: YouTubePlaylistQueue, shuffle: Boolean = false) {
        if (queue.initialSongs.isEmpty()) return
        
        val implQueue = YouTubePlaylistQueueImpl(
            playlistId = queue.playlistId,
            playlistTitle = queue.playlistTitle,
            initialSongs = queue.initialSongs,
            initialContinuation = queue.initialContinuation,
            startIndex = queue.startIndex,
        )
        currentQueue = implQueue
        
        val uiState = queueManager.buildUiState(implQueue, shuffle)
        
        _uiState.update { current ->
            current.copy(
                currentSong = uiState.currentSong,
                queue = uiState.queue,
                currentIndex = uiState.currentIndex,
                queueSource = uiState.queueSource,
                error = null,
                isShuffled = uiState.isShuffled,
                queueSession = uiState.queueSession,
            )
        }
        checkAndFetchMoreSongs(_uiState.value, _uiState.value.currentIndex)
        _uiState.value.currentSong?.let(::resolveAndPlay)
    }

    @JvmName("playCustomFromSongItems")
    fun playCustom(songs: List<SongItem>, startIndex: Int = 0) =
        playCustom(songs.map { it.toMediaMetadata() }, startIndex)

    fun playCustom(songs: List<MediaMetadata>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val queue = LocalQueue(QueueSource.Custom, songs, startIndex)
        currentQueue = queue
        val uiState = queueManager.buildUiState(queue, false)
        
        _uiState.update { current ->
            current.copy(
                currentSong = uiState.currentSong,
                queue = uiState.queue,
                currentIndex = uiState.currentIndex,
                queueSource = uiState.queueSource,
                error = null,
                isShuffled = uiState.isShuffled,
                queueSession = uiState.queueSession,
            )
        }
        uiState.currentSong?.let(::resolveAndPlay)
    }

    fun togglePlayPause() {
        val state = _uiState.value
        if (state.currentSong == null || state.queue.isEmpty() || state.currentIndex !in state.queue.indices) {
            mediaSession.resetToIdle()
            return
        }
        playerService.togglePlayPause()
    }

    fun seekTo(millis: Long) {
        playerService.seekTo(millis)
    }

    fun setVolume(value: Int) {
        playerService.setVolume(value)
    }

    fun toggleMute() {
        playerService.toggleMute()
    }

    fun playEndpoint(endpoint: WatchEndpoint, shuffle: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(playbackState = PlaybackState.LOADING, error = null) }
            val result = withContext(Dispatchers.IO) {
                YouTube.next(endpoint).getOrNull()
            }

            if (result != null && result.items.isNotEmpty()) {
                val songs = result.items.map { it.toMediaMetadata() }
                val startIdx = result.currentIndex ?: 0
                val source = QueueSource.Playlist(endpoint.playlistId ?: endpoint.videoId ?: "", result.title ?: "Playlist")

                val session = PlayerQueueCoordinator.collectionSession(source, songs, startIdx)
                    .copy(continuation = result.continuation, endpoint = endpoint)

                _uiState.update { current ->
                    val base = current.copy(
                        currentSong = session.currentSong(),
                        queue = session.queueItems(),
                        currentIndex = session.currentIndex,
                        queueSource = source,
                        error = null,
                        isShuffled = false,
                        queueSession = session
                    )
                    if (shuffle) PlayerQueueCoordinator.shuffleFromStart(base) else base
                }
                _uiState.value.currentSong?.let(::resolveAndPlay)
            } else {
                _uiState.update { it.copy(playbackState = PlaybackState.IDLE, error = "No se pudieron cargar las canciones de la lista.") }
            }
        }
    }

    private fun checkAndFetchMoreSongs(state: PlayerUiState, nextIndex: Int) {
        val session = state.queueSession

        if (session.playlistQueue != null && session.playlistQueue.hasNextPage() && nextIndex >= session.order.size - 3) {
            fetchMoreJob?.cancel()
            fetchMoreJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    val newSongs = session.playlistQueue.loadNextPage()
                    if (newSongs.isNotEmpty()) {
                        val newMetadata = newSongs.map { it.toMediaMetadata() }
                        _uiState.update { currentState ->
                            val currentSession = currentState.queueSession
                            val updatedItems = currentSession.items + newMetadata
                            val newOrder = currentSession.order + newMetadata.indices.map { currentSession.items.size + it }
                            val updatedSession = currentSession.copy(
                                items = updatedItems,
                                order = newOrder,
                                playlistQueue = session.playlistQueue,
                            )
                            currentState.copy(
                                queueSession = updatedSession,
                                queue = updatedSession.queueItems()
                            )
                        }
                    }
                } catch (e: Exception) {
                    log.warning("Error fetching more playlist songs: ${e.message}")
                }
            }
            return
        }

        if (session.continuation != null && session.endpoint != null && nextIndex >= session.order.size - 3) {
            // Prevent multiple parallel fetches
            val currentContinuation = session.continuation
            _uiState.update { it.copy(queueSession = it.queueSession.copy(continuation = null)) }

            fetchMoreJob?.cancel()
            fetchMoreJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    val result = YouTube.next(session.endpoint, currentContinuation).getOrNull()
                    if (result != null && result.items.isNotEmpty()) {
                        val newSongs = result.items.map { it.toMediaMetadata() }

                        _uiState.update { currentState ->
                            val currentSession = currentState.queueSession
                            val updatedItems = currentSession.items + newSongs
                            val newOrder = currentSession.order + newSongs.indices.map { currentSession.items.size + it }

                            val updatedSession = currentSession.copy(
                                items = updatedItems,
                                order = newOrder,
                                continuation = result.continuation
                            )

                            currentState.copy(
                                queueSession = updatedSession,
                                queue = updatedSession.queueItems()
                            )
                        }
                    } else {
                        // Restore continuation if fetching failed and we want to retry?
                        // Depending on the implementation, you could restore it or leave it null.
                        if (result?.continuation != null) {
                            _uiState.update { it.copy(queueSession = it.queueSession.copy(continuation = result.continuation)) }
                        }
                    }
                } catch (e: Exception) {
                    log.warning("Error fetching more songs: ${e.message}")
                    // restore token to try again later
                    _uiState.update { it.copy(queueSession = it.queueSession.copy(continuation = currentContinuation)) }
                }
            }
        }
    }

    fun next() {
        val state = _uiState.value
        val nextIndex = PlayerQueueCoordinator.nextIndex(state) ?: run {
            if (state.repeatMode == RepeatMode.OFF) stop()
            return
        }

        _uiState.update {
            val updatedSession = it.queueSession.copy(currentIndex = nextIndex)
            it.copy(
                currentIndex = nextIndex,
                currentSong = updatedSession.order.getOrNull(nextIndex)?.let(updatedSession.items::getOrNull),
                queueSession = updatedSession,
                queue = updatedSession.queueItems()
            )
        }

        // Use QueueManager for auto-load if we have a queue
        currentQueue?.let { queue ->
            viewModelScope.launch(Dispatchers.IO) {
                val newItems = queueManager.checkAndLoadMore(queue, nextIndex, _uiState.value.queueSession.items.size)
                if (newItems.isNotEmpty()) {
                    _uiState.update { currentState ->
                        val currentSession = currentState.queueSession
                        val updatedItems = currentSession.items + newItems
                        val newOrder = currentSession.order + newItems.indices.map { currentSession.items.size + it }
                        val updatedSession = currentSession.copy(
                            items = updatedItems,
                            order = newOrder,
                        )
                        currentState.copy(
                            queueSession = updatedSession,
                            queue = updatedSession.queueItems()
                        )
                    }
                }
            }
        }
        // Fallback for old-style queues without Queue interface
        if (currentQueue == null) {
            checkAndFetchMoreSongs(_uiState.value, nextIndex)
        }

        playAtIndex(nextIndex)
    }

    fun previous() {
        val state = _uiState.value
        if (state.queueSession.items.isEmpty()) return

        if (_progressState.value.positionMs > 3000) {
            seekTo(0)
            return
        }

        val prevIndex = PlayerQueueCoordinator.previousIndex(state) ?: return
        playAtIndex(prevIndex)
    }

    fun toggleShuffle() {
        _uiState.update(PlayerQueueCoordinator::toggleShuffle)
    }

    fun toggleRepeat() {
        _uiState.update {
            it.copy(
                repeatMode = when (it.repeatMode) {
                    RepeatMode.OFF -> RepeatMode.ALL
                    RepeatMode.ALL -> RepeatMode.ONE
                    RepeatMode.ONE -> RepeatMode.OFF
                }
            )
        }
    }

    fun stop() {
        resolveJob?.cancel()
        playRequestId += 1
        playerService.stop()
        _progressState.value = PlayerProgressState()
        _uiState.update {
            it.copy(
                currentSong = null,
                queue = emptyList(),
                currentIndex = 0,
                playbackState = PlaybackState.IDLE
            )
        }
        mediaSession.resetToIdle()
    }

    fun addToQueue(song: SongItem) = addToQueue(song.toMediaMetadata())

    fun addToQueue(song: MediaMetadata) {
        _uiState.update { state ->
            val newState = PlayerQueueCoordinator.append(state, song)
            // Rebuild shuffle order if shuffle is enabled
            if (newState.isShuffled) {
                val currentIndex = newState.currentIndex
                val items = newState.queueSession.items
                val order = newState.queueSession.order
                val rebuilt = queueManager.rebuildShuffleOrder(
                    items = items,
                    order = order,
                    currentIndex = currentIndex,
                    newItems = listOf(song),
                )
                val updatedSession = newState.queueSession.copy(
                    items = rebuilt.first,
                    order = rebuilt.second,
                )
                newState.copy(
                    queueSession = updatedSession,
                    queue = updatedSession.queueItems(),
                )
            } else {
                newState
            }
        }
    }

    fun playNext(song: SongItem) = playNext(song.toMediaMetadata())

    fun playNext(song: MediaMetadata) {
        _uiState.update { state ->
            val newState = PlayerQueueCoordinator.insertNext(state, song)
            // Rebuild shuffle order if shuffle is enabled
            if (newState.isShuffled) {
                val currentIndex = newState.currentIndex
                val items = newState.queueSession.items
                val order = newState.queueSession.order
                val rebuilt = queueManager.rebuildShuffleOrder(
                    items = items,
                    order = order,
                    currentIndex = currentIndex,
                    newItems = listOf(song),
                )
                val updatedSession = newState.queueSession.copy(
                    items = rebuilt.first,
                    order = rebuilt.second,
                )
                newState.copy(
                    queueSession = updatedSession,
                    queue = updatedSession.queueItems(),
                )
            } else {
                newState
            }
        }
    }

    fun playNextResolved(song: SongItem) {
        viewModelScope.launch {
            val resolvedSong = withContext(Dispatchers.IO) { song.withMissingMetadataResolved() }
            playNext(resolvedSong)
        }
    }

    fun addToQueueResolved(song: SongItem) {
        viewModelScope.launch {
            val resolvedSong = withContext(Dispatchers.IO) { song.withMissingMetadataResolved() }
            addToQueue(resolvedSong)
        }
    }

    fun removeFromQueue(index: Int) {
        val state = _uiState.value
        if (index < 0 || index >= state.queueSession.order.size) return

        val session = state.queueSession
        val newOrder = session.order.toMutableList().apply { removeAt(index) }
        val newItems = session.items
        val newIndex = when {
            newOrder.isEmpty() -> {
                stop()
                return
            }

            index < state.currentIndex -> state.currentIndex - 1
            index == state.currentIndex -> {
                val nextIdx = index.coerceAtMost(newOrder.lastIndex)
                val nextSong = newOrder.getOrNull(nextIdx)?.let(newItems::getOrNull)
                _uiState.update {
                    it.copy(
                        queue = newOrder.mapNotNull { idx -> newItems.getOrNull(idx) },
                        currentIndex = nextIdx,
                        currentSong = nextSong,
                        queueSession = session.copy(order = newOrder, currentIndex = nextIdx)
                    )
                }
                nextSong?.let(::resolveAndPlay)
                return
            }

            else -> state.currentIndex
        }
        _uiState.update {
            it.copy(
                queue = newOrder.mapNotNull { idx -> newItems.getOrNull(idx) },
                currentIndex = newIndex,
                queueSession = session.copy(order = newOrder, currentIndex = newIndex)
            )
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        _uiState.update { state -> PlayerQueueCoordinator.move(state, fromIndex, toIndex) }
    }

    fun playAtIndex(index: Int) {
        val state = _uiState.value
        if (index < 0 || index >= state.queueSession.order.size) return

        val song = state.queueSession.order.getOrNull(index)?.let(state.queueSession.items::getOrNull) ?: return
        _progressState.update { it.copy( positionMs = 0, durationMs = song.duration.toLong() * 1000) }
        _uiState.update {
            it.copy(
                currentSong = song,
                currentIndex = index,
                error = null,
                queueSession = state.queueSession.copy(currentIndex = index)
            )
        }
        checkAndFetchMoreSongs(_uiState.value, index)
        resolveAndPlay(song)
    }

    private fun resolveAndPlay(song: MediaMetadata) {
        resolveJob?.cancel()
        playRequestId += 1
        val requestId = playRequestId

        _progressState.update { it.copy(positionMs = 0, durationMs = song.duration.toLong() * 1000) }

        resolveJob = viewModelScope.launch {
            _uiState.update { it.copy(playbackState = PlaybackState.LOADING, error = null) }
            _progressState.update { it.copy(positionMs = 0, durationMs = song.duration.toLong() * 1000) }
            playerService.stopAudioOnly()

            try {
                val cachedFile = withContext(Dispatchers.IO) {
                    DownloadService.getCachedFile(song.id)
                }
                if (requestId != playRequestId) return@launch

                if (cachedFile != null) {
                    playerService.play(cachedFile.absolutePath)
                } else {
                    val streamUrl = withContext(Dispatchers.IO) {
                        streamResolver.resolveAudioStream(song.id)
                    }.streamUrl
                    if (requestId != playRequestId) return@launch

                    if (streamUrl.isNotEmpty()) {
                        playerService.play(streamUrl)
                    } else if (requestId == playRequestId) {
                        _uiState.update { it.copy(error = "No se pudo obtener el audio para \"${song.title}\"") }
                    }
                }
                if (requestId != playRequestId) return@launch

                // Guardar canción en caché y registrar reproducción
                withContext(Dispatchers.IO) {
                    cacheSongMetadata(song)
                    databaseDao.insertEvent(
                        songId = song.id,
                        timestamp = java.time.LocalDateTime.now(),
                        playTime = 0L,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.e("Playback failed for ${song.id} - ${song.title}", e)
                if (requestId == playRequestId) {
                    _uiState.update { it.copy(error = e.message) }
                }
            }
        }
    }

    private suspend fun cacheSongMetadata(song: MediaMetadata) {
        val exists = databaseDao.songById(song.id).firstOrNull() != null
        if (!exists) {
            databaseDao.insertSong(song.toSongEntity())
        }
        val currentArtists = databaseDao.artistsForSong(song.id)
        if (currentArtists.isEmpty() && song.artists.isNotEmpty()) {
            song.artists.forEachIndexed { i, artist ->
                artist.id?.let { id ->
                    val artistEntity = ArtistEntity(
                        id = id,
                        name = artist.name,
                        lastUpdateTime = java.time.LocalDateTime.now()
                    )
                    databaseDao.insertArtist(artistEntity)
                    databaseDao.insertSongArtistMap(song.id, id, i)
                }
            }
        }
        song.album?.let { album ->
            val currentAlbum = databaseDao.albumForSong(song.id)
            if (currentAlbum == null) {
                databaseDao.insertSongAlbumMap(song.id, album.id, 0)
            }
        }
    }

    private fun onTrackEnded() {
        val state = _uiState.value
        when (state.repeatMode) {
            RepeatMode.ONE -> state.currentSong?.let(::resolveAndPlay)
            RepeatMode.ALL -> next()
            RepeatMode.OFF -> if (state.currentIndex < state.queue.lastIndex) next()
        }
    }

    private fun fetchRelatedQueue(song: MediaMetadata, sessionSeed: QueueSession) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val endpoint = WatchEndpoint(videoId = song.id)
                val result = YouTube.next(endpoint).getOrNull() ?: return@launch
                val originalSongId = song.id

                _uiState.update { state ->
                    if (state.currentSong?.id != originalSongId || sessionSeed.source !is QueueSource.Single) return@update state

                    val suggestedCurrent = result.items.find { it.id == originalSongId }?.toMediaMetadata()
                    val related = result.items
                        .filter { it.id != originalSongId }
                        .map { it.toMediaMetadata() }
                    val items = listOfNotNull(
                        state.currentSong.let {
                            if (it.duration <= 0 && suggestedCurrent != null && suggestedCurrent.duration > 0) {
                                it.copy(duration = suggestedCurrent.duration)
                            } else {
                                it
                            }
                        }
                    ) + related
                    val order = items.indices.toList()

                    state.copy(
                        currentSong = items.firstOrNull(),
                        queue = items,
                        currentIndex = 0,
                        queueSource = QueueSource.Single(originalSongId),
                        isShuffled = false,
                        queueSession = QueueSession(
                            source = QueueSource.Single(originalSongId),
                            items = items,
                            order = order,
                            currentIndex = 0
                        )
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    override fun onCleared() {
        resolveJob?.cancel()
        playRequestId += 1
        resolveJob = null
        playerService.stopAudioOnly()
        super.onCleared()
    }
}
