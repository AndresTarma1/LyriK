package com.example.melodist.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.melodist.listentogether.ConnectionState
import com.example.melodist.listentogether.ListenTogetherEvent
import com.example.melodist.listentogether.ListenTogetherManager
import com.example.melodist.listentogether.RoomRole
import com.example.melodist.utils.LocalSnackbarHostState
import com.example.melodist.utils.LocalSnackbarScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Listen Together screen — create or join a synced listening room.
 * MVP UI: connection state, create/join, room code, member list and host approvals.
 */
@Composable
fun ListenTogetherScreen() {
    val manager: ListenTogetherManager = koinInject()
    val snackbar = LocalSnackbarHostState.current
    val scope = LocalSnackbarScope.current

    val connectionState by manager.connectionState.collectAsState()
    val roomState by manager.roomState.collectAsState()
    val role by manager.role.collectAsState()
    val myUserId by manager.userId.collectAsState()
    val pendingRequests by manager.pendingJoinRequests.collectAsState()

    var username by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }

    // Surface relevant events as snackbars.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        manager.events.collect { event ->
            when (event) {
                is ListenTogetherEvent.JoinRejected -> snackbar.showSnackbar("Solicitud rechazada: ${event.reason}")
                is ListenTogetherEvent.Kicked -> snackbar.showSnackbar("Te expulsaron de la sala: ${event.reason}")
                is ListenTogetherEvent.Error -> snackbar.showSnackbar("Error: ${event.message}")
                is ListenTogetherEvent.ConnectionError -> snackbar.showSnackbar("Error de conexión: ${event.error}")
                else -> Unit
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Groups, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Escuchar juntos",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Reproduce en sincronía con tus amigos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ConnectionBadge(connectionState)

            val room = roomState
            if (room == null) {
                LobbyContent(
                    username = username,
                    onUsernameChange = { username = it },
                    joinCode = joinCode,
                    onJoinCodeChange = { joinCode = it.uppercase() },
                    busy = connectionState == ConnectionState.CONNECTING,
                    onCreate = { manager.createRoom(username.ifBlank { "Anfitrión" }) },
                    onJoin = { manager.joinRoom(joinCode.trim(), username.ifBlank { "Invitado" }) },
                )
            } else {
                RoomContent(
                    roomCode = room.roomCode,
                    isHost = role == RoomRole.HOST,
                    members = room.users.map { Triple(it.username, it.isHost, it.userId == myUserId) },
                    pending = pendingRequests.map { it.userId to it.username },
                    onApprove = { manager.approveJoin(it) },
                    onReject = { manager.rejectJoin(it) },
                    onCopyCode = { scope.launch { snackbar.showSnackbar("Código copiado: ${room.roomCode}") } },
                    onLeave = { manager.leaveRoom() },
                )
            }
        }
    }
}

@Composable
private fun ConnectionBadge(state: ConnectionState) {
    val (label, color) = when (state) {
        ConnectionState.CONNECTED -> "Conectado" to MaterialTheme.colorScheme.primary
        ConnectionState.CONNECTING -> "Conectando…" to MaterialTheme.colorScheme.tertiary
        ConnectionState.RECONNECTING -> "Reconectando…" to MaterialTheme.colorScheme.tertiary
        ConnectionState.ERROR -> "Error de conexión" to MaterialTheme.colorScheme.error
        ConnectionState.DISCONNECTED -> "Desconectado" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(8.dp)) {
                Surface(shape = RoundedCornerShape(4.dp), color = color, modifier = Modifier.fillMaxSize()) {}
            }
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = color)
        }
    }
}

@Composable
private fun LobbyContent(
    username: String,
    onUsernameChange: (String) -> Unit,
    joinCode: String,
    onJoinCodeChange: (String) -> Unit,
    busy: Boolean,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
) {
    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        label = { Text("Tu nombre") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Crear una sala", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Serás el anfitrión: tu reproducción controla la de todos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onCreate, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Crear sala")
                }
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Unirse a una sala", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = joinCode,
                onValueChange = onJoinCodeChange,
                label = { Text("Código de sala") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = onJoin,
                enabled = !busy && joinCode.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Unirse")
            }
        }
    }
}

@Composable
private fun RoomContent(
    roomCode: String,
    isHost: Boolean,
    members: List<Triple<String, Boolean, Boolean>>, // username, isHost, isMe
    pending: List<Pair<String, String>>, // userId, username
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onCopyCode: () -> Unit,
    onLeave: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (isHost) "Eres el anfitrión" else "Estás escuchando",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    roomCode,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onCopyCode) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar código", modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    if (pending.isNotEmpty()) {
        Text("Solicitudes para unirse", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        pending.forEach { (userId, name) ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { onApprove(userId) }) {
                        Icon(Icons.Filled.Check, "Aprobar", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { onReject(userId) }) {
                        Icon(Icons.Filled.Close, "Rechazar", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    Text(
        "Miembros (${members.size})",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    LazyColumn(
        modifier = Modifier.fillMaxWidth().height((members.size.coerceAtMost(6) * 52).dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(members) { (name, host, isMe) ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (host) {
                        Icon(Icons.Filled.Star, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        if (isMe) "$name (tú)" else name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(4.dp))
    Button(
        onClick = onLeave,
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Salir de la sala")
    }
}
