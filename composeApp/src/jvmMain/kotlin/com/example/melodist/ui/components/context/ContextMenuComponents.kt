package com.example.melodist.ui.components.context

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
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
import com.example.melodist.download.DownloadState
import com.metrolist.innertube.models.SongItem
import lyrik.composeapp.generated.resources.Res
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.onHover

sealed interface SongMenuAction {
    data object StartRadio : SongMenuAction
    data object ToggleLike : SongMenuAction
    data object Download : SongMenuAction
    data object CancelDownload : SongMenuAction
    data object RemoveDownload : SongMenuAction
    data object PlayNext : SongMenuAction
    data object AddToQueue : SongMenuAction
    data object AddToPlaylist : SongMenuAction
    data object RemoveFromLibrary : SongMenuAction
    data object RemoveFromPlaylist : SongMenuAction
}

@Composable
fun CollectionContextMenuContent(
    title: String,
    isPlaylist: Boolean,
    onOpen: () -> Unit,
    onPlay: (() -> Unit)? = null,
    onShuffle: (() -> Unit)? = null,
    onRemoveFromLibrary: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.width(260.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
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

@Composable
fun SongContextMenuContent(
    song: SongItem,
    liked: Boolean,
    downloadState: DownloadState?,
    showQueueActions: Boolean = true,
    showRemoveFromLibrary: Boolean = false,
    showRemoveFromPlaylist: Boolean = false,
    onAction: (SongMenuAction) -> Unit,
) {
    Surface(
        modifier = Modifier.width(260.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            ContextMenuItem(
                text = stringResource(Res.string.context_start_radio),
                icon = Icons.Default.Radio,
                onClick = { onAction(SongMenuAction.StartRadio) }
            )

            ContextMenuItem(
                text = if (liked) stringResource(Res.string.context_unlike) else stringResource(Res.string.context_like),
                icon = if (liked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                iconTint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                onClick = { onAction(SongMenuAction.ToggleLike) }
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
                else -> Triple(
                    stringResource(Res.string.context_download),
                    Icons.Default.Download,
                    MaterialTheme.colorScheme.primary
                )
            }

            val downloadAction = when (downloadState) {
                is DownloadState.Completed -> SongMenuAction.RemoveDownload
                is DownloadState.Downloading, is DownloadState.Queued -> SongMenuAction.CancelDownload
                else -> SongMenuAction.Download
            }

            ContextMenuItem(label, icon, color) { onAction(downloadAction) }

            if (showQueueActions) {
                HorizontalDivider(
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
                ContextMenuItem(stringResource(Res.string.context_play_next), Icons.AutoMirrored.Filled.PlaylistAdd) {
                    onAction(SongMenuAction.PlayNext)
                }
                ContextMenuItem(stringResource(Res.string.context_add_queue), Icons.AutoMirrored.Filled.QueueMusic) {
                    onAction(SongMenuAction.AddToQueue)
                }
            }

            HorizontalDivider(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            ContextMenuItem(stringResource(Res.string.context_add_playlist), Icons.Default.AddCircleOutline) {
                onAction(SongMenuAction.AddToPlaylist)
            }

            if (showRemoveFromLibrary) {
                ContextMenuItem(
                    "Eliminar de biblioteca",
                    Icons.Default.Delete,
                    MaterialTheme.colorScheme.error
                ) { onAction(SongMenuAction.RemoveFromLibrary) }
            }

            if (showRemoveFromPlaylist) {
                ContextMenuItem(
                    stringResource(Res.string.context_remove_playlist),
                    Icons.Default.RemoveCircleOutline,
                    MaterialTheme.colorScheme.error
                ) { onAction(SongMenuAction.RemoveFromPlaylist) }
            }
        }
    }
}

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
            .height(36.dp)
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
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (hovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                else Color.Transparent
            )
            .onHover { hovered = it }
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}
