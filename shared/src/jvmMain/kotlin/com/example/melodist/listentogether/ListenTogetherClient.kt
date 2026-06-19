package com.example.melodist.listentogether

import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, ERROR }

enum class RoomRole { HOST, GUEST, NONE }

/** Events emitted by the client for the manager/UI to react to. */
sealed class ListenTogetherEvent {
    data class Connected(val userId: String) : ListenTogetherEvent()
    data object Disconnected : ListenTogetherEvent()
    data class ConnectionError(val error: String) : ListenTogetherEvent()
    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : ListenTogetherEvent()
    data class Reconnected(val roomCode: String, val state: RoomState, val isHost: Boolean) : ListenTogetherEvent()

    data class RoomCreated(val roomCode: String, val userId: String) : ListenTogetherEvent()
    data class JoinRequestReceived(val userId: String, val username: String) : ListenTogetherEvent()
    data class JoinApproved(val roomCode: String, val userId: String, val state: RoomState) : ListenTogetherEvent()
    data class JoinRejected(val reason: String) : ListenTogetherEvent()
    data class UserJoined(val userId: String, val username: String) : ListenTogetherEvent()
    data class UserLeft(val userId: String, val username: String) : ListenTogetherEvent()

    data class PlaybackSync(val action: PlaybackActionPayload) : ListenTogetherEvent()
    data class BufferWait(val trackId: String, val waitingFor: List<String>) : ListenTogetherEvent()
    data class BufferComplete(val trackId: String) : ListenTogetherEvent()
    data class SyncStateReceived(val state: SyncStatePayload) : ListenTogetherEvent()

    data class HostChanged(val newHostId: String, val newHostName: String) : ListenTogetherEvent()
    data class Kicked(val reason: String) : ListenTogetherEvent()
    data class Error(val code: String, val message: String) : ListenTogetherEvent()
    data class UserReconnected(val username: String) : ListenTogetherEvent()
    data class UserDisconnected(val username: String) : ListenTogetherEvent()
}

private sealed class PendingAction {
    data class CreateRoom(val username: String) : PendingAction()
    data class JoinRoom(val roomCode: String, val username: String) : PendingAction()
}

/**
 * WebSocket client for the Listen Together protocol — a ktor/CIO port of Metrolist's
 * `ListenTogetherClient`. Handles connection, room lifecycle and message dispatch; playback
 * bridging lives in [ListenTogetherManager].
 */
class ListenTogetherClient(
    private val serverUrlProvider: () -> String = { ListenTogetherServers.defaultServerUrl },
) {
    companion object {
        private const val TAG = "[LT-Client]"
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val PING_INTERVAL_MS = 25_000L
    }

    private val codec = MessageCodec()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val httpClient = HttpClient(CIO) { install(WebSockets) }

    private var connectionJob: Job? = null
    private var pingJob: Job? = null
    private var outgoingChannel: Channel<ByteArray>? = null
    private var session: DefaultClientWebSocketSession? = null

    private var pendingAction: PendingAction? = null
    private var reconnectAttempts = 0
    private var explicitlyDisconnected = false

    // Persisted-in-memory session info (enough to recover from a network blip within a run).
    private var sessionToken: String? = null
    private var storedRoomCode: String? = null
    private var storedUsername: String? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _roomState = MutableStateFlow<RoomState?>(null)
    val roomState: StateFlow<RoomState?> = _roomState.asStateFlow()

    private val _role = MutableStateFlow(RoomRole.NONE)
    val role: StateFlow<RoomRole> = _role.asStateFlow()

    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId.asStateFlow()

    private val _pendingJoinRequests = MutableStateFlow<List<JoinRequestPayload>>(emptyList())
    val pendingJoinRequests: StateFlow<List<JoinRequestPayload>> = _pendingJoinRequests.asStateFlow()

    private val _events = MutableSharedFlow<ListenTogetherEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ListenTogetherEvent> = _events.asSharedFlow()

    val isInRoom: Boolean get() = _roomState.value != null
    val isHost: Boolean get() = _role.value == RoomRole.HOST

    // ---- Connection ----

    fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTED || _connectionState.value == ConnectionState.CONNECTING) {
            return
        }
        explicitlyDisconnected = false
        _connectionState.value = ConnectionState.CONNECTING
        val url = serverUrlProvider()
        Napier.i("$TAG Connecting to $url")

        connectionJob = scope.launch {
            try {
                httpClient.webSocket(urlString = url) {
                    session = this
                    val out = Channel<ByteArray>(Channel.UNLIMITED)
                    outgoingChannel = out
                    _connectionState.value = ConnectionState.CONNECTED
                    reconnectAttempts = 0
                    startPing()
                    onConnected()

                    val writer = launch {
                        for (bytes in out) {
                            try {
                                send(Frame.Binary(true, bytes))
                            } catch (e: Exception) {
                                Napier.e("$TAG Failed to send frame", e)
                            }
                        }
                    }
                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Binary) handleMessage(frame.readBytes())
                        }
                    } finally {
                        writer.cancel()
                    }
                }
            } catch (e: Exception) {
                Napier.e("$TAG Connection failure", e)
                scope.launch { _events.emit(ListenTogetherEvent.ConnectionError(e.message ?: "unknown")) }
            } finally {
                handleDisconnect()
            }
        }
    }

    private fun onConnected() {
        scope.launch { _events.emit(ListenTogetherEvent.Connected(_userId.value ?: "")) }
        val token = sessionToken
        val room = storedRoomCode
        if (token != null && room != null) {
            Napier.i("$TAG Attempting session reconnect to $room")
            send(MessageTypes.RECONNECT, ReconnectPayload(token))
        } else {
            executePendingAction()
        }
    }

    private fun executePendingAction() {
        val action = pendingAction ?: return
        pendingAction = null
        when (action) {
            is PendingAction.CreateRoom -> send(MessageTypes.CREATE_ROOM, CreateRoomPayload(action.username))
            is PendingAction.JoinRoom ->
                send(MessageTypes.JOIN_ROOM, JoinRoomPayload(action.roomCode.uppercase(), action.username))
        }
    }

    private fun handleDisconnect() {
        stopPing()
        outgoingChannel?.close()
        outgoingChannel = null
        session = null
        if (explicitlyDisconnected) {
            _connectionState.value = ConnectionState.DISCONNECTED
            return
        }
        // Auto-reconnect if we still have a session to recover.
        if (sessionToken != null && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++
            _connectionState.value = ConnectionState.RECONNECTING
            scope.launch {
                _events.emit(ListenTogetherEvent.Reconnecting(reconnectAttempts, MAX_RECONNECT_ATTEMPTS))
                delay(1000L * reconnectAttempts)
                if (!explicitlyDisconnected) connect()
            }
        } else {
            _connectionState.value = ConnectionState.DISCONNECTED
            scope.launch { _events.emit(ListenTogetherEvent.Disconnected) }
        }
    }

    fun disconnect() {
        explicitlyDisconnected = true
        stopPing()
        scope.launch { runCatching { session?.close() } }
        connectionJob?.cancel()
        outgoingChannel?.close()
        outgoingChannel = null
        session = null
        sessionToken = null
        storedRoomCode = null
        storedUsername = null
        pendingAction = null
        _roomState.value = null
        _role.value = RoomRole.NONE
        _pendingJoinRequests.value = emptyList()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun startPing() {
        stopPing()
        pingJob = scope.launch {
            while (isActive) {
                delay(PING_INTERVAL_MS)
                send<Unit>(MessageTypes.PING, null)
            }
        }
    }

    private fun stopPing() {
        pingJob?.cancel()
        pingJob = null
    }

    // ---- Sending ----

    private inline fun <reified T> send(type: String, payload: T?) {
        try {
            val bytes = codec.encode(type, payload)
            val result = outgoingChannel?.trySend(bytes)
            if (result?.isSuccess != true) Napier.w("$TAG Could not enqueue $type (not connected?)")
        } catch (e: Exception) {
            Napier.e("$TAG Error encoding $type", e)
        }
    }

    // ---- Public room API ----

    fun createRoom(username: String) {
        sessionToken = null
        storedRoomCode = null
        storedUsername = username
        if (_connectionState.value == ConnectionState.CONNECTED) {
            send(MessageTypes.CREATE_ROOM, CreateRoomPayload(username))
        } else {
            pendingAction = PendingAction.CreateRoom(username)
            connect()
        }
    }

    fun joinRoom(roomCode: String, username: String) {
        sessionToken = null
        storedUsername = username
        if (_connectionState.value == ConnectionState.CONNECTED) {
            send(MessageTypes.JOIN_ROOM, JoinRoomPayload(roomCode.uppercase(), username))
        } else {
            pendingAction = PendingAction.JoinRoom(roomCode, username)
            connect()
        }
    }

    fun leaveRoom() {
        send<Unit>(MessageTypes.LEAVE_ROOM, null)
        disconnect()
    }

    fun approveJoin(userId: String) {
        send(MessageTypes.APPROVE_JOIN, ApproveJoinPayload(userId))
        _pendingJoinRequests.value = _pendingJoinRequests.value.filterNot { it.userId == userId }
    }

    fun rejectJoin(userId: String, reason: String? = null) {
        send(MessageTypes.REJECT_JOIN, RejectJoinPayload(userId, reason ?: ""))
        _pendingJoinRequests.value = _pendingJoinRequests.value.filterNot { it.userId == userId }
    }

    fun kickUser(userId: String, reason: String? = null) {
        send(MessageTypes.KICK_USER, KickUserPayload(userId, reason ?: ""))
    }

    fun transferHost(newHostId: String) {
        send(MessageTypes.TRANSFER_HOST, TransferHostPayload(newHostId))
    }

    fun requestSync() {
        send<Unit>(MessageTypes.REQUEST_SYNC, null)
    }

    fun sendBufferReady(trackId: String) {
        send(MessageTypes.BUFFER_READY, BufferReadyPayload(trackId))
    }

    fun sendPlaybackAction(
        action: String,
        trackId: String? = null,
        position: Long? = null,
        trackInfo: TrackInfo? = null,
        queue: List<TrackInfo>? = null,
        queueTitle: String? = null,
        insertNext: Boolean? = null,
        volume: Float? = null,
    ) {
        send(
            MessageTypes.PLAYBACK_ACTION,
            PlaybackActionPayload(
                action = action,
                trackId = trackId ?: "",
                position = position ?: 0L,
                trackInfo = trackInfo,
                insertNext = insertNext ?: false,
                queue = queue ?: emptyList(),
                queueTitle = queueTitle ?: "",
                volume = volume ?: 1f,
            ),
        )
    }

    // ---- Receiving ----

    private fun handleMessage(data: ByteArray) {
        try {
            val (type, payloadBytes) = codec.decode(data)
            when (type) {
                MessageTypes.PONG -> Unit
                MessageTypes.ROOM_CREATED -> {
                    val p = codec.decodePayload(type, payloadBytes) as? RoomCreatedPayload ?: return
                    sessionToken = p.sessionToken
                    storedRoomCode = p.roomCode
                    _userId.value = p.userId
                    _role.value = RoomRole.HOST
                    _roomState.value = RoomState(
                        roomCode = p.roomCode,
                        hostId = p.userId,
                        users = listOf(UserInfo(p.userId, storedUsername ?: "Host", isHost = true)),
                        isPlaying = false,
                        position = 0L,
                        lastUpdate = System.currentTimeMillis(),
                    )
                    emit(ListenTogetherEvent.RoomCreated(p.roomCode, p.userId))
                }
                MessageTypes.JOIN_REQUEST -> {
                    val p = codec.decodePayload(type, payloadBytes) as? JoinRequestPayload ?: return
                    _pendingJoinRequests.value = _pendingJoinRequests.value + p
                    emit(ListenTogetherEvent.JoinRequestReceived(p.userId, p.username))
                }
                MessageTypes.JOIN_APPROVED -> {
                    val p = codec.decodePayload(type, payloadBytes) as? JoinApprovedPayload ?: return
                    sessionToken = p.sessionToken
                    storedRoomCode = p.roomCode
                    _userId.value = p.userId
                    _role.value = RoomRole.GUEST
                    _roomState.value = p.state
                    emit(ListenTogetherEvent.JoinApproved(p.roomCode, p.userId, p.state))
                }
                MessageTypes.JOIN_REJECTED -> {
                    val p = codec.decodePayload(type, payloadBytes) as? JoinRejectedPayload ?: return
                    emit(ListenTogetherEvent.JoinRejected(p.reason))
                }
                MessageTypes.USER_JOINED -> {
                    val p = codec.decodePayload(type, payloadBytes) as? UserJoinedPayload ?: return
                    _roomState.value = _roomState.value?.let { rs ->
                        if (rs.users.any { it.userId == p.userId }) rs
                        else rs.copy(users = rs.users + UserInfo(p.userId, p.username, isHost = false))
                    }
                    emit(ListenTogetherEvent.UserJoined(p.userId, p.username))
                }
                MessageTypes.USER_LEFT -> {
                    val p = codec.decodePayload(type, payloadBytes) as? UserLeftPayload ?: return
                    _roomState.value = _roomState.value?.let { rs ->
                        rs.copy(users = rs.users.filterNot { it.userId == p.userId })
                    }
                    emit(ListenTogetherEvent.UserLeft(p.userId, p.username))
                }
                MessageTypes.SYNC_PLAYBACK -> {
                    val p = codec.decodePayload(type, payloadBytes) as? PlaybackActionPayload ?: return
                    emit(ListenTogetherEvent.PlaybackSync(p))
                }
                MessageTypes.BUFFER_WAIT -> {
                    val p = codec.decodePayload(type, payloadBytes) as? BufferWaitPayload ?: return
                    emit(ListenTogetherEvent.BufferWait(p.trackId, p.waitingFor))
                }
                MessageTypes.BUFFER_COMPLETE -> {
                    val p = codec.decodePayload(type, payloadBytes) as? BufferCompletePayload ?: return
                    emit(ListenTogetherEvent.BufferComplete(p.trackId))
                }
                MessageTypes.SYNC_STATE -> {
                    val p = codec.decodePayload(type, payloadBytes) as? SyncStatePayload ?: return
                    emit(ListenTogetherEvent.SyncStateReceived(p))
                }
                MessageTypes.HOST_CHANGED -> {
                    val p = codec.decodePayload(type, payloadBytes) as? HostChangedPayload ?: return
                    _role.value = if (p.newHostId == _userId.value) RoomRole.HOST else RoomRole.GUEST
                    _roomState.value = _roomState.value?.copy(
                        hostId = p.newHostId,
                        users = _roomState.value!!.users.map { it.copy(isHost = it.userId == p.newHostId) },
                    )
                    emit(ListenTogetherEvent.HostChanged(p.newHostId, p.newHostName))
                }
                MessageTypes.KICKED -> {
                    val p = codec.decodePayload(type, payloadBytes) as? KickedPayload ?: return
                    emit(ListenTogetherEvent.Kicked(p.reason))
                    disconnect()
                }
                MessageTypes.ERROR -> {
                    val p = codec.decodePayload(type, payloadBytes) as? ErrorPayload ?: return
                    emit(ListenTogetherEvent.Error(p.code, p.message))
                }
                MessageTypes.RECONNECTED -> {
                    val p = codec.decodePayload(type, payloadBytes) as? ReconnectedPayload ?: return
                    storedRoomCode = p.roomCode
                    _userId.value = p.userId
                    _role.value = if (p.isHost) RoomRole.HOST else RoomRole.GUEST
                    _roomState.value = p.state
                    emit(ListenTogetherEvent.Reconnected(p.roomCode, p.state, p.isHost))
                }
                MessageTypes.USER_RECONNECTED -> {
                    val p = codec.decodePayload(type, payloadBytes) as? UserReconnectedPayload ?: return
                    emit(ListenTogetherEvent.UserReconnected(p.username))
                }
                MessageTypes.USER_DISCONNECTED -> {
                    val p = codec.decodePayload(type, payloadBytes) as? UserDisconnectedPayload ?: return
                    emit(ListenTogetherEvent.UserDisconnected(p.username))
                }
                else -> Napier.w("$TAG Unknown message type: $type")
            }
        } catch (e: Exception) {
            Napier.e("$TAG Error parsing message", e)
        }
    }

    private fun emit(event: ListenTogetherEvent) {
        scope.launch { _events.emit(event) }
    }
}
