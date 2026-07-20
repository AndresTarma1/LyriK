package com.example.musicApp.listentogether

import com.example.musicApp.models.MediaMetadata
import com.example.musicApp.player.PlaybackState
import com.example.musicApp.viewmodels.PlayerViewModel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Conecta [ListenTogetherClient] con el [PlayerViewModel] de Melodist.
 *
 * ANFITRIÓN: observa el estado del reproductor (pista / pausa-reanudar / seek) y transmite acciones.
 * INVITADO: aplica las acciones de reproducción entrantes al reproductor local.
 *
 * Adaptado del `ListenTogetherManager` de Metrolist (que se conecta al `Player.Listener` de ExoPlayer);
 * aquí observamos los StateFlows de Melodist en su lugar, ya que la reproducción con mpv es impulsada
 * por eventos/flows.
 */
class ListenTogetherManager(
    private val client: ListenTogetherClient,
) {
    companion object {
        private const val TAG = "[LT-Manager]"
        private const val POSITION_TOLERANCE_MS = 2_000L
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var player: PlayerViewModel? = null
    private var hostObserverJob: Job? = null

    private var lastSentTrackId: String? = null
    private var lastSentPlaying: Boolean? = null

    @Volatile
    private var isApplyingRemote = false

    // Exponer el estado del cliente para la UI.
    val connectionState = client.connectionState
    val roomState = client.roomState
    val role = client.role
    val userId = client.userId
    val pendingJoinRequests = client.pendingJoinRequests
    val events = client.events

    val isInRoom: Boolean get() = client.isInRoom
    val isHost: Boolean get() = client.isHost

    /** Recopilar eventos del cliente una vez al inicio de la app. */
    fun initialize() {
        scope.launch {
            client.events.collect { event -> runCatching { handleEvent(event) }.onFailure { Napier.e("$TAG handleEvent", it) } }
        }
        scope.launch {
            client.role.collect { refreshObservation() }
        }
    }

    /** Proporcionar (o limpiar) el reproductor. Se llama cuando PlayerViewModel está disponible. */
    fun setPlayer(playerViewModel: PlayerViewModel?) {
        player = playerViewModel
        refreshObservation()
    }

    // ---- Passthrough de la API de sala (usado por la UI) ----
    fun createRoom(username: String) = client.createRoom(username)
    fun joinRoom(code: String, username: String) = client.joinRoom(code, username)
    fun leaveRoom() = client.leaveRoom()
    fun approveJoin(userId: String) = client.approveJoin(userId)
    fun rejectJoin(userId: String) = client.rejectJoin(userId)
    fun kickUser(userId: String) = client.kickUser(userId)
    fun transferHost(userId: String) = client.transferHost(userId)

    // ---- Observación del anfitrión ----

    private fun refreshObservation() {
        val pvm = player
        // Los invitados no pueden controlar la reproducción compartida; su pausa/reanudar se convierte en silenciar/activar sonido.
        pvm?.listenTogetherGuestMode = isInRoom && !isHost
        if (pvm != null && isInRoom && isHost) startHostObservation(pvm) else stopHostObservation()
    }

    private fun startHostObservation(pvm: PlayerViewModel) {
        if (hostObserverJob?.isActive == true) return
        Napier.i("$TAG Start host observation")
        lastSentTrackId = pvm.uiState.value.currentSong?.id
        lastSentPlaying = pvm.uiState.value.playbackState == PlaybackState.PLAYING

        hostObserverJob = scope.launch {
            // Cambios de pista
            launch {
                pvm.uiState.map { it.currentSong?.id }.distinctUntilChanged().collect { trackId ->
                    if (!isHost || isApplyingRemote) return@collect
                    if (trackId != null && trackId != lastSentTrackId) {
                        val state = pvm.uiState.value
                        val song = state.currentSong ?: return@collect
                        lastSentTrackId = trackId
                        lastSentPlaying = false
                        Napier.i("$TAG Host track change -> ${song.title}")
                        client.sendPlaybackAction(
                            action = PlaybackActions.CHANGE_TRACK,
                            trackId = trackId,
                            trackInfo = song.toTrackInfo(),
                            queue = state.queue.map { it.toTrackInfo() },
                            queueTitle = "Listen Together",
                        )
                    }
                }
            }
            // Reproducir / pausar
            launch {
                pvm.uiState.map { it.playbackState }.distinctUntilChanged().collect { pbState ->
                    if (!isHost || isApplyingRemote) return@collect
                    val position = pvm.progressState.value.positionMs
                    val trackId = pvm.uiState.value.currentSong?.id
                    when (pbState) {
                        PlaybackState.PLAYING -> if (lastSentPlaying != true) {
                            lastSentPlaying = true
                            client.sendPlaybackAction(PlaybackActions.PLAY, trackId = trackId, position = position)
                        }
                        PlaybackState.PAUSED -> if (lastSentPlaying == true) {
                            lastSentPlaying = false
                            client.sendPlaybackAction(PlaybackActions.PAUSE, trackId = trackId, position = position)
                        }
                        else -> Unit
                    }
                }
            }
            // Búsquedas de posición (seek)
            launch {
                pvm.seekEvents.collect { positionMs ->
                    if (!isHost || isApplyingRemote) return@collect
                    val trackId = pvm.uiState.value.currentSong?.id
                    client.sendPlaybackAction(PlaybackActions.SEEK, trackId = trackId, position = positionMs)
                }
            }
            // Contenido de la cola (agregados/eliminados). Saltar dentro de la misma cola no cambia
            // la lista de ids, así que esto solo se activa en ediciones reales de la cola.
            launch {
                pvm.uiState.map { state -> state.queue.map { it.id } }.distinctUntilChanged().collect { ids ->
                    if (!isHost || isApplyingRemote || ids.isEmpty()) return@collect
                    client.sendPlaybackAction(
                        action = PlaybackActions.SYNC_QUEUE,
                        queue = pvm.uiState.value.queue.map { it.toTrackInfo() },
                        queueTitle = "Listen Together",
                    )
                }
            }
        }
    }

    private fun stopHostObservation() {
        hostObserverJob?.cancel()
        hostObserverJob = null
    }

    // ---- Manejo de eventos ----

    private fun handleEvent(event: ListenTogetherEvent) {
        when (event) {
            is ListenTogetherEvent.RoomCreated -> refreshObservation()
            is ListenTogetherEvent.JoinApproved -> applyFullState(
                event.state.currentTrack, event.state.isPlaying, event.state.position, event.state.queue,
            )
            is ListenTogetherEvent.Reconnected -> if (!event.isHost) {
                applyFullState(event.state.currentTrack, event.state.isPlaying, event.state.position, event.state.queue)
            }
            is ListenTogetherEvent.HostChanged -> refreshObservation()
            is ListenTogetherEvent.SyncStateReceived -> if (!isHost) {
                applyFullState(event.state.currentTrack, event.state.isPlaying, event.state.position, event.state.queue)
            }
            is ListenTogetherEvent.UserJoined -> if (isHost) {
                // Enviar la pista actual al nuevo usuario.
                val pvm = player ?: return
                val state = pvm.uiState.value
                val song = state.currentSong ?: return
                client.sendPlaybackAction(
                    action = PlaybackActions.CHANGE_TRACK,
                    trackId = song.id,
                    trackInfo = song.toTrackInfo(),
                    queue = state.queue.map { it.toTrackInfo() },
                    queueTitle = "Listen Together",
                )
                if (state.playbackState == PlaybackState.PLAYING) {
                    client.sendPlaybackAction(PlaybackActions.PLAY, trackId = song.id, position = pvm.progressState.value.positionMs)
                }
            }
            is ListenTogetherEvent.PlaybackSync -> if (!isHost) handlePlaybackSync(event.action)
            is ListenTogetherEvent.Kicked -> stopHostObservation()
            else -> Unit
        }
    }

    private fun handlePlaybackSync(action: PlaybackActionPayload) {
        val pvm = player ?: return
        when (action.action) {
            PlaybackActions.CHANGE_TRACK -> action.trackInfo?.let { loadTrack(it, action.queue, action.position, autoplay = true) }
            PlaybackActions.PLAY -> applyPlay(action.position)
            PlaybackActions.PAUSE -> applyPause(action.position)
            PlaybackActions.SEEK -> applySeek(action.position)
            PlaybackActions.SKIP_NEXT -> withRemote { pvm.next() }
            PlaybackActions.SKIP_PREV -> withRemote { pvm.previous() }
            PlaybackActions.SYNC_QUEUE -> applyQueueSync(action.queue)
            else -> Unit
        }
    }

    private fun applyFullState(track: TrackInfo?, isPlaying: Boolean, position: Long, queue: List<TrackInfo>) {
        if (isHost) return
        track ?: return
        loadTrack(track, queue, position, autoplay = isPlaying)
    }

    /** Invitado: cargar la cola completa (para que skip/next funcione) posicionado en [currentTrack]. */
    private fun loadTrack(currentTrack: TrackInfo, queue: List<TrackInfo>, position: Long, autoplay: Boolean) {
        val pvm = player ?: return
        Napier.i("$TAG Guest load ${currentTrack.title} @ $position (queue=${queue.size})")
        if (queue.isNotEmpty()) {
            val items = queue.map { it.toMediaMetadata() }
            val index = items.indexOfFirst { it.id == currentTrack.id }.coerceAtLeast(0)
            withRemote { pvm.playCustom(items, index) }
        } else {
            withRemote { pvm.playSingle(currentTrack.toMediaMetadata()) }
        }
        // El stream se resuelve de forma asíncrona; hacer seek (y pausar opcionalmente) una vez que esté reproduciendo.
        scope.launch {
            delay(1200)
            if (position > POSITION_TOLERANCE_MS) withRemote { pvm.seekTo(position) }
            if (!autoplay && pvm.uiState.value.playbackState == PlaybackState.PLAYING) {
                withRemote { pvm.togglePlayPause() }
            }
        }
    }

    /** Invitado: reconciliar ediciones de la cola sin reiniciar la reproducción (solo agregar para MVP). */
    private fun applyQueueSync(queue: List<TrackInfo>) {
        if (isHost) return
        val pvm = player ?: return
        if (queue.isEmpty()) return
        val current = pvm.uiState.value.queue.map { it.id }.toSet()
        if (current.isEmpty()) return // aún no se cargó — CHANGE_TRACK lo llenará
        val newTracks = queue.filter { it.id !in current }
        if (newTracks.isEmpty()) return
        Napier.i("$TAG Guest queue sync: appending ${newTracks.size} track(s)")
        newTracks.forEach { t -> withRemote { pvm.addToQueue(t.toMediaMetadata()) } }
    }

    private fun applyPlay(position: Long) {
        val pvm = player ?: return
        val cur = pvm.progressState.value.positionMs
        if (kotlin.math.abs(cur - position) > POSITION_TOLERANCE_MS) withRemote { pvm.seekTo(position) }
        if (pvm.uiState.value.playbackState != PlaybackState.PLAYING) withRemote { pvm.togglePlayPause() }
    }

    private fun applyPause(position: Long) {
        val pvm = player ?: return
        if (pvm.uiState.value.playbackState == PlaybackState.PLAYING) withRemote { pvm.togglePlayPause() }
        val cur = pvm.progressState.value.positionMs
        if (kotlin.math.abs(cur - position) > POSITION_TOLERANCE_MS) withRemote { pvm.seekTo(position) }
    }

    private fun applySeek(position: Long) {
        val pvm = player ?: return
        if (kotlin.math.abs(pvm.progressState.value.positionMs - position) > POSITION_TOLERANCE_MS) {
            withRemote { pvm.seekTo(position) }
        }
    }

    /** Ejecutar [block] con la bandera de aplicación remota activada para que los observadores del anfitrión no lo repliquen. */
    private inline fun withRemote(block: () -> Unit) {
        val pvm = player
        isApplyingRemote = true
        pvm?.allowInternalSync = true
        try {
            block()
        } finally {
            isApplyingRemote = false
            pvm?.allowInternalSync = false
        }
    }
}

private fun MediaMetadata.toTrackInfo(): TrackInfo = TrackInfo(
    id = id,
    title = title,
    artist = artists.joinToString(", ") { it.name },
    album = album?.title ?: "",
    duration = duration * 1000L,
    thumbnail = thumbnailUrl ?: "",
)

private fun TrackInfo.toMediaMetadata(): MediaMetadata = MediaMetadata(
    id = id,
    title = title,
    artists = listOf(MediaMetadata.Artist(id = null, name = artist)),
    duration = (duration / 1000L).toInt(),
    thumbnailUrl = thumbnail.ifEmpty { null },
    album = album.ifEmpty { null }?.let { MediaMetadata.Album(id = "", title = it) },
)
