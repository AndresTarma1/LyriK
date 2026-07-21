package com.example.musicApp.ui.components.context

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.musicApp.download.DownloadState
import com.metrolist.innertube.models.SongItem
import lyrik.composeapp.generated.resources.Res
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

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

/** Evita repetir tamaño + contentDescription en cada leadingIcon del menú. */
@Composable
private fun MenuLeadingIcon(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
    )
}

private data class DownloadMenuSpec(
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val action: SongMenuAction,
)

/** Surface compartida por ambos menús: mismo ancho, forma, elevación y borde. */
@Composable
private fun MenuSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.width(260.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CollectionContextMenuContent(
    title: String,
    isPlaylist: Boolean,
    onOpen: () -> Unit,
    onPlay: (() -> Unit)? = null,
    onShuffle: (() -> Unit)? = null,
    onRemoveFromLibrary: (() -> Unit)? = null,
) {
    MenuSurface {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        HorizontalDivider(modifier = Modifier.padding(MenuDefaults.HorizontalDividerPadding))

        DropdownMenuItem(
            text = {
                Text(
                    stringResource(
                        if (isPlaylist) Res.string.context_open_playlist
                        else Res.string.context_open_album
                    )
                )
            },
            leadingIcon = { MenuLeadingIcon(Icons.AutoMirrored.Filled.OpenInNew) },
            onClick = onOpen,
        )

        onPlay?.let { action ->
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.context_play)) },
                leadingIcon = { MenuLeadingIcon(Icons.Default.PlayArrow, MaterialTheme.colorScheme.primary) },
                onClick = action,
            )
        }

        onShuffle?.let { action ->
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.context_shuffle)) },
                leadingIcon = { MenuLeadingIcon(Icons.Default.Shuffle, MaterialTheme.colorScheme.primary) },
                onClick = action,
            )
        }

        onRemoveFromLibrary?.let { action ->
            HorizontalDivider(modifier = Modifier.padding(MenuDefaults.HorizontalDividerPadding))
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.context_remove_library)) },
                leadingIcon = { MenuLeadingIcon(Icons.Default.Delete, MaterialTheme.colorScheme.error) },
                onClick = action,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    MenuSurface {
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.context_start_radio)) },
            leadingIcon = { MenuLeadingIcon(Icons.Default.Radio) },
            onClick = { onAction(SongMenuAction.StartRadio) },
        )

        DropdownMenuItem(
            text = {
                Text(stringResource(if (liked) Res.string.context_unlike else Res.string.context_like))
            },
            leadingIcon = {
                MenuLeadingIcon(
                    icon = if (liked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = { onAction(SongMenuAction.ToggleLike) },
        )

        val downloadSpec = when (downloadState) {
            is DownloadState.Completed -> DownloadMenuSpec(
                label = stringResource(Res.string.context_remove_download),
                icon = Icons.Default.DeleteOutline,
                tint = MaterialTheme.colorScheme.error,
                action = SongMenuAction.RemoveDownload,
            )
            is DownloadState.Downloading, is DownloadState.Queued -> DownloadMenuSpec(
                label = stringResource(Res.string.context_cancel_download),
                icon = Icons.Default.Cancel,
                tint = MaterialTheme.colorScheme.error,
                action = SongMenuAction.CancelDownload,
            )
            else -> DownloadMenuSpec(
                label = stringResource(Res.string.context_download),
                icon = Icons.Default.Download,
                tint = MaterialTheme.colorScheme.primary,
                action = SongMenuAction.Download,
            )
        }

        DropdownMenuItem(
            text = { Text(downloadSpec.label) },
            leadingIcon = { MenuLeadingIcon(downloadSpec.icon, downloadSpec.tint) },
            onClick = { onAction(downloadSpec.action) },
        )

        if (showQueueActions) {
            HorizontalDivider(
                modifier = Modifier.padding(MenuDefaults.HorizontalDividerPadding),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            )

            DropdownMenuItem(
                text = { Text(stringResource(Res.string.context_play_next)) },
                leadingIcon = { MenuLeadingIcon(Icons.AutoMirrored.Filled.PlaylistAdd) },
                onClick = { onAction(SongMenuAction.PlayNext) },
            )

            DropdownMenuItem(
                text = { Text(stringResource(Res.string.context_add_queue)) },
                leadingIcon = { MenuLeadingIcon(Icons.AutoMirrored.Filled.QueueMusic) },
                onClick = { onAction(SongMenuAction.AddToQueue) },
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(MenuDefaults.HorizontalDividerPadding),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        )

        DropdownMenuItem(
            text = { Text(stringResource(Res.string.context_add_playlist)) },
            leadingIcon = { MenuLeadingIcon(Icons.Default.AddCircleOutline) },
            onClick = { onAction(SongMenuAction.AddToPlaylist) },
        )

        if (showRemoveFromLibrary) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.context_remove_library)) },
                leadingIcon = { MenuLeadingIcon(Icons.Default.Delete, MaterialTheme.colorScheme.error) },
                onClick = { onAction(SongMenuAction.RemoveFromLibrary) },
            )
        }

        if (showRemoveFromPlaylist) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.context_remove_playlist)) },
                leadingIcon = { MenuLeadingIcon(Icons.Default.RemoveCircleOutline, MaterialTheme.colorScheme.error) },
                onClick = { onAction(SongMenuAction.RemoveFromPlaylist) },
            )
        }
    }
}