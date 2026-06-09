package com.example.melodist.ui.components.context

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.rememberCursorPositionProvider
import com.example.melodist.download.DownloadState
import com.example.melodist.ui.components.song.AddToPlaylistDialog
import com.example.melodist.ui.helpers.rememberSongDownloadState
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.viewmodels.LibraryPlaylistsViewModel
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import lyrik.composeapp.generated.resources.Res
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CollectionContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    title: String,
    isPlaylist: Boolean,
    onOpen: () -> Unit,
    onPlay: (() -> Unit)? = null,
    onShuffle: (() -> Unit)? = null,
    onRemoveFromLibrary: (() -> Unit)? = null,
) {

    if (!expanded) return
    val cursorPositionProvider = rememberCursorPositionProvider()

    Popup(
        onDismissRequest = onDismiss,
        popupPositionProvider = cursorPositionProvider,
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            modifier = Modifier
                .width(260.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    StyledMenuItem(
                        text = stringResource(if (isPlaylist) Res.string.context_open_playlist else Res.string.context_open_album),
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        onClick = onOpen
                    )

                    onPlay?.let {
                        StyledMenuItem(
                            text = stringResource(Res.string.context_play),
                            icon = Icons.Default.PlayArrow,
                            iconTint = MaterialTheme.colorScheme.primary,
                            onClick = it
                        )
                    }

                    onShuffle?.let {
                        StyledMenuItem(
                            text = stringResource(Res.string.context_shuffle),
                            icon = Icons.Default.Shuffle,
                            iconTint = MaterialTheme.colorScheme.primary,
                            onClick = it
                        )
                    }

                    onRemoveFromLibrary?.let {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        StyledMenuItem(
                            text = stringResource(Res.string.context_remove_library),
                            icon = Icons.Default.Delete,
                            iconTint = MaterialTheme.colorScheme.error,
                            onClick = it
                        )
                    }

            }

        }


    }
}


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SongContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    song: SongItem,
    onRemoveFromLibrary: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    isLocalPlaylist: Boolean = false,
    showQueueActions: Boolean = true,
) {
    if (!expanded) return

    val downloadViewModel = LocalDownloadViewModel.current
    val downloadState by rememberSongDownloadState(song.id, downloadViewModel)
    val playerViewModel = LocalPlayerViewModel.current
    val playlistsViewModel: LibraryPlaylistsViewModel = koinInject()

    // Usamos el provider de JetBrains para que siga al ratón,
    // pero en un Popup limpio sin decoraciones extra de Material.
    val cursorPositionProvider = rememberCursorPositionProvider()

    var showPlaylistDialog by remember { mutableStateOf(false) }

    Popup(
        onDismissRequest = onDismiss,
        popupPositionProvider = cursorPositionProvider,
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            modifier = Modifier
                .width(260.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                ContextMenuItem(
                    text = stringResource(Res.string.context_start_radio),
                    icon = Icons.Default.Radio,
                    onClick = {
                        val endpoint = song.endpoint
                        if (endpoint != null) {
                            playerViewModel.playEndpoint(endpoint)
                        } else {
                            playerViewModel.playEndpoint(WatchEndpoint(videoId = song.id))
                        }
                    }
                )

                val (label, icon, color) = when (downloadState) {
                    is DownloadState.Completed -> Triple(
                        stringResource(Res.string.context_remove_download),
                        Icons.Default.DeleteOutline,
                        MaterialTheme.colorScheme.error
                    )

                    is DownloadState.Downloading, is DownloadState.Queued -> Triple(
                        stringResource(Res.string.context_cancel_download),
                        Icons.Default.Cancel,
                        MaterialTheme.colorScheme.error
                    )

                    else -> Triple(stringResource(Res.string.context_download), Icons.Default.Download, MaterialTheme.colorScheme.primary)
                }

                ContextMenuItem(label, icon, color) {
                    when (downloadState) {
                        is DownloadState.Completed -> downloadViewModel.removeDownload(song.id)
                        is DownloadState.Downloading, is DownloadState.Queued -> downloadViewModel.cancelDownload(song.id)
                        else -> downloadViewModel.downloadSong(song)
                    }
                    onDismiss()
                }

                if (showQueueActions) {
                    HorizontalDivider(
                        Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    ContextMenuItem(stringResource(Res.string.context_play_next), Icons.AutoMirrored.Filled.PlaylistAdd) {
                        playerViewModel.playNextResolved(song)
                        onDismiss()
                    }
                    ContextMenuItem(stringResource(Res.string.context_add_queue), Icons.AutoMirrored.Filled.QueueMusic) {
                        playerViewModel.addToQueueResolved(song)
                        onDismiss()
                    }
                }

                HorizontalDivider(
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                ContextMenuItem(stringResource(Res.string.context_add_playlist), Icons.Default.AddCircleOutline) {
                    showPlaylistDialog = true
                    // No hacemos onDismiss aquí para que no parpadee al abrir el dialog
                }

                if (onRemoveFromLibrary != null || (isLocalPlaylist && onRemoveFromPlaylist != null)) {
                    onRemoveFromLibrary?.let {
                        ContextMenuItem(
                            "Eliminar de biblioteca",
                            Icons.Default.Delete,
                            MaterialTheme.colorScheme.error
                        ) {
                            it()
                            onDismiss()
                        }
                    }

                    if (isLocalPlaylist && onRemoveFromPlaylist != null) {
                        ContextMenuItem(
                            stringResource(Res.string.context_remove_playlist),
                            Icons.Default.RemoveCircleOutline,
                            MaterialTheme.colorScheme.error
                        ) {
                            onRemoveFromPlaylist()
                            onDismiss()
                        }
                    }
                }
            }

        }
    }

    if (showPlaylistDialog) {
        AddToPlaylistDialog(
            song = song,
            playlistsViewModel = playlistsViewModel,
            onDismiss = {
                showPlaylistDialog = false
                onDismiss() // Cerramos el estado del menú al cerrar el diálogo
            }
        )
    }
}

/**
 * Item de menú optimizado para Desktop (altura reducida y hover suave)
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ContextMenuItem(
    text: String,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    var isHovered by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .fillMaxWidth()
            .height(36.dp) // Altura estándar Desktop (más baja que Mobile)
            .background(if (isHovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f) else Color.Transparent)
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

// Un helper pequeño para no repetir código y mantener los iconos de 18.dp
@Composable
private fun CustomDropdownItem(
    text: String,
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp)) },
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = Modifier.height(38.dp) // Altura Desktop
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun StyledMenuItem(
    text: String,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    var hovered by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .fillMaxWidth()
            .height(38.dp) // Altura más compacta para Desktop
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (hovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                else Color.Transparent
            )
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp) // Icono más pequeño = más elegante
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium, // Tipografía equilibrada
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}