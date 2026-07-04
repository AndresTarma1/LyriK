package com.example.melodist.listentogether

import com.example.melodist.models.MediaMetadata
import com.example.melodist.player.PlaybackState
import com.example.melodist.viewmodels.PlayerViewModel
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
 * Bridges [ListenTogetherClient] with Melodist's [PlayerViewModel].
 *
 * HOST: observes player state (track / play-pause / seek) and broadcasts actions.
 * GUEST: applies incoming playback actions to the local player.
 *
 * Adapted from Metrolist's `ListenTogetherManager` (which hooks ExoPlayer's `Player.Listener`);
 * here we observe Melodist's StateFlows instead, since mpv playback is event/flow-driven.
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

    // Expose client state for the UI.
    val connectionState = client.connectionState
    val roomState = client.roomState
    val role = client.role
    val userId = client.userId
    val pendingJoinRequests = client.pendingJoinRequests
    val events = client.events

    val isInRoom: Boolean get() = client.isInRoom
    val isHost: Boolean get() = client.isHost

    /** Collect client events once at app start. */
    fun initialize() {
        scope.launch {
            client.events.collect { event -> runCatching { handleEvent(event) }.onFailure { Napier.e("$TAG handleEvent", it) } }
        }
        scope.launch {
            client.role.collect { refreshObservation() }
        }
    }

    /** Provide (or clear) the player. Called when PlayerViewModel becomes available. */
    fun setPlayer(playerViewModel: PlayerViewModel?) {
        player = playerViewModel
        refreshObservation()
    }

    // ---- Room API passthrough (used by UI) ----
    fun createRoom(username: String) = client.createRoom(username)
    fun joinRoom(code: String, username: String) = client.joinRoom(code, username)
    fun leaveRoom() = client.leaveRoom()
    fun approveJoin(userId: String) = client.approveJoin(userId)
    fun rejectJoin(userId: String) = client.rejectJoin(userId)
    fun kickUser(userId: String) = client.kickUser(userId)
    fun transferHost(userId: String) = client.transferHost(userId)

    // ---- Host observation ----

    private fun refreshObservation() {
        val pvm = player
        // Guests can't control shared playback; their play/pause becomes mute/unmute.
        pvm?.listenTogetherGuestMode = isInRoom && !isHost
        if (pvm != null && isInRoom && isHost) startHostObservation(pvm) else stopHostObservation()
    }

    private fun startHostObservation(pvm: PlayerViewModel) {
        if (hostObserverJob?.isActive == true) return
        Napier.i("$TAG Start host observation")
        lastSentTrackId = pvm.uiState.value.currentSong?.id
        lastSentPlaying = pvm.uiState.value.playbackState == PlaybackState.PLAYING

        hostObserverJob = scope.launch {
            // Track changes
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
            // Play / pause
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
            // Seeks
            launch {
                pvm.seekEvents.collect { positionMs ->
                    if (!isHost || isApplyingRemote) return@collect
                    val trackId = pvm.uiState.value.currentSong?.id
                    client.sendPlaybackAction(PlaybackActions.SEEK, trackId = trackId, position = positionMs)
                }
            }
            // Queue contents (additions/removals). Skipping within the same queue doesn't change
            // the id list, so this only fires on real queue edits.
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

    // ---- Event handling ----

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
                // Send current track to the newcomer.
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

    /** Guest: load the full queue (so skip/next works) positioned on [currentTrack]. */
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
        // The stream resolves asynchronously; seek (and optionally pause) once it's playing.
        scope.launch {
            delay(1200)
            if (position > POSITION_TOLERANCE_MS) withRemote { pvm.seekTo(position) }
            if (!autoplay && pvm.uiState.value.playbackState == PlaybackState.PLAYING) {
                withRemote { pvm.togglePlayPause() }
            }
        }
    }

    /** Guest: reconcile queue edits without restarting playback (append-only for MVP). */
    private fun applyQueueSync(queue: List<TrackInfo>) {
        if (isHost) return
        val pvm = player ?: return
        if (queue.isEmpty()) return
        val current = pvm.uiState.value.queue.map { it.id }.toSet()
        if (current.isEmpty()) return // not loaded yet — CHANGE_TRACK will populate it
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

    /** Run [block] with the remote-applying guard set so host observers don't echo it. */
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
