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
import com.example.melodist.utils.NetworkMonitor
import com.example.melodist.utils.PendingAction
import com.example.melodist.utils.PendingSyncQueue
import com.example.melodist.utils.awaitOnline
import com.example.melodist.utils.withMissingMetadataResolved
import com.example.melodist.viewmodels.queues.YouTubePlaylistQueue
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.MediaInfo
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
    private val pendingSyncQueue: PendingSyncQueue,
) : ViewModel() {
    private val log = Logger.getLogger("PlayerViewModel")

    val highResCoverArt = userPreferences.highResCoverArt
    val seekBarStyle = userPreferences.seekBarStyle
    val playbackSpeed = userPreferences.playbackSpeed

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch { userPreferences.setPlaybackSpeed(speed) }
    }

    val likedSongIds: StateFlow<Set<String>> = databaseDao.likedSongs()
        .map { list -> list.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _progressState = MutableStateFlow(PlayerProgressState())
    val progressState: StateFlow<PlayerProgressState> = _progressState.asStateFlow()

    /**
     * Emite la posición objetivo (ms) cada vez que el usuario realiza un salto. Se usa en Listen Together
     * para transmitir los saltos del anfitrión; es inofensivo cuando la función no está en uso.
     */
    private val _seekEvents = MutableSharedFlow<Long>(extraBufferCapacity = 8)
    val seekEvents: SharedFlow<Long> = _seekEvents.asSharedFlow()

    /** Mensajes transitorios para el usuario (por ejemplo, si una canción no se pudo reproducir). La UI los muestra como snackbars. */
    private val _playbackMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val playbackMessages: SharedFlow<String> = _playbackMessages.asSharedFlow()

    /** Fallos consecutivos de resolución/reproducción; el salto automático se detiene al alcanzar [MAX_CONSECUTIVE_FAILURES]. */
    private var consecutiveFailures = 0

    /**
     * Cuando es verdadero, Listen Together está aplicando un comando remoto, por lo que los
     * observadores no deben retransmitir el cambio de estado resultante (previene bucles de
     * retroalimentación entre anfitrión e invitado).
     */
    @Volatile
    var allowInternalSync: Boolean = false

    /**
     * Verdadero mientras el usuario es invitado en Listen Together: no puede controlar la
     * reproducción compartida, por lo que el botón de play/pause local se reutiliza para
     * silenciar/activar su propia salida de audio.
     */
    @Volatile
    var listenTogetherGuestMode: Boolean = false

    private var resolveJob: Job? = null
    private var fetchMoreJob: Job? = null
    private var prefetchJob: Job? = null
    private var reconnectWatchJob: Job? = null
    private var playRequestId = 0L
    private var currentQueue: Queue? = null

    /** Verdadero cuando el usuario activó el modo sin conexión, o una sonda de conectividad indica que estamos fuera de línea. */
    private suspend fun isEffectivelyOffline(): Boolean =
        userPreferences.offlineModeEnabled.first() || !NetworkMonitor.isOnline()

    /**
     * ✅ Inicialización diferida de PlayerService (mpv).
     * Se llama desde main.kt solo cuando el ViewModel se crea por primera vez,
     * evitando la inicialización pesada al arrancar la app.
     *
     * MediaSession (SMTC) NO se inicializa aquí intencionalmente: sus controles de transporte
     * necesitan que el hilo que la crea siga vivo ejecutando un bucle de mensajes de Windows,
     * a diferencia de mpv. Se inicializa por separado en main.kt en el Event Dispatch Thread
     * de AWT, que vive durante toda la vida de la aplicación.
     */
    fun initialize() {
        playerService.init()
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
            userPreferences.playbackSpeed.collect { speed ->
                playerService.setPlaybackSpeed(speed)
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
                    PlaybackState.PLAYING -> consecutiveFailures = 0 // una pista realmente inició
                    PlaybackState.ENDED -> onTrackEnded()
                    PlaybackState.ERROR -> log.warning("Error de reproducción; el usuario puede reintentar manualmente")
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
                .collectLatest { fetchLyrics(); fetchMetadataInfo() }
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
                    log.fine("Error al descargar miniatura SMTC: ${e.message}")
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
                com.example.melodist.utils.retryWithBackoff { YouTube.likeVideo(song.id, newLiked) }
                    .onFailure {
                        Napier.w("Failed to push like state for ${song.id}: ${it.message}")
                        pendingSyncQueue.enqueue(PendingAction.LikeSong(song.id, newLiked))
                    }
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
                com.example.melodist.utils.retryWithBackoff { YouTube.likeVideo(song.id, newLiked) }
                    .onFailure {
                        Napier.w("Failed to push like state for ${song.id}: ${it.message}")
                        pendingSyncQueue.enqueue(PendingAction.LikeSong(song.id, newLiked))
                    }
            }
        }
    }

    fun playSingle(song: SongItem) = playSingle(song.toMediaMetadata())

    /**
     * Encola una sola canción e inicia la reproducción inmediatamente.
     *
     * La cola relacionada (para reproducción automática/siguiente) se carga en segundo plano
     * y no bloquea el inicio de la reproducción.
     *
     * @param song La canción a reproducir.
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
        // Reproducir inmediatamente; la cola de radio (para reproducción automática/siguiente) se carga
        // en segundo plano y nunca bloquea ni falla la reproducción — tocar una canción no debe depender
        // de su endpoint de radio.
        resolveAndPlay(song)
        fetchRelatedQueue(song, _uiState.value.queueSession)
    }

    /**
     * Obtiene un álbum por su browse ID e inicia la reproducción.
     *
     * Si se encuentran canciones, reproduce el álbum comenzando desde el índice especificado.
     * Si [shuffle] es verdadero, la cola se barajea antes de la reproducción. Si no se encuentran
     * canciones, invoca [onEmpty].
     *
     * @param browseId El identificador browse del álbum.
     * @param title El título del álbum.
     * @param startIndex La posición de la cola desde la que iniciar la reproducción.
     * @param shuffle Si se debe barajear la cola.
     * @param onEmpty Callback invocado si el álbum no contiene canciones.
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
    fun playPlaylist(songs: List<SongItem>, startIndex: Int = 0, playlistId: String, title: String, shuffle: Boolean = false) =
        playPlaylist(songs.map { it.toMediaMetadata() }, startIndex, playlistId, title, shuffle)

    fun playPlaylist(songs: List<MediaMetadata>, startIndex: Int = 0, playlistId: String, title: String, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        val queue = LocalQueue(QueueSource.Playlist(playlistId, title), songs, startIndex)
        currentQueue = queue
        val uiState = queueManager.buildUiState(queue, shuffle)
        
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
        // Como invitado en Listen Together, el anfitrión controla la reproducción — el botón de
        // play/pause silencia la salida local en su lugar. Las acciones aplicadas remotamente
        // (allowInternalSync) ignoran esto.
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
     * Alterna entre la salida de audio silenciada y activada.
     */
    fun toggleMute() {
        playerService.toggleMute()
    }

    /**
     * Reproduce un endpoint con vista previa instantánea opcional mientras carga la cola.
     *
     * Muestra [previewSong] inmediatamente en el mini-reproductor mientras obtiene la cola
     * completa desde el endpoint. Si la obtención de la cola tiene éxito, la cola obtenida
     * reemplaza la vista previa. Si la obtención falla y se proporcionó [previewSong], la
     * reproducción recurre a reproducir esa canción sola.
     *
     * @param previewSong Una canción opcional para mostrar inmediatamente. Si la carga de la cola falla, esta canción se reproduce como pista individual.
     */
    fun playEndpoint(endpoint: WatchEndpoint, shuffle: Boolean = false, previewSong: MediaMetadata? = null) {
        viewModelScope.launch {
            // Mostrar el mini-reproductor instantáneamente con la canción clicada en lugar de esperar
            // a la construcción de la cola por red (YouTube.next). La cola real la reemplaza cuando llega.
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
                // La obtención de radio/cola falló — aún así reproducir la canción sola en lugar de mostrar error.
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
            // Prevenir múltiples obtenciones en paralelo
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
                        // Restaurar continuation si la obtención falló y queremos reintentar?
                        // Depende de la implementación, se puede restaurar o dejarlo null.
                        if (result?.continuation != null) {
                            _uiState.update { it.copy(queueSession = it.queueSession.copy(continuation = result.continuation)) }
                        }
                    }
                } catch (e: Exception) {
                    log.warning("Error al obtener más canciones: ${e.message}")
                    // restaurar el token para reintentar más tarde
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

        // Usar QueueManager para auto-carga si tenemos una cola
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
        // Respaldo para colas antiguas sin interfaz Queue
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
        // append() agrega la canción al final del orden de reproducción, lo cual es correcto
        // tanto si el barajeo está activado como no (termina al final del orden barajeo actual).
        // No se necesita re-barajear.
        _uiState.update { state -> PlayerQueueCoordinator.append(state, song) }
    }

    fun playNext(song: SongItem) = playNext(song.toMediaMetadata())

    fun playNext(song: MediaMetadata) {
        // insertNext() coloca la canción justo después de la actual en el orden de reproducción,
        // lo cual ya respeta el barajeo (currentIndex es un índice dentro del orden barajeo). El
        // código anterior TAMBIÉN llamaba a rebuildShuffleOrder aquí, lo cual duplicaba la canción
        // y re-barajeaba toda la cola — enviando la pista "reproducir siguiente" a una posición
        // aleatoria/última. Usar insertNext solo.
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

    /** Reintenta la reproducción de la canción actual (para una acción de "reintentar" en un error de reproducción). */
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
     * Reproduce la canción dada, resolviendo su fuente de audio mediante múltiples estrategias
     * de respaldo.
     *
     * Primero busca un archivo en caché. Si no está disponible, resuelve una URL de stream y
     * confirma que la reproducción haya iniciado. Si la reproducción no logra iniciarse, recurre
     * a resolver mediante yt-dlp. Usa seguimiento de ID de solicitud para ignorar resultados de
     * intentos de reproducción obsoletos. Al tener éxito, almacena los metadatos de la canción
     * en caché y registra el evento de reproducción. Actualiza el estado de la interfaz con
     * información de carga y errores según sea necesario.
     */
    private fun resolveAndPlay(song: MediaMetadata) {
        resolveJob?.cancel()
        reconnectWatchJob?.cancel()
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

                // Las canciones sin caché necesitan una resolución en vivo; fallar rápido si estamos
                // sin conexión (o el usuario activó el modo sin conexión) en lugar de agotar toda la
                // cascada de en-proceso + yt-dlp solo para que termine agotando el tiempo de todas formas.
                val offline = cachedFile == null && isEffectivelyOffline()
                if (offline) {
                    if (requestId == playRequestId) handlePlaybackFailure(song)
                    return@launch
                }

                // videostatsPlaybackUrl de la pista resuelta — se usa para registrar la reproducción
                // en la cuenta después (historial/recomendaciones). Solo la ruta en-proceso lo tiene.
                var trackingUrl: String? = null
                val played: Boolean = when {
                    cachedFile != null -> {
                        playerService.play(cachedFile.absolutePath)
                        true
                    }
                    YtDlpResolver.needsYtDlp(song.id) -> {
                        // Video conocido como problemático (todos los clientes en-proceso dan 403 en
                        // esta sesión): saltar directamente a yt-dlp en lugar de repetir el ciclo lento
                        // de resolución + fallo de mpv.
                        playViaYtDlp(song, requestId)
                    }
                    else -> {
                        // La resolución en-proceso puede LANZAR excepciones para videos con restricción
                        // de edad/login (cada cliente sin poToken retorna LOGIN_REQUIRED). No saltar a
                        // la siguiente pista — yt-dlp (con la cookie de la cuenta) puede reproducirlos,
                        // así que tratar una resolución lanzada/vacía como "intentar yt-dlp".
                        val playbackData = try {
                            withContext(Dispatchers.IO) {
                                streamResolver.resolveAudioStream(song.id)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Napier.w("In-process resolve failed for ${song.id} (${e.message}); falling back to yt-dlp")
                            null
                        }
                        val streamUrl = playbackData?.streamUrl
                        trackingUrl = playbackData?.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                        if (requestId != playRequestId) return@launch

                        if (!streamUrl.isNullOrEmpty()) {
                            playerService.play(streamUrl)
                            // El stream resuelto puede pasar la validación HTTP pero dar 403 en mpv
                            // (por ejemplo, URLs IOS con restricción spc). Si la reproducción no
                            // inicia realmente, recurrir a yt-dlp, que maneja los videos difíciles
                            // que nuestro pipeline en-proceso no puede.
                            val started = playerService.awaitPlaybackStarted()
                            if (started || requestId != playRequestId) {
                                true
                            } else {
                                YtDlpResolver.markNeedsYtDlp(song.id)
                                Napier.w("Stream did not start for ${song.id}; trying yt-dlp fallback")
                                playViaYtDlp(song, requestId)
                            }
                        } else {
                            // La resolución lanzó o no retornó nada (login/restricción de edad): último recurso es yt-dlp.
                            YtDlpResolver.markNeedsYtDlp(song.id)
                            Napier.w("No in-process stream for ${song.id}; trying yt-dlp fallback")
                            playViaYtDlp(song, requestId)
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

                // Registrar la reproducción en la cuenta de YouTube del usuario para que cuente
                // en el historial y actualice las recomendaciones (fire-and-forget; solo cuando se
                // está conectado y se conoce el tracking).
                trackingUrl?.takeIf { it.isNotBlank() && YouTube.cookie != null }?.let { url ->
                    viewModelScope.launch(Dispatchers.IO) {
                        runCatching { YouTube.registerPlayback(playbackTracking = url) }
                            .onFailure { Napier.w("registerPlayback failed for ${song.id}: ${it.message}") }
                    }
                }

                // Pre-calentar el caché del stream de la siguiente pista para que saltar a ella sea casi instantáneo.
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
     * Una pista no pudo ser resuelta/reproducida. Si es porque estamos sin conexión, detener y
     * dejar que el usuario reintente una vez que se restablezca la conectividad — la reproducción
     * automática simplemente fallaría de la misma manera en cada pista restante de la cola. De lo
     * contrario, notificar y avanzar automáticamente a la siguiente pista, a menos que muchas
     * pistas consecutivas hayan fallado (probablemente un problema más amplio) — entonces también
     * detenerse.
     */
    private suspend fun handlePlaybackFailure(song: MediaMetadata) {
        consecutiveFailures++
        _uiState.update { it.copy(error = null) }

        val manualOffline = userPreferences.offlineModeEnabled.first()
        if (manualOffline || !NetworkMonitor.isOnline()) {
            consecutiveFailures = 0
            val message = if (manualOffline) "Modo sin conexión activado. Reproducción pausada."
            else "Sin conexión a internet. Reproducción pausada."
            _playbackMessages.tryEmit(message)
            _uiState.update { it.copy(playbackState = PlaybackState.ERROR, error = message) }
            // Solo reanudar automáticamente por una caída real de conectividad — el modo sin
            // conexión manual es una decisión deliberada que el usuario debe desactivar por sí
            // mismo, no algo que anulamos en su nombre.
            if (!manualOffline) watchForReconnect(song)
            return
        }

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
     * Vigila que se restablezca la conectividad y reintenta automáticamente [song] una vez que
     * lo haga — el usuario no debería tener que darse cuenta y presionar reproducir manualmente
     * de nuevo. Se cancela con la siguiente llamada a [resolveAndPlay] (cambio de canción,
     * reintento manual, etc.) para que nunca se ejecute con una canción obsoleta.
     */
    private fun watchForReconnect(song: MediaMetadata) {
        reconnectWatchJob?.cancel()
        reconnectWatchJob = viewModelScope.launch {
            NetworkMonitor.awaitOnline()
            if (!userPreferences.offlineModeEnabled.first() && _uiState.value.currentSong?.id == song.id) {
                retry()
            }
        }
    }

    /**
     * Pre-resuelve la URL del stream de la siguiente pista en segundo plano para que [next] sea
     * casi instantáneo (pre-calienta la misma entrada de StreamCache que usará [resolveAndPlay]).
     * Omite archivos en caché y videos conocidos como exclusivos de yt-dlp (re-resolverlos en
     * proceso es inútil).
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
                // mejor esfuerzo de pre-carga
            }
        }
    }

    /**
     * Resuelve [song] mediante yt-dlp y la reproduce; mantiene el mini-reproductor en LOADING
     * mientras tanto. Retorna verdadero si la reproducción se inició, falso si la URL no pudo
     * resolverse (el llamador maneja el fallo/salto).
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
                // Las canciones que vienen de Listen Together (y algunos resultados de búsqueda) solo
                // llevan el nombre del artista, sin id de YouTube. Sin un id, el mapeo canción↔artista
                // se omitía por completo, por lo que el historial no mostraba artista. Usar un id
                // sintético estable como respaldo para que el nombre persista.
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

    private val _currentMediaInfo = MutableStateFlow<MediaInfo?>(null)
    val currentMediaInfo: StateFlow<MediaInfo?> = _currentMediaInfo.asStateFlow()

    fun fetchMetadataInfo() {
        val song = _uiState.value.currentSong ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _currentMediaInfo.value = null
            try {
                val mediaInfo = YouTube.getMediaInfo(song.id).getOrNull()
                _currentMediaInfo.value = mediaInfo
            } catch (_: Exception) {
                _currentMediaInfo.value = null
            }
        }
    }

    /** Letras sincronizadas por tiempo (BetterLyrics o LRC sincronizado de YouTube). Nulo cuando solo existe texto plano. */
    private val _syncedLyrics = MutableStateFlow<List<LyricLine>?>(null)
    val syncedLyrics: StateFlow<List<LyricLine>?> = _syncedLyrics.asStateFlow()

    // Canción para la que ya se pidieron (o están en curso) las letras. Evita re-fetch al solo
    // cambiar de pestaña (Cola/Info -> Letras) sobre la misma canción.
    private var lastLyricsSongId: String? = null

    fun fetchLyrics() {
        val song = _uiState.value.currentSong ?: return
        if (song.id == lastLyricsSongId) return
        lastLyricsSongId = song.id
        viewModelScope.launch(Dispatchers.IO) {
            _currentLyrics.value = null
            _syncedLyrics.value = null

            val artist = song.artists.joinToString(", ") { it.name }
            val album = song.album?.title

            // Intentar proveedores sincronizados en orden de fiabilidad. El primer LRC utilizable gana.
            //   1) LrcLib  — gratuito, confiable, sincronizado por líneas
            //   2) KuGou   — sincronizado por líneas
            //   3) (Deshabilitado) BetterLyrics — sincronizado por palabras (mejor UX, pero puede requerir autenticación/no estar disponible)
            val lrc = runCatching { LrcLib.getLyrics(song.title, artist, song.duration, album).getOrNull() }.getOrNull()
                ?: runCatching { KuGou.getLyrics(song.title, artist, song.duration, album).getOrNull() }.getOrNull()
                // ?: runCatching { BetterLyrics.getLyrics(song.title, artist, song.duration, album) }.getOrNull()

            if (lrc != null) {
                if (SyncedLyrics.isSynced(lrc)) {
                    val parsed = SyncedLyrics.parse(lrc)
                    if (parsed.isNotEmpty()) {
                        _syncedLyrics.value = parsed
                        _currentLyrics.value = parsed.joinToString("\n") { it.text }
                        return@launch
                    }
                }
                // LRC plano sin marcas de tiempo — mostrar como texto.
                _currentLyrics.value = lrc
                return@launch
            }

            // Recurrir a las letras de YouTube (usualmente texto plano, ocasionalmente LRC).
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
        reconnectWatchJob?.cancel()
        playRequestId += 1
        resolveJob = null
        playerService.stopAudioOnly()
        super.onCleared()
    }
}
