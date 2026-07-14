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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import com.example.melodist.listentogether.ConnectionState
import com.example.melodist.listentogether.ListenTogetherEvent
import com.example.melodist.listentogether.ListenTogetherManager
import com.example.melodist.listentogether.RoomRole
import com.example.melodist.utils.LocalSnackbarHostState
import com.example.melodist.utils.LocalSnackbarScope
import com.example.melodist.utils.LocalUserPreferences
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import lyrik.composeapp.generated.resources.Res
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Elemento de miembro estable para uso de clave en LazyColumn. */
private data class MemberItem(
    val userId: String,
    val username: String,
    val isHost: Boolean,
    val isMe: Boolean,
)

/**
 * Pantalla de Escuchar Juntos — crear o unirse a una sala de escucha sincronizada.
 * MVP UI: estado de conexión, crear/unirse, código de sala, lista de miembros y aprobaciones del anfitrión.
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

    val prefs = LocalUserPreferences.current
    val savedName by prefs.listenTogetherUsername.collectAsState("")
    var username by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }
    // Autocompletar el nombre de usuario recordado una vez que se cargue.
    LaunchedEffect(savedName) { if (username.isBlank() && savedName.isNotBlank()) username = savedName }
    val defaultHost = stringResource(Res.string.lt_default_host)
    val defaultGuest = stringResource(Res.string.lt_default_guest)
    val fmtRequestRejected = stringResource(Res.string.lt_request_rejected)
    val fmtKicked = stringResource(Res.string.lt_kicked)
    val fmtError = stringResource(Res.string.lt_error_generic)
    val fmtConnectionError = stringResource(Res.string.lt_connection_error)

    // Mostrar eventos relevantes como snackbar.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        manager.events.collect { event ->
            when (event) {
                is ListenTogetherEvent.JoinRejected -> snackbar.showSnackbar(fmtRequestRejected.format(event.reason))
                is ListenTogetherEvent.Kicked -> snackbar.showSnackbar(fmtKicked.format(event.reason))
                is ListenTogetherEvent.Error -> snackbar.showSnackbar(fmtError.format(event.message))
                is ListenTogetherEvent.ConnectionError -> snackbar.showSnackbar(fmtConnectionError.format(event.error))
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
            // Encabezado
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Groups, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        stringResource(Res.string.lt_title),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(Res.string.lt_subtitle),
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
                    onCreate = {
                        val name = username.ifBlank { defaultHost }
                        scope.launch { prefs.setListenTogetherUsername(name.trim()) }
                        manager.createRoom(name)
                    },
                    onJoin = {
                        val name = username.ifBlank { defaultGuest }
                        scope.launch { prefs.setListenTogetherUsername(name.trim()) }
                        manager.joinRoom(joinCode.trim(), name)
                    },
                )
            } else {
                val codeCopiedMsg = stringResource(Res.string.lt_code_copied, room.roomCode)
                RoomContent(
                    roomCode = room.roomCode,
                    isHost = role == RoomRole.HOST,
                    members = room.users.map { MemberItem(it.userId, it.username, it.isHost, it.userId == myUserId) },
                    pending = pendingRequests.map { it.userId to it.username },
                    onApprove = { manager.approveJoin(it) },
                    onReject = { manager.rejectJoin(it) },
                    onTransferHost = { manager.transferHost(it) },
                    onKick = { manager.kickUser(it) },
                    onCopyCode = {
                        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                        clipboard.setContents(StringSelection(room.roomCode), null)
                        scope.launch { snackbar.showSnackbar(codeCopiedMsg) }
                    },
                    onLeave = { manager.leaveRoom() },
                )
            }
        }
    }
}

@Composable
private fun ConnectionBadge(state: ConnectionState) {
    val (label, color) = when (state) {
        ConnectionState.CONNECTED -> stringResource(Res.string.lt_connected) to MaterialTheme.colorScheme.primary
        ConnectionState.CONNECTING -> stringResource(Res.string.lt_connecting) to MaterialTheme.colorScheme.tertiary
        ConnectionState.RECONNECTING -> stringResource(Res.string.lt_reconnecting) to MaterialTheme.colorScheme.tertiary
        ConnectionState.ERROR -> stringResource(Res.string.lt_error) to MaterialTheme.colorScheme.error
        ConnectionState.DISCONNECTED -> stringResource(Res.string.lt_disconnected) to MaterialTheme.colorScheme.onSurfaceVariant
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
        label = { Text(stringResource(Res.string.lt_username_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(Res.string.lt_create_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                stringResource(Res.string.lt_create_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onCreate, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(Res.string.lt_create_btn))
                }
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(Res.string.lt_join_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = joinCode,
                onValueChange = onJoinCodeChange,
                label = { Text(stringResource(Res.string.lt_join_code_label)) },
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
                Text(stringResource(Res.string.lt_join_btn))
            }
        }
    }
}

@Composable
private fun RoomContent(
    roomCode: String,
    isHost: Boolean,
    members: List<MemberItem>,
    pending: List<Pair<String, String>>, // userId, nombre de usuario
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onTransferHost: (String) -> Unit,
    onKick: (String) -> Unit,
    onCopyCode: () -> Unit,
    onLeave: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (isHost) stringResource(Res.string.lt_host_badge) else stringResource(Res.string.lt_guest_badge),
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
                    Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(Res.string.lt_copy_code), modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    if (pending.isNotEmpty()) {
        Text(stringResource(Res.string.lt_pending_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        pending.forEach { (userId, name) ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { onApprove(userId) }) {
                        Icon(Icons.Filled.Check, stringResource(Res.string.lt_approve), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { onReject(userId) }) {
                        Icon(Icons.Filled.Close, stringResource(Res.string.lt_reject), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    Text(
        stringResource(Res.string.lt_members_title, members.size),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    LazyColumn(
        modifier = Modifier.fillMaxWidth().height((members.size.coerceAtMost(6) * 52).dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(members, key = { it.userId }) { member ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (member.isHost) {
                        Icon(Icons.Filled.Star, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        if (member.isMe) stringResource(Res.string.lt_you_suffix, member.username) else member.username,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    // Controles exclusivos del anfitrión sobre otros miembros: transferir anfitrión, expulsar.
                    if (isHost && !member.isMe) {
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = stringResource(Res.string.lt_member_options),
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                tonalElevation = 0.dp,
                                shadowElevation = 8.dp,
                            ) {
                                if (!member.isHost) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.lt_make_host)) },
                                        onClick = { showMenu = false; onTransferHost(member.userId) },
                                        leadingIcon = { Icon(Icons.Filled.StarOutline, null) },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.lt_kick), color = MaterialTheme.colorScheme.error) },
                                    onClick = { showMenu = false; onKick(member.userId) },
                                    leadingIcon = { Icon(Icons.Filled.PersonRemove, null, tint = MaterialTheme.colorScheme.error) },
                                )
                            }
                        }
                    }
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
        Text(stringResource(Res.string.lt_leave_btn))
    }
}
