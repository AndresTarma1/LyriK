package com.example.melodist.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.melodist.data.account.AccountManager
import com.example.melodist.data.remote.ApiService
import com.example.melodist.data.repository.UserPreferencesRepository
import com.example.melodist.db.DatabaseDao
import com.example.melodist.db.entities.ArtistEntity
import com.example.melodist.download.DownloadService
import com.example.melodist.lyrics.BetterLyrics
import com.example.melodist.lyrics.LyricLine
import com.example.melodist.lyrics.SyncedLyrics
import com.metrolist.lrclib.LrcLib
import com.metrolist.kugou.KuGou
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
    val databaseDao: DatabaseDao,
    private val queueManager: QueueManager,
) : ViewModel() {
    private val log = Logger.getLogger("PlayerViewModel")

    val highResCoverArt = userPreferences.highResCoverArt

    val likedSongIds: StateFlow<Set<String>> = databaseDao.likedSongs()
        .map { list -> list.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _progressState = MutableStateFlow(PlayerProgressState())
    val progressState: StateFlow<PlayerProgressState> = _progressState.asStateFlow()

    /**
     * Emits the target position (ms) whenever the user issues a seek. Used by Listen Together to
     * broadcast host seeks; harmless when the feature is unused.
     */
    private val _seekEvents = MutableSharedFlow<Long>(extraBufferCapacity = 8)
    val seekEvents: SharedFlow<Long> = _seekEvents.asSharedFlow()

    /** User-facing transient messages (e.g. a song couldn't be played). UI shows these as snackbars. */
    private val _playbackMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val playbackMessages: SharedFlow<String> = _playbackMessages.asSharedFlow()

    /** Consecutive resolve/playback failures; auto-skip stops once this hits [MAX_CONSECUTIVE_FAILURES]. */
    private var consecutiveFailures = 0

    /**
     * When true, Listen Together is applying a remote command, so observers must not re-broadcast
     * the resulting state change (prevents host/guest feedback loops).
     */
    @Volatile
    var allowInternalSync: Boolean = false

    /**
     * True while the user is a Listen Together guest: they can't control shared playback, so the
     * local play/pause toggle is repurposed to mute/unmute their own output instead.
     */
    @Volatile
    var listenTogetherGuestMode: Boolean = false

    private var resolveJob: Job? = null
    private var fetchMoreJob: Job? = null
    private var prefetchJob: Job? = null
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
            userPreferences.crossfadeEnabled.collect { enabled ->
                playerService.setCrossfadeEnabled(enabled)
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
                    PlaybackState.PLAYING -> consecutiveFailures = 0 // a track actually started
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

        viewModelScope.launch {
            _uiState.map { it.currentSong?.id }
                .distinctUntilChanged()
                .filterNotNull()
                .collectLatest { fetchLyrics() }
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
        doToggleLike(song)
    }

    fun toggleLikeForSong(song: SongItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = databaseDao.songById(song.id).firstOrNull()
            val newLiked: Boolean
            if (entity != null) {
                newLiked = !entity.liked
                databaseDao.insertSong(entity.localToggleLike())
            } else {
                newLiked = true
                databaseDao.insertSong(song.toMediaMetadata().toSongEntity().localToggleLike())
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
            if (AccountManager.isLoggedIn) {
                YouTube.likeVideo(song.id, newLiked)
            }
        }
    }

    private fun doToggleLike(song: MediaMetadata) {
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

            if (AccountManager.isLoggedIn) {
                YouTube.likeVideo(song.id, newLiked)
            }
        }
    }

    fun playSingle(song: SongItem) = playSingle(song.toMediaMetadata())

    /**
     * Queues a single song and begins playback immediately.
     *
     * The related queue (for autoplay/next) loads in the background and does not
     * block playback initiation.
     *
     * @param song The song to play.
     */
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
        // Play immediately; the radio queue (for autoplay/next) loads in the background and never
        // blocks or fails playback — tapping a song must not depend on its radio endpoint.
        resolveAndPlay(song)
        fetchRelatedQueue(song, _uiState.value.queueSession)
    }

    /**
     * Fetches an album by browse ID and begins playback.
     *
     * If songs are found, plays the album starting from the specified index. If [shuffle] is true,
     * the queue is shuffled before playback. If no songs are found, invokes [onEmpty].
     *
     * @param browseId The album's browse identifier.
     * @param title The album title.
     * @param startIndex The queue position at which to start playback.
     * @param shuffle Whether to shuffle the queue.
     * @param onEmpty Callback invoked if the album contains no songs.
     */
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
        // As a Listen Together guest, the host owns playback — the play/pause control mutes
        // the local output instead. Remote-applied actions (allowInternalSync) bypass this.
        if (listenTogetherGuestMode && !allowInternalSync) {
            toggleMute()
            return
        }
        val state = _uiState.value
        if (state.currentSong == null || state.queue.isEmpty() || state.currentIndex !in state.queue.indices) {
            mediaSession.resetToIdle()
            return
        }
        playerService.togglePlayPause()
    }

    fun seekTo(millis: Long) {
        playerService.seekTo(millis)
        _seekEvents.tryEmit(millis)
    }

    fun setVolume(value: Int) {
        playerService.setVolume(value)
    }

    /**
     * Toggles between muted and unmuted audio output.
     */
    fun toggleMute() {
        playerService.toggleMute()
    }

    /**
     * Plays an endpoint with optional instant preview while loading the queue.
     *
     * Displays [previewSong] immediately in the miniplayer while fetching the full queue from the endpoint.
     * If queue fetching succeeds, the fetched queue replaces the preview. If fetching fails and
     * [previewSong] was provided, playback falls back to playing that song alone.
     *
     * @param previewSong An optional song to display immediately. If queue loading fails, this song is played as a single track.
     */
    fun playEndpoint(endpoint: WatchEndpoint, shuffle: Boolean = false, previewSong: MediaMetadata? = null) {
        viewModelScope.launch {
            // Show the miniplayer instantly with the clicked song instead of waiting for the
            // network queue build (YouTube.next). The real queue replaces it when it arrives.
            _uiState.update {
                it.copy(
                    currentSong = previewSong ?: it.currentSong,
                    playbackState = PlaybackState.LOADING,
                    error = null,
                )
            }
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
            } else if (previewSong != null) {
                // Radio/queue fetch failed — still play the song solo instead of erroring out.
                log.warning("next() failed for ${endpoint.videoId}; playing single song")
                playSingle(previewSong)
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
        // append() adds the song to the end of the play order, which is correct whether or not
        // shuffle is on (it lands at the end of the current shuffled order). No re-shuffle needed.
        _uiState.update { state -> PlayerQueueCoordinator.append(state, song) }
    }

    fun playNext(song: SongItem) = playNext(song.toMediaMetadata())

    fun playNext(song: MediaMetadata) {
        // insertNext() places the song right after the current one in the play order, which already
        // respects shuffle (currentIndex is an index into the shuffled order). The previous code
        // ALSO called rebuildShuffleOrder here, which duplicated the song and re-shuffled the whole
        // queue — sending the "play next" track to a random/last position. Use insertNext alone.
        _uiState.update { state -> PlayerQueueCoordinator.insertNext(state, song) }
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

    /** Re-attempts playback of the current song (for a "retry" action on a playback error). */
    fun retry() {
        val song = _uiState.value.currentSong ?: return
        _uiState.update { it.copy(error = null) }
        resolveAndPlay(song)
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

    /**
     * Plays the given song, resolving its audio source through multiple fallback strategies.
     *
     * Checks for a cached file first. If unavailable, resolves a stream URL and confirms playback
     * has started. If playback fails to start, falls back to resolving via yt-dlp. Uses request ID
     * tracking to ignore results from stale playback attempts. On success, caches song metadata and
     * logs the playback event. Updates UI state with loading and error information as needed.
     */
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

                val played: Boolean = when {
                    cachedFile != null -> {
                        playerService.play(cachedFile.absolutePath)
                        true
                    }
                    YtDlpResolver.needsYtDlp(song.id) -> {
                        // Known-hard video (all in-process clients 403 this session): skip straight to
                        // yt-dlp instead of repeating the slow resolve + mpv-failure cycle.
                        playViaYtDlp(song, requestId)
                    }
                    else -> {
                        val streamUrl = withContext(Dispatchers.IO) {
                            streamResolver.resolveAudioStream(song.id)
                        }.streamUrl
                        if (requestId != playRequestId) return@launch

                        if (streamUrl.isNotEmpty()) {
                            playerService.play(streamUrl)
                            // The resolved stream can pass HTTP validation yet 403 in mpv (e.g. spc-gated
                            // IOS URLs). If playback doesn't actually start, fall back to yt-dlp, which
                            // handles the hard videos our in-process pipeline can't.
                            val started = playerService.awaitPlaybackStarted()
                            if (started || requestId != playRequestId) {
                                true
                            } else {
                                YtDlpResolver.markNeedsYtDlp(song.id)
                                Napier.w("Stream did not start for ${song.id}; trying yt-dlp fallback")
                                playViaYtDlp(song, requestId)
                            }
                        } else {
                            false
                        }
                    }
                }
                if (requestId != playRequestId) return@launch

                if (!played) {
                    handlePlaybackFailure(song)
                    return@launch
                }

                // Guardar canción en caché y registrar reproducción
                withContext(Dispatchers.IO) {
                    cacheSongMetadata(song)
                    databaseDao.insertEvent(
                        songId = song.id,
                        timestamp = java.time.LocalDateTime.now(),
                        playTime = 0L,
                    )
                }

                // Warm the next track's stream cache so skipping to it is near-instant.
                prefetchNext()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.e("Playback failed for ${song.id} - ${song.title}", e)
                if (requestId == playRequestId) handlePlaybackFailure(song)
            }
        }
    }

    companion object {
        private const val MAX_CONSECUTIVE_FAILURES = 5
    }

    /**
     * A track couldn't be resolved/played. Notify the user and auto-advance to the next track,
     * unless too many tracks in a row have failed (likely a broader problem) — then stop.
     */
    private fun handlePlaybackFailure(song: MediaMetadata) {
        consecutiveFailures++
        _uiState.update { it.copy(error = null) }
        val hasNext = PlayerQueueCoordinator.nextIndex(_uiState.value) != null
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES || !hasNext) {
            consecutiveFailures = 0
            _playbackMessages.tryEmit("No se pudo reproducir «${song.title}»")
            _uiState.update { it.copy(playbackState = PlaybackState.ERROR) }
        } else {
            _playbackMessages.tryEmit("No se pudo reproducir «${song.title}», pasando a la siguiente")
            next()
        }
    }

    /**
     * Pre-resolves the next track's stream URL in the background so [next] is near-instant
     * (it warms the same StreamCache entry [resolveAndPlay] will hit). Skips cached files and
     * known yt-dlp-only videos (re-resolving those in-process is pointless).
     */
    private fun prefetchNext() {
        val state = _uiState.value
        val nextIdx = PlayerQueueCoordinator.nextIndex(state) ?: return
        val nextSong = state.queueSession.order.getOrNull(nextIdx)
            ?.let(state.queueSession.items::getOrNull) ?: return
        if (YtDlpResolver.needsYtDlp(nextSong.id)) return
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                if (DownloadService.getCachedFile(nextSong.id) != null) return@launch
                streamResolver.resolveAudioStream(nextSong.id) // result discarded; StreamCache warmed
            } catch (_: Exception) {
                // best-effort prefetch
            }
        }
    }

    /**
     * Resolves [song] via yt-dlp and plays it; keeps the miniplayer in LOADING meanwhile.
     * Returns true if playback was started, false if the URL couldn't be resolved (caller handles
     * the failure / skip).
     */
    private suspend fun playViaYtDlp(song: MediaMetadata, requestId: Long): Boolean {
        _uiState.update { it.copy(playbackState = PlaybackState.LOADING, error = null) }
        val ytUrl = withContext(Dispatchers.IO) {
            YtDlpResolver.resolveAudioUrl(song.id, streamResolver.currentAudioQuality())
        }
        if (requestId != playRequestId) return true // superseded — not a failure
        return if (ytUrl != null) {
            playerService.play(ytUrl)
            true
        } else {
            false
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
                // Songs coming from Listen Together (and some search results) carry only an artist
                // name, no YouTube id. Without an id the song↔artist mapping was skipped entirely, so
                // history showed no artist. Fall back to a stable synthetic id so the name persists.
                val id = artist.id ?: "local:${artist.name.trim().lowercase().hashCode()}"
                if (artist.name.isBlank()) return@forEachIndexed
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

    private val _currentLyrics = MutableStateFlow<String?>(null)
    val currentLyrics: StateFlow<String?> = _currentLyrics.asStateFlow()

    /** Time-synced lyrics (BetterLyrics or synced YouTube LRC). Null when only plain text exists. */
    private val _syncedLyrics = MutableStateFlow<List<LyricLine>?>(null)
    val syncedLyrics: StateFlow<List<LyricLine>?> = _syncedLyrics.asStateFlow()

    fun fetchLyrics() {
        val song = _uiState.value.currentSong ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _currentLyrics.value = null
            _syncedLyrics.value = null

            val artist = song.artists.joinToString(", ") { it.name }
            val album = song.album?.title

            // Try synced providers in order of reliability. The first usable LRC wins.
            //   1) LrcLib  — free, reliable, line-synced
            //   2) KuGou   — line-synced
            //   3) BetterLyrics — word-synced (best UX, but may be auth-gated/unavailable)
            val lrc = runCatching { LrcLib.getLyrics(song.title, artist, song.duration, album).getOrNull() }.getOrNull()
                ?: runCatching { KuGou.getLyrics(song.title, artist, song.duration, album).getOrNull() }.getOrNull()
                ?: runCatching { BetterLyrics.getLyrics(song.title, artist, song.duration, album) }.getOrNull()

            if (lrc != null) {
                if (SyncedLyrics.isSynced(lrc)) {
                    val parsed = SyncedLyrics.parse(lrc)
                    if (parsed.isNotEmpty()) {
                        _syncedLyrics.value = parsed
                        _currentLyrics.value = parsed.joinToString("\n") { it.text }
                        return@launch
                    }
                }
                // Plain LRC with no timestamps — show as text.
                _currentLyrics.value = lrc
                return@launch
            }

            // Fall back to YouTube lyrics (usually plain text, occasionally LRC).
            try {
                val nextResult = YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull() ?: return@launch
                val endpoint = nextResult.lyricsEndpoint ?: return@launch
                val lyrics = YouTube.lyrics(endpoint).getOrNull()
                _currentLyrics.value = lyrics
                if (lyrics != null && SyncedLyrics.isSynced(lyrics)) {
                    _syncedLyrics.value = SyncedLyrics.parse(lyrics).takeIf { it.isNotEmpty() }
                }
            } catch (_: Exception) {
                _currentLyrics.value = null
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
