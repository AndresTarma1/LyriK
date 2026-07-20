package com.example.melodist.ui.components.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.melodist.ui.components.song.AddToPlaylistDialog
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.utils.LocalSnackbarHostState
import com.example.melodist.utils.LocalSnackbarScope
import com.example.melodist.utils.LocalUserPreferences
import com.example.melodist.ui.components.layout.AppVerticalScrollbar
import com.example.melodist.viewmodels.PlayerUiState
import com.example.melodist.viewmodels.QueueSource
import com.example.melodist.utils.LocalPlaylistsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lyrik.composeapp.generated.resources.Res
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun PlaybackQueuePanel(
    state: PlayerUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.background,
    showCloseButton: Boolean = true,
) {
    val playerViewModel = LocalPlayerViewModel.current
    val downloadViewModel = LocalDownloadViewModel.current
    val coroutineScope = rememberCoroutineScope()
    val preferencesRepo = LocalUserPreferences.current
    val listState = rememberLazyListState()
    val playlistsViewModel = LocalPlaylistsViewModel.current
    var showSaveQueueDialog by remember { mutableStateOf(false) }
    var showAddQueueDialog by remember { mutableStateOf(false) }
    val defaultQueueName = stringResource(Res.string.queue_title)
    var queuePlaylistName by remember(state.queueSource, state.queue, defaultQueueName) {
        mutableStateOf(
            when (val source = state.queueSource) {
                is QueueSource.Album -> source.title
                is QueueSource.Playlist -> source.title
                else -> defaultQueueName
            }
        )
    }
    val queueSongs = remember(state.queue) { state.queue.map { it.toSongItem() } }

    val queueLocked by preferencesRepo.queueLocked.collectAsState(initial = false)
    val snackbar = LocalSnackbarHostState.current
    val scope = LocalSnackbarScope.current

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        playerViewModel.moveQueueItem(from.index, to.index)
    }

    LaunchedEffect(state.isShuffled) {
        if (state.queue.isNotEmpty() && state.currentIndex in state.queue.indices) {
            delay(100.milliseconds)
            listState.scrollToItem(state.currentIndex)
        }
    }

    Surface(
        modifier = modifier,
        color = containerColor,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            QueueHeader(
                state = state,
                showCloseButton = showCloseButton,
                onDismiss = onDismiss,
                onAddToPlaylist = { showAddQueueDialog = true },
                onDownloadAll = {
                    downloadViewModel.downloadAll(queueSongs)
                    scope.launch {
                        snackbar.showSnackbar(getString(Res.string.queue_download_added, queueSongs.size))
                    }
                },
                onSaveAsPlaylist = { showSaveQueueDialog = true },
                onToggleLock = {
                    coroutineScope.launch { preferencesRepo.setQueueLocked(!queueLocked) }
                },
                queueLocked = queueLocked,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Box(Modifier.fillMaxSize()) {
                if (state.queue.isEmpty()) {
                    EmptyQueuePlaceholder()
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(
                            items = state.queue,
                            key = { index, _ ->
                                state.queueSession.order.getOrNull(index) ?: index
                            }
                        ) { index, queueSong ->
                            val isCurrent = index == state.currentIndex
                            val itemKey = state.queueSession.order.getOrNull(index) ?: index
                            ReorderableItem(reorderableState, key = itemKey) { isDragging ->
                                val dragModifier = if (!queueLocked) Modifier.draggableHandle() else Modifier
                                QueueItem(
                                    song = queueSong,
                                    isCurrent = isCurrent,
                                    isDragging = isDragging,
                                    dragModifier = dragModifier,
                                    onClick = { playerViewModel.playAtIndex(index) },
                                    onRemove = { playerViewModel.removeFromQueue(index) },
                                )
                            }
                        }
                    }
                }

                AppVerticalScrollbar(
                    state = listState,
                    modifier = Modifier.align(Alignment.BottomEnd).width(8.dp).padding(end = 4.dp, bottom = 4.dp)
                )
            }
        }
    }

    if (showAddQueueDialog) {
        AddToPlaylistDialog(
            songs = queueSongs,
            playlistsViewModel = playlistsViewModel,
            onDismiss = { showAddQueueDialog = false }
        )
    }

    if (showSaveQueueDialog) {
        AlertDialog(
            onDismissRequest = { showSaveQueueDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            title = { Text(stringResource(Res.string.save_queue_title)) },
            text = {
                OutlinedTextField(
                    value = queuePlaylistName,
                    onValueChange = { queuePlaylistName = it },
                    label = { Text(stringResource(Res.string.playlist_name_label)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = queuePlaylistName.isNotBlank(),
                    onClick = {
                        playlistsViewModel.createLocalPlaylist(queuePlaylistName.trim(), queueSongs)
                        scope.launch { snackbar.showSnackbar(getString(Res.string.queue_saved_as, queuePlaylistName.trim())) }
                        showSaveQueueDialog = false
                    }
                ) { Text(stringResource(Res.string.btn_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveQueueDialog = false }) { Text(stringResource(Res.string.cancel)) }
            }
        )
    }
}

@Composable
private fun QueueHeader(
    state: PlayerUiState,
    showCloseButton: Boolean,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownloadAll: () -> Unit,
    onSaveAsPlaylist: () -> Unit,
    onToggleLock: () -> Unit,
    queueLocked: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                stringResource(Res.string.queue_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                stringResource(Res.string.queue_songs_count, state.queue.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            var showMenu by remember { mutableStateOf(false) }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        stringResource(Res.string.options),
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 8.dp,
                ) {
                    DropdownMenuItem(
                        onClick = { showMenu = false; onAddToPlaylist() },
                        text = { Text(stringResource(Res.string.add_to_playlist)) },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    )
                    DropdownMenuItem(
                        onClick = { showMenu = false; onDownloadAll() },
                        text = { Text(stringResource(Res.string.download_queue)) },
                        leadingIcon = {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    )
                    DropdownMenuItem(
                        onClick = { showMenu = false; onSaveAsPlaylist() },
                        text = { Text(stringResource(Res.string.save_as_playlist)) },
                        leadingIcon = {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    )
                    DropdownMenuItem(
                        onClick = { showMenu = false; onToggleLock() },
                        text = { Text(if (queueLocked) stringResource(Res.string.unlock_queue) else stringResource(Res.string.lock_queue)) },
                        leadingIcon = {
                            Icon(
                                if (queueLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }
            }

            if (showCloseButton) {
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Icon(
                        Icons.Default.Close, stringResource(Res.string.close_queue),
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyQueuePlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.AutoMirrored.Filled.QueueMusic,
            null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(Res.string.queue_empty),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(Res.string.queue_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}
