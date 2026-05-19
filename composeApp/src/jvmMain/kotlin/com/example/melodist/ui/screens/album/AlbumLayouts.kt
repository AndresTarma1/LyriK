package com.example.melodist.ui.screens.album

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Explicit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.melodist.ui.components.dialogs.DownloadConfirmationDialog
import com.example.melodist.navigation.Route
import com.example.melodist.ui.components.layout.AppVerticalScrollbar
import com.example.melodist.ui.components.LoadingMoreSongsItem
import com.example.melodist.ui.components.MelodistImage
import com.example.melodist.ui.components.PlaceholderType
import com.example.melodist.ui.screens.AlbumScreenActions
import com.example.melodist.ui.screens.AlbumScreenState
import com.example.melodist.ui.screens.playlist.MultiSongSelectionBar
import com.example.melodist.ui.screens.playlist.SongListItem
import com.example.melodist.ui.screens.shared.calculateTotalDuration
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.utils.LocalPlayerViewModel
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.AlbumPage


@Composable
internal fun AlbumScreenLayout(
    albumPage: AlbumPage,
    state: AlbumScreenState,
    actions: AlbumScreenActions,
) {
    val playerViewModel = LocalPlayerViewModel.current
    val downloadViewModel = LocalDownloadViewModel.current
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)

    val songIds = remember(state.songs) { state.songs.map { it.id } }
    val isFullyDownloadedState = remember(songIds, downloadViewModel) {
        downloadViewModel.isFullyDownloadedFlow(songIds)
    }.collectAsState(initial = false)
    val isAnyDownloadingState = remember(songIds, downloadViewModel) {
        downloadViewModel.isAnyDownloadingFlow(songIds)
    }.collectAsState(initial = false)

    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        DownloadConfirmationDialog(
            onConfirm = {
                downloadViewModel.removeDownloads(songIds)
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    val controls = AlbumInfoPanelControls(
        isSaved = state.isSaved,
        isSaving = state.isSaving,
        isLoadingForPlay = state.isLoadingForPlay,
    )

    // Estado de selección múltiple
    var selectedSongIds by remember(state.songs) { mutableStateOf<Set<String>>(emptySet()) }
    val selectedSongs = remember(state.songs, selectedSongIds) {
        state.songs.filter { it.id in selectedSongIds }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth < 980.dp) {
            AlbumCompactLayout(
                albumPage = albumPage,
                songs = state.songs,
                hasMore = state.hasMore,
                controls = controls,
                isAnyDownloading = isAnyDownloadingState.value,
                isFullyDownloaded = isFullyDownloadedState.value,
                selectedSongIds = selectedSongIds,
                selectedSongs = selectedSongs,
                onSelectionChange = { id, selected ->
                    selectedSongIds = if (selected) selectedSongIds + id else selectedSongIds - id
                },
                onClearSelection = { selectedSongIds = emptySet() },
                onSurfaceColor = onSurfaceColor,
                onSurfaceVariant = onSurfaceVariant,
                onLoadMore = actions.onLoadMore,
                onSongClick = { index ->
                    if (selectedSongIds.isNotEmpty()) {
                        selectedSongIds = if (state.songs[index].id in selectedSongIds) {
                            selectedSongIds - state.songs[index].id
                        } else {
                            selectedSongIds + state.songs[index].id
                        }
                    } else {
                        playerViewModel.playAlbum(state.songs, index, albumPage.album.browseId, albumPage.album.title)
                    }
                },
                onToggleSave = actions.onToggleSave,
                onPlayAll = actions.onPlayAll,
                onShuffle = actions.onShuffle,
                showDeleteDialog = { showDeleteDialog = true },
                onNavigate = actions.onNavigate,
            )
        } else {
            AlbumWideLayout(
                albumPage = albumPage,
                songs = state.songs,
                hasMore = state.hasMore,
                controls = controls,
                isAnyDownloading = isAnyDownloadingState.value,
                isFullyDownloaded = isFullyDownloadedState.value,
                selectedSongIds = selectedSongIds,
                selectedSongs = selectedSongs,
                onSelectionChange = { id, selected ->
                    selectedSongIds = if (selected) selectedSongIds + id else selectedSongIds - id
                },
                onClearSelection = { selectedSongIds = emptySet() },
                onSurfaceColor = onSurfaceColor,
                onSurfaceVariant = onSurfaceVariant,
                onLoadMore = actions.onLoadMore,
                onSongClick = { index ->
                    if (selectedSongIds.isNotEmpty()) {
                        selectedSongIds = if (state.songs[index].id in selectedSongIds) {
                            selectedSongIds - state.songs[index].id
                        } else {
                            selectedSongIds + state.songs[index].id
                        }
                    } else {
                        playerViewModel.playAlbum(state.songs, index, albumPage.album.browseId, albumPage.album.title)
                    }
                },
                onToggleSave = actions.onToggleSave,
                onPlayAll = actions.onPlayAll,
                onShuffle = actions.onShuffle,
                showDeleteDialog = { showDeleteDialog = true },
                onNavigate = actions.onNavigate,
            )
        }
    }
}


@Composable
internal fun AlbumWideLayout(
    albumPage: AlbumPage,
    songs: List<SongItem>,
    hasMore: Boolean,
    controls: AlbumInfoPanelControls,
    isAnyDownloading: Boolean,
    isFullyDownloaded: Boolean,
    selectedSongIds: Set<String>,
    selectedSongs: List<SongItem>,
    onSelectionChange: (String, Boolean) -> Unit,
    onClearSelection: () -> Unit,
    onSurfaceColor: Color,
    onSurfaceVariant: Color,
    onLoadMore: () -> Unit,
    onSongClick: (index: Int) -> Unit,
    onToggleSave: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    showDeleteDialog: () -> Unit,
    onNavigate: (Route) -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize().padding(start = 48.dp, end = 24.dp, top = 16.dp)) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AlbumInfoPanel(
                albumPage = albumPage,
                songs = songs,
                onSurfaceColor = onSurfaceColor,
                onSurfaceVariant = onSurfaceVariant,
                coverSize = 240.dp,
                controls = controls,
                isAnyDownloading = isAnyDownloading,
                isFullyDownloaded = isFullyDownloaded,
                onToggleSave = onToggleSave,
                onPlayAll = onPlayAll,
                onShuffle = onShuffle,
                showDeleteDialog = showDeleteDialog,
                onNavigate = onNavigate,
            )
        }

        Spacer(Modifier.width(32.dp))

        // Lista de canciones con scroll propio
        AlbumSongList(
            modifier = Modifier.weight(1f),
            songs = songs,
            hasMore = hasMore,
            selectedSongIds = selectedSongIds,
            onSelectionChange = onSelectionChange,
            onClearSelection = onClearSelection,
            selectedSongs = selectedSongs,
            isLocalPlaylist = false,
            onRemoveFromPlaylist = null,
            onLoadMore = onLoadMore,
            onSongClick = onSongClick,
        )
    }
}


@Composable
internal fun AlbumCompactLayout(
    albumPage: AlbumPage,
    songs: List<SongItem>,
    hasMore: Boolean,
    controls: AlbumInfoPanelControls,
    isAnyDownloading: Boolean,
    isFullyDownloaded: Boolean,
    selectedSongIds: Set<String>,
    selectedSongs: List<SongItem>,
    onSelectionChange: (String, Boolean) -> Unit,
    onClearSelection: () -> Unit,
    onSurfaceColor: Color,
    onSurfaceVariant: Color,
    onLoadMore: () -> Unit,
    onSongClick: (index: Int) -> Unit,
    onToggleSave: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    showDeleteDialog: () -> Unit,
    onNavigate: (Route) -> Unit,
) {
    Box {
        val lazyColumnState = rememberLazyListState()

        val showStickHeader by remember {
            derivedStateOf { lazyColumnState.firstVisibleItemIndex > 0 }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = lazyColumnState,
        ) {
            if (showStickHeader) {
                stickyHeader {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(start = 56.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = albumPage.album.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showDeleteDialog() }) {
                            Icon(
                                imageVector = if (isFullyDownloaded) Icons.Filled.Delete else Icons.Default.Download,
                                contentDescription = if (isFullyDownloaded) "Eliminar descargas" else "Descargar álbum"
                            )
                        }
                        IconButton(onClick = onPlayAll) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Reproducir")
                        }
                    }
                }
            }

            // Info panel sin scroll propio: se desplaza con la Column
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(16.dp))
                    AlbumInfoPanel(
                        albumPage = albumPage,
                        songs = songs,
                        onSurfaceColor = onSurfaceColor,
                        onSurfaceVariant = onSurfaceVariant,
                        coverSize = 190.dp,
                        controls = controls,
                        isAnyDownloading = isAnyDownloading,
                        isFullyDownloaded = isFullyDownloaded,
                        onToggleSave = onToggleSave,
                        onPlayAll = onPlayAll,
                        onShuffle = onShuffle,
                        showDeleteDialog = showDeleteDialog,
                        onNavigate = onNavigate,
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                SongListItem(
                    albumIndex = index + 1,
                    song = song,
                    onPlay = { onSongClick(index) },
                    isSelected = song.id in selectedSongIds,
                    selectionMode = selectedSongIds.isNotEmpty(),
                    onSelectionChange = { selected ->
                        onSelectionChange(song.id, selected)
                    },
                    isLocalPlaylist = false,
                    onRemoveFromPlaylist = null,
                    modifier = Modifier.animateItem(
                        placementSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                )
                if (index < songs.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                        modifier = Modifier.padding(start = 48.dp)
                    )
                }
            }

            if (hasMore) item { LoadingMoreSongsItem(onLoadMore = onLoadMore) }
        }

        AppVerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd),
            state = lazyColumnState
        )

        MultiSongSelectionBar(
            selectedSongs = selectedSongs,
            isLocalPlaylist = false,
            onClearSelection = onClearSelection,
            onRemoveFromPlaylist = null,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}


@Composable
private fun AlbumSongList(
    modifier: Modifier,
    songs: List<SongItem>,
    hasMore: Boolean,
    selectedSongIds: Set<String>,
    onSelectionChange: (String, Boolean) -> Unit,
    onClearSelection: () -> Unit,
    selectedSongs: List<SongItem>,
    isLocalPlaylist: Boolean,
    onRemoveFromPlaylist: ((String) -> Unit)?,
    onLoadMore: () -> Unit,
    onSongClick: (index: Int) -> Unit,
) {
    Box(modifier = modifier) {
        val lazyListState = rememberLazyListState()

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 16.dp),
        ) {
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                SongListItem(
                    albumIndex = index + 1,
                    song = song,
                    onPlay = { onSongClick(index) },
                    isSelected = song.id in selectedSongIds,
                    selectionMode = selectedSongIds.isNotEmpty(),
                    onSelectionChange = { selected ->
                        onSelectionChange(song.id, selected)
                    },
                    isLocalPlaylist = isLocalPlaylist,
                    onRemoveFromPlaylist = onRemoveFromPlaylist,
                    modifier = Modifier.animateItem(
                        placementSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                )
                if (index < songs.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                        modifier = Modifier.padding(start = 48.dp)
                    )
                }
            }

            if (hasMore) item { LoadingMoreSongsItem(onLoadMore = onLoadMore) }
        }

        AppVerticalScrollbar(
            state = lazyListState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(12.dp)
        )

        MultiSongSelectionBar(
            selectedSongs = selectedSongs,
            isLocalPlaylist = isLocalPlaylist,
            onClearSelection = onClearSelection,
            onRemoveFromPlaylist = onRemoveFromPlaylist,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}


internal data class AlbumInfoPanelControls(
    val isSaved: Boolean,
    val isSaving: Boolean,
    val isLoadingForPlay: Boolean,
)

@Composable
internal fun AlbumInfoPanel(
    albumPage: AlbumPage,
    songs: List<SongItem>,
    onSurfaceColor: Color,
    onSurfaceVariant: Color,
    coverSize: Dp,
    controls: AlbumInfoPanelControls,
    isAnyDownloading: Boolean,
    isFullyDownloaded: Boolean,
    onToggleSave: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    showDeleteDialog: () -> Unit,
    onNavigate: (Route) -> Unit,
) {
    val downloadViewModel = LocalDownloadViewModel.current

    val songIds = remember(songs) { songs.map { it.id } }
    val isDownloading by remember(songIds, downloadViewModel) {
        downloadViewModel.isAnyDownloadingFlow(songIds)
    }.collectAsState(initial = false)

    val isFullyDownloaded by remember(songIds, downloadViewModel) {
        downloadViewModel.isFullyDownloadedFlow(songIds)
    }.collectAsState(initial = false)

    val firstArtist = albumPage.album.artists?.firstOrNull()

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = firstArtist?.id != null) {
                firstArtist?.id?.let { onNavigate(Route.Artist(it)) }
            }
            .pointerHoverIcon(if (firstArtist?.id != null) PointerIcon.Hand else PointerIcon.Default)
    ) {
        Text(
            text = "Autor • ${firstArtist?.name ?: "Artista"}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = onSurfaceColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }

    Spacer(Modifier.height(20.dp))

    Card(
        modifier = Modifier.size(coverSize).shadow(24.dp, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp)
    ) {
        MelodistImage(
            url = albumPage.album.thumbnail,
            contentDescription = albumPage.album.title,
            modifier = Modifier.fillMaxSize(),
            placeholderType = PlaceholderType.ALBUM,
            contentScale = ContentScale.Crop,
            iconSize = coverSize * 0.33f,
        )
    }

    Spacer(Modifier.height(24.dp))

    Text(
        text = albumPage.album.title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        color = onSurfaceColor
    )

    Spacer(Modifier.height(6.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (albumPage.album.explicit) {
            Icon(Icons.Default.Explicit, null, modifier = Modifier.size(16.dp), tint = onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            "Álbum • ${albumPage.album.year ?: ""}",
            style = MaterialTheme.typography.bodyMedium,
            color = onSurfaceVariant
        )
    }

    Text(
        "${songs.size} canciones • ${calculateTotalDuration(songs)}",
        style = MaterialTheme.typography.bodySmall,
        color = onSurfaceVariant.copy(alpha = 0.7f)
    )

    Spacer(Modifier.height(24.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconButton(
            onClick = { if (!controls.isSaving) onToggleSave() },
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .pointerHoverIcon(if (controls.isSaving) PointerIcon.Default else PointerIcon.Hand)
        ) {
            if (controls.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    if (controls.isSaved) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    null,
                    tint = if (controls.isSaved) MaterialTheme.colorScheme.primary else onSurfaceColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        FloatingActionButton(
            onClick = { if (!controls.isLoadingForPlay) onPlayAll() },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(56.dp)
                .pointerHoverIcon(if (controls.isLoadingForPlay) PointerIcon.Default else PointerIcon.Hand)
        ) {
            if (controls.isLoadingForPlay) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(28.dp))
            }
        }

        IconButton(
            onClick = { if (!controls.isLoadingForPlay) onShuffle() },
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .pointerHoverIcon(PointerIcon.Hand)
        ) {
            Icon(Icons.Default.Shuffle, null, tint = onSurfaceColor, modifier = Modifier.size(20.dp))
        }

        IconButton(
            onClick = {
                if (isFullyDownloaded) {
                    showDeleteDialog()
                } else if (!isDownloading) {
                    downloadViewModel.downloadAll(songs)
                }
            },
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .pointerHoverIcon(PointerIcon.Hand)
        ) {
            if (isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    if (isFullyDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                    null,
                    tint = if (isFullyDownloaded) MaterialTheme.colorScheme.primary else onSurfaceColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}