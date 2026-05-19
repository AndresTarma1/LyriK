package com.example.melodist.ui.screens.playlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.melodist.ui.components.LoadingMoreSongsItem
import com.example.melodist.ui.components.MelodistImage
import com.example.melodist.ui.components.PlaceholderType
import com.example.melodist.ui.components.dialogs.DownloadConfirmationDialog
import com.example.melodist.ui.components.layout.AppVerticalScrollbar
import com.example.melodist.ui.screens.PlaylistActions
import com.example.melodist.ui.screens.PlaylistScreenState
import com.example.melodist.ui.utils.circleAwareShape
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.utils.LocalPlayerViewModel
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.PlaylistPage

@Composable
internal fun PlaylistLayout(
    playlistPage: PlaylistPage,
    state: PlaylistScreenState,
    actions: PlaylistActions
) {
    val playerViewModel = LocalPlayerViewModel.current
    val downloadViewModel = LocalDownloadViewModel.current

    val songIds = remember(state.songs) { state.songs.map { it.id } }

    val isAnyDownloading by remember(songIds, downloadViewModel) {
        downloadViewModel.isAnyDownloadingFlow(songIds)
    }.collectAsState(initial = false)

    val isFullyDownloaded by remember(songIds, downloadViewModel) {
        downloadViewModel.isFullyDownloadedFlow(songIds)
    }.collectAsState(initial = false)

    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedSongIds by remember(state.songs) { mutableStateOf<Set<String>>(emptySet()) }
    val selectedSongs = remember(state.songs, selectedSongIds) {
        state.songs.filter { it.id in selectedSongIds }
    }

    if (showDeleteDialog) {
        DownloadConfirmationDialog(
            onConfirm = { downloadViewModel.removeDownloads(songIds) },
            onDismiss = { showDeleteDialog = false }
        )
    }

    val onDownloadClick = {
        val isDownloadsPlaylist = playlistPage.playlist.id == "LOCAL_DOWNLOADS"
        if (isFullyDownloaded && !isDownloadsPlaylist) showDeleteDialog = true
        else if (!isAnyDownloading) actions.onDownloadPlaylist()
    }

    val controls = PlaylistInfoPanelControls(
        isSaved = state.isSaved,
        isSaving = state.isSaving,
        isLoadingForPlay = state.isLoadingForPlay,
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth < 980.dp) {
            PlaylistCompactLayout(
                playlistPage = playlistPage,
                state = state,
                actions = actions,
                controls = controls,
                selectedSongIds = selectedSongIds,
                selectedSongs = selectedSongs,
                isAnyDownloading = { isAnyDownloading },
                isFullyDownloaded = { isFullyDownloaded },
                onDownloadClick = onDownloadClick,
                onSelectionChange = { id, selected ->
                    selectedSongIds = if (selected) selectedSongIds + id else selectedSongIds - id
                },
                onClearSelection = { selectedSongIds = emptySet() },
                onSongPlay = { index ->
                    playerViewModel.playPlaylist(
                        state.songs, index,
                        playlistPage.playlist.id,
                        playlistPage.playlist.title
                    )
                }
            )
        } else {
            PlaylistWideLayout(
                playlistPage = playlistPage,
                state = state,
                actions = actions,
                controls = controls,
                selectedSongIds = selectedSongIds,
                selectedSongs = selectedSongs,
                isAnyDownloading = { isAnyDownloading },
                isFullyDownloaded = { isFullyDownloaded },
                onDownloadClick = onDownloadClick,
                onSelectionChange = { id, selected ->
                    selectedSongIds = if (selected) selectedSongIds + id else selectedSongIds - id
                },
                onClearSelection = { selectedSongIds = emptySet() },
                onSongPlay = { index ->
                    playerViewModel.playPlaylist(
                        state.songs, index,
                        playlistPage.playlist.id,
                        playlistPage.playlist.title
                    )
                }
            )
        }
    }
}

@Composable
internal fun PlaylistWideLayout(
    playlistPage: PlaylistPage,
    state: PlaylistScreenState,
    actions: PlaylistActions,
    controls: PlaylistInfoPanelControls,
    selectedSongIds: Set<String>,
    selectedSongs: List<SongItem>,
    isAnyDownloading: () -> Boolean,
    isFullyDownloaded: () -> Boolean,
    onDownloadClick: () -> Unit,
    onSelectionChange: (String, Boolean) -> Unit,
    onClearSelection: () -> Unit,
    onSongPlay: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 48.dp, end = 48.dp, top = 32.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(40.dp)
    ) {
        // Panel lateral fijo con scroll propio
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlaylistInfoPanel(
                playlistPage = playlistPage,
                coverSize = 240.dp,
                controls = controls,
                actions = actions,
                isDownloadingAny = isAnyDownloading,
                isFullyDownloaded = isFullyDownloaded,
                onDownloadClick = onDownloadClick
            )
        }

        // Lista de canciones
        PlaylistSongList(
            modifier = Modifier.weight(1f),
            state = state,
            playlistPage = playlistPage,
            selectedSongIds = selectedSongIds,
            selectedSongs = selectedSongs,
            actions = actions,
            onSelectionChange = onSelectionChange,
            onClearSelection = onClearSelection,
            onSongPlay = onSongPlay
        )
    }
}

@Composable
internal fun PlaylistCompactLayout(
    playlistPage: PlaylistPage,
    state: PlaylistScreenState,
    actions: PlaylistActions,
    controls: PlaylistInfoPanelControls,
    selectedSongIds: Set<String>,
    selectedSongs: List<SongItem>,
    isAnyDownloading: () -> Boolean,
    isFullyDownloaded: () -> Boolean,
    onDownloadClick: () -> Unit,
    onSelectionChange: (String, Boolean) -> Unit,
    onClearSelection: () -> Unit,
    onSongPlay: (Int) -> Unit,
) {
    Box {

        val lazyColumnState = rememberLazyListState()

        val showStickHeader by remember {
            derivedStateOf { lazyColumnState.firstVisibleItemIndex > 0 }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
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
                            text = playlistPage.playlist.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = onDownloadClick,
                            enabled = !isAnyDownloading() || isFullyDownloaded()
                        ) {
                            Icon(
                                imageVector = if (isFullyDownloaded()) Icons.Default.Delete else Icons.Default.Download,
                                contentDescription = if (isFullyDownloaded()) "Eliminar descargas" else "Descargar playlist"
                            )
                        }

                        IconButton(onClick = actions.onPlay) {
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
                        PlaylistInfoPanel(
                            playlistPage = playlistPage,
                            coverSize = 190.dp,
                            controls = controls,
                            actions = actions,
                            isDownloadingAny = isAnyDownloading,
                            isFullyDownloaded = isFullyDownloaded,
                            onDownloadClick = onDownloadClick
                        )
                        Spacer(Modifier.height(12.dp))
                    }

            }

            itemsIndexed(
                items = state.songs,
                key = { index, song -> "${song.id}_$index" }
            ) { index, song ->
                SongListItem(
                    song = song,
                    onPlay = {
                        if (selectedSongIds.isNotEmpty()) {
                            onSelectionChange(song.id, song.id !in selectedSongIds)
                        } else {
                            onSongPlay(index)
                        }
                    },
                    isSelected = song.id in selectedSongIds,
                    selectionMode = selectedSongIds.isNotEmpty(),
                    onSelectionChange = { selected -> onSelectionChange(song.id, selected) },
                    isLocalPlaylist = actions.isLocalPlaylist,
                    onRemoveFromPlaylist = actions.onRemoveSongFromPlaylist,
                    modifier = Modifier.animateItem(
                        placementSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                )
            }

            if (state.hasMore) {
                item { LoadingMoreSongsItem(onLoadMore = actions.onLoadMore) }
            }

        }

        AppVerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd),
            state = lazyColumnState
        )

        MultiSongSelectionBar(
            selectedSongs = selectedSongs,
            isLocalPlaylist = actions.isLocalPlaylist,
            onClearSelection = onClearSelection,
            onRemoveFromPlaylist = actions.onRemoveSongFromPlaylist,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun PlaylistSongList(
    modifier: Modifier,
    state: PlaylistScreenState,
    playlistPage: PlaylistPage,
    selectedSongIds: Set<String>,
    selectedSongs: List<SongItem>,
    actions: PlaylistActions,
    onSelectionChange: (String, Boolean) -> Unit,
    onClearSelection: () -> Unit,
    onSongPlay: (Int) -> Unit,
) {
    Box(modifier = modifier) {
        val lazyListState = rememberLazyListState()

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 16.dp),
            contentPadding = PaddingValues(
                bottom = if (selectedSongIds.isNotEmpty()) 72.dp else 0.dp
            )
        ) {
            itemsIndexed(
                items = state.songs,
                key = { index, song -> "${song.id}_$index" }
            ) { index, song ->
                SongListItem(
                    song = song,
                    onPlay = {
                        if (selectedSongIds.isNotEmpty()) {
                            onSelectionChange(song.id, song.id !in selectedSongIds)
                        } else {
                            onSongPlay(index)
                        }
                    },
                    isSelected = song.id in selectedSongIds,
                    selectionMode = selectedSongIds.isNotEmpty(),
                    onSelectionChange = { selected -> onSelectionChange(song.id, selected) },
                    isLocalPlaylist = actions.isLocalPlaylist,
                    onRemoveFromPlaylist = actions.onRemoveSongFromPlaylist,
                    modifier = Modifier.animateItem(
                        placementSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                )
            }

            if (state.hasMore) {
                item { LoadingMoreSongsItem(onLoadMore = actions.onLoadMore) }
            }
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
            isLocalPlaylist = actions.isLocalPlaylist,
            onClearSelection = onClearSelection,
            onRemoveFromPlaylist = actions.onRemoveSongFromPlaylist,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

internal data class PlaylistInfoPanelControls(
    val isSaved: Boolean,
    val isSaving: Boolean,
    val isLoadingForPlay: Boolean,
)

@Composable
internal fun PlaylistInfoPanel(
    playlistPage: PlaylistPage,
    coverSize: Dp,
    controls: PlaylistInfoPanelControls,
    actions: PlaylistActions,
    isDownloadingAny: () -> Boolean,
    isFullyDownloaded: () -> Boolean,
    onDownloadClick: () -> Unit
) {
    // Author chip
    playlistPage.playlist.author?.let { author ->
        AssistChip(
            onClick = {},
            label = {
                Text(
                    text = author.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
            },
            leadingIcon = {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(circleAwareShape())
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    MelodistImage(
                        url = null,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        shape = circleAwareShape(),
                        contentScale = ContentScale.Crop,
                        placeholderType = PlaceholderType.ARTIST,
                        iconSize = 12.dp
                    )
                }
            },
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
        )
        Spacer(Modifier.height(16.dp))
    }

    // Cover
    Card(
        modifier = Modifier
            .size(coverSize)
            .shadow(elevation = 20.dp, shape = RoundedCornerShape(16.dp), clip = false),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        MelodistImage(
            url = playlistPage.playlist.thumbnail,
            contentDescription = playlistPage.playlist.title,
            modifier = Modifier.fillMaxSize(),
            placeholderType = PlaceholderType.PLAYLIST,
            contentScale = ContentScale.Crop,
            iconSize = coverSize * 0.35f,
            isLowRes = false  // ✅ Alta resolución solo en pantalla de detalle
        )
    }

    Spacer(Modifier.height(20.dp))

    // Título
    Text(
        text = playlistPage.playlist.title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurface
    )

    Spacer(Modifier.height(4.dp))

    // Meta
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Playlist",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val songCountText = playlistPage.playlist.songCountText
        if (!songCountText.isNullOrBlank()) {
            Text(
                text = " • $songCountText",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(Modifier.height(24.dp))

    // Botones de control
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Grupo Izquierdo
        PlaylistActionButton(
            onClick = { actions.onToggleSave() },
            icon = if (controls.isSaved) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            isActive = controls.isSaved,
            isLoading = controls.isSaving
        )

        Spacer(Modifier.width(12.dp))

        // Descargar
        DownloadAllButton(
            isDownloadingAny = isDownloadingAny,
            isFullyDownloaded = isFullyDownloaded,
            onClick = onDownloadClick
        )

        Spacer(Modifier.width(10.dp)) // Espacio mayor para el Play

        FloatingActionButton(
            onClick = { if (!controls.isLoadingForPlay) actions.onPlay() },
            shape = circleAwareShape(),
            containerColor = MaterialTheme.colorScheme.primary,
            elevation = FloatingActionButtonDefaults.elevation(8.dp, 12.dp),
            modifier = Modifier
                .size(64.dp) // Un pelín más grande para destacar
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

        // Play — acción principal, más grande
        Spacer(Modifier.width(10.dp))

        // SHUFFLE
        PlaylistActionButton(
            onClick = { actions.onShuffle() },
            icon = Icons.Default.Shuffle,
            isActive = false, // El shuffle suele ser un trigger, no un estado persistente aquí
            isLoading = false
        )

    }
}

@Composable
fun PlaylistActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    isActive: Boolean,
    isLoading: Boolean,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    // Animación suave de color de fondo y contenido
    val containerColor by animateColorAsState(
        targetValue = if (isActive) activeColor.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "color"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isActive) activeColor
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tint"
    )

    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp), // Un poco más grandes para mejor touch target
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = circleAwareShape() // Compatible con OpenGL
    ) {
        Crossfade(targetState = isLoading, label = "icon_state") { loading ->
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = contentColor
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DownloadAllButton(
    isDownloadingAny: () -> Boolean,
    isFullyDownloaded: () -> Boolean,
    onClick: () -> Unit
) {
    val downloading = isDownloadingAny()
    val fullyDownloaded = isFullyDownloaded()

    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .pointerHoverIcon(PointerIcon.Hand),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (fullyDownloaded)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        if (downloading) {
            CircularWavyProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Icon(
                imageVector = if (fullyDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                contentDescription = null,
                tint = if (fullyDownloaded) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}