package com.example.melodist.ui.screens.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Explicit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import com.example.melodist.ui.components.BoxForContainerContextMenuItem
import com.example.melodist.ui.components.song.DownloadIndicator
import com.example.melodist.ui.components.song.AddToPlaylistDialog
import com.example.melodist.ui.components.MelodistImage
import com.example.melodist.ui.components.PlaceholderType
import com.example.melodist.ui.components.context.SongContextMenu
import com.example.melodist.download.DownloadState
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.ui.helpers.rememberSongDownloadState
import com.example.melodist.ui.helpers.rememberSongLikedState
import com.example.melodist.ui.screens.shared.formatDuration
import com.example.melodist.utils.LocalSnackbarHostState
import com.metrolist.innertube.models.SongItem
import com.example.melodist.viewmodels.LibraryPlaylistsViewModel
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.modifier.onHover
import org.koin.compose.koinInject

val ListItemHeight = 72.dp
val ListThumbnailSize = 56.dp
val ThumbnailCornerRadius = 8.dp

@Composable
inline fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    noinline subtitle: (@Composable RowScope.() -> Unit)? = null,
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isSelected: Boolean? = false,
    isActive: Boolean = false,
    isAvailable: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (isActive) {
            modifier
                .height(ListItemHeight)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    color = // selected active
                        if (isSelected == true) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.secondaryContainer
                )
        } else if (isSelected == true) {
            modifier // inactive selected
                .height(ListItemHeight)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color = MaterialTheme.colorScheme.inversePrimary.copy(alpha = 0.4f))
        } else {
            modifier // default
                .height(ListItemHeight)
                .padding(horizontal = 8.dp)
        }
    ) {
        Box(
            modifier = Modifier.padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            thumbnailContent()
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    subtitle()
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            trailingContent()
        }
    }
}

@Composable
internal fun MultiSongSelectionBar(
    selectedSongs: List<SongItem>,
    allSongIds: List<String> = emptyList(),
    isLocalPlaylist: Boolean,
    onClearSelection: () -> Unit,
    onSelectAll: (() -> Unit)? = null,
    onRemoveFromPlaylist: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val downloadViewModel = LocalDownloadViewModel.current
    val playlistsViewModel: LibraryPlaylistsViewModel = koinInject()
    var showPlaylistDialog by remember { mutableStateOf(false) }
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val allSelected = allSongIds.isNotEmpty() && selectedSongs.size == allSongIds.size

    if (selectedSongs.isEmpty()) return

    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.padding(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "${selectedSongs.size} seleccionadas",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            if (onSelectAll != null && allSongIds.isNotEmpty()) {
                if (allSelected) {
                    IconButton(onClick = onClearSelection) {
                        Icon(Icons.Default.Clear, "Deseleccionar todo")
                    }
                } else {
                    IconButton(onClick = onSelectAll) {
                        Icon(Icons.Default.AddBox, "Seleccionar todo")
                    }
                }
            }
            IconButton(onClick = { showPlaylistDialog = true }) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Añadir a playlist")
            }
            IconButton(onClick = { downloadViewModel.downloadAll(selectedSongs) }) {
                Icon(Icons.Default.Download, "Descargar")
            }
            if (isLocalPlaylist && onRemoveFromPlaylist != null) {
                IconButton(onClick = {
                    selectedSongs.forEach { onRemoveFromPlaylist(it.id) }
                    scope.launch {
                        snackbar.showSnackbar("${selectedSongs.size} canciones eliminadas de la playlist")
                    }
                    onClearSelection()
                }) {
                    Icon(Icons.Default.Delete, "Quitar de playlist", tint = MaterialTheme.colorScheme.error)
                }
            }
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Default.Close, "Cancelar")
            }
        }
    }

    if (showPlaylistDialog) {
        AddToPlaylistDialog(
            songs = selectedSongs,
            playlistsViewModel = playlistsViewModel,
            onDismiss = {
                showPlaylistDialog = false
                onClearSelection()
            }
        )
    }
}

@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String?,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isSelected: Boolean? = false,
    isActive: Boolean = false,
) = ListItem(
    title = title,
    subtitle = {
        badges()

        if (!subtitle.isNullOrEmpty()) {
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    },
    thumbnailContent = thumbnailContent,
    trailingContent = trailingContent,
    modifier = modifier,
    isSelected = isSelected,
    isActive = isActive
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SongListItem(
    albumIndex: Int? = null,
    song: SongItem,
    onPlay: () -> Unit,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    onSelectionChange: ((Boolean) -> Unit)? = null,
    isLocalPlaylist: Boolean = false,
    onRemoveFromPlaylist: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val downloadViewModel = LocalDownloadViewModel.current
    val downloadState by rememberSongDownloadState(song.id, downloadViewModel)
    val showDownloadIndicator = downloadState != null && downloadState !is DownloadState.Cancelled
    val playerViewModel = LocalPlayerViewModel.current
    val liked = rememberSongLikedState(song.id, playerViewModel)

    var isHovered by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        BoxForContainerContextMenuItem(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
            onHoverChange = { isHovered = it },
            onMenuAction = {
                showContextMenu = true
            }
        ) { menuButtonModifier, openMenuFromButton ->
            Surface(
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = selectionMode) { onSelectionChange?.invoke(!isSelected) }
            ) {
                ListItem(
                    title = song.title,
                    subtitle = song.artists.joinToString(", ") { it.name }.ifEmpty { "Artista desconocido" },
                    badges = {
                        if (showDownloadIndicator) {
                            DownloadIndicator(
                                state = downloadState,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                        }


                        if (song.explicit) {
                            Icon(
                                Icons.Default.Explicit, null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                    },
                    thumbnailContent = {
                        Box(
                            modifier = Modifier.width(ListThumbnailSize)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable { onPlay() },
                            contentAlignment = Alignment.Center
                        ) {

                            if (albumIndex != null) {
                                Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                                    if (isHovered) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Text(
                                            text = albumIndex.toString(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(ListThumbnailSize)
                                        .onHover{ isHovered = it }
                                ) {
                                    MelodistImage(
                                        url = song.thumbnail,
                                        contentDescription = song.title,
                                        modifier = Modifier.matchParentSize(),
                                        contentScale = ContentScale.Crop,
                                        shape = RoundedCornerShape(ThumbnailCornerRadius),
                                        placeholderType = PlaceholderType.SONG,
                                        iconSize = 24.dp,
                                        isLowRes = true
                                    )
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(
                                                Color.Black.copy(alpha = if (isHovered) 0.4f else 0f),
                                                shape = RoundedCornerShape(ThumbnailCornerRadius)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = Color.White.copy(alpha = if (isHovered) 1f else 0f),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                        }
                    },
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (isHovered && !selectionMode) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(onClick = { playerViewModel.toggleLikeForSong(song) }, modifier = Modifier.size(36.dp)) {
                                        Icon(
                                            if (liked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                                            null,
                                            modifier = Modifier.size(20.dp),
                                            tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = openMenuFromButton, modifier = menuButtonModifier.size(36.dp)) {
                                        Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }

                                if (selectionMode || isHovered) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { onSelectionChange?.invoke(it) }
                                    )
                                } else {
                                    Text(
                                        text = formatDuration(song.duration ?: 0),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.width(40.dp)
                                    )
                                }

                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isSelected = isSelected
                )
            }
        }

        SongContextMenu(
            expanded = showContextMenu,
            isLocalPlaylist = isLocalPlaylist,
            onRemoveFromPlaylist = { onRemoveFromPlaylist?.invoke(song.id) },
            onDismiss = { showContextMenu = false },
            song = song
        )
    }
}

