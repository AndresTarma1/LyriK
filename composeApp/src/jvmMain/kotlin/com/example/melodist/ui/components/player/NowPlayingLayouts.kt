package com.example.melodist.ui.components.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Explicit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.melodist.navigation.Route
import com.example.melodist.models.MediaMetadata
import com.example.melodist.ui.components.*
import com.example.melodist.ui.components.background.BlurredImageBackground
import com.example.melodist.ui.components.skeletons.AnimatedEqualizer
import com.example.melodist.utils.upscaleThumbnailUrl
import com.example.melodist.ui.components.song.DownloadIndicator
import com.example.melodist.ui.components.song.AddToPlaylistDialog
import com.example.melodist.ui.helpers.rememberSongDownloadState
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.utils.LocalSnackbarScope
import com.example.melodist.utils.LocalUserPreferences
import com.example.melodist.utils.isWideThumbnail
import com.example.melodist.viewmodels.PlayerUiState
import com.example.melodist.viewmodels.QueueSource
import com.example.melodist.utils.LocalPlaylistsViewModel
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.Album
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.melodist.utils.LocalSnackbarHostState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.time.Duration.Companion.milliseconds

import androidx.compose.material.icons.filled.PlayArrow // ¡Asegúrate de importar esto!
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import com.example.melodist.ui.components.context.SongContextMenuPopup
import com.example.melodist.ui.components.layout.AppVerticalScrollbar
import io.github.aakira.napier.Napier
import org.jetbrains.jewel.foundation.modifier.onHover
import lyrik.composeapp.generated.resources.Res
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** The three views available in the right-hand panel of the expanded Now Playing screen. */
enum class NowPlayingTab { LYRICS, QUEUE, INFO }

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun NowPlayingLayout(
    state: PlayerUiState,
    song: MediaMetadata,
    onCollapse: () -> Unit,
    onNavigate: ((Route) -> Unit)? = null,
    selectedTab: NowPlayingTab = NowPlayingTab.QUEUE,
    onTabSelected: (NowPlayingTab) -> Unit = {},
    lyrics: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val playerViewModel = LocalPlayerViewModel.current
    val highRes by playerViewModel.highResCoverArt.collectAsState(false)
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    val preferencesRepo = LocalUserPreferences.current
    val equalizerBands by preferencesRepo.equalizerBands.collectAsState(initial = List(10) { 0f })

    BlurredImageBackground(
        imageUrl = song.thumbnailUrl,
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        darkOverlayAlpha = 0.62f,
        gradientFraction = 0.52f
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            val isCompact = maxWidth < 900.dp || maxHeight < 560.dp

            // --- Capa de Contenido Principal: portada a la izquierda, tabs a la derecha ---
            Box(modifier = Modifier.fillMaxSize()) {
                if (isCompact) {
                    CompactNowPlayingLayout(
                        song = song,
                        highRes = highRes,
                        state = state,
                        lyrics = lyrics,
                        selectedTab = selectedTab,
                        onTabSelected = onTabSelected,
                        onNavigate = onNavigate,
                        onCollapse = onCollapse,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                } else {
                    ExpandedNowPlayingLayout(
                        song = song,
                        highRes = highRes,
                        state = state,
                        lyrics = lyrics,
                        selectedTab = selectedTab,
                        onTabSelected = onTabSelected,
                        onNavigate = onNavigate,
                        onCollapse = onCollapse,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
            }

            // --- Capa de Acciones Globales (Siempre accesible arriba a la derecha) ---
            TopActionOverlay(
                showMenu = showMenu,
                onMenuToggle = { showMenu = it },
                onOpenEqualizer = { showEqualizer = true }
            )
        }
    }

    // --- Diálogo del Ecualizador ---
    if (showEqualizer) {
        AlertDialog(
            onDismissRequest = { showEqualizer = false },
            icon = { Icon(Icons.Rounded.GraphicEq, null) },
            title = { Text(stringResource(Res.string.equalizer_title)) },
            text = {
                EqualizerPanel(
                    bands = equalizerBands,
                    onBandsChange = { scope.launch { preferencesRepo.setEqualizerBands(it) } }
                )
            },
            confirmButton = {
                TextButton(onClick = { showEqualizer = false }) {
                    Text(stringResource(Res.string.close_equalizer))
                }
            }
        )
    }
}

// ==========================================
// COMPONENTES AUXILIARES EXTRAÍDOS
// ==========================================

/**
 * Spotify-style expanded layout: cover art + song info stay fixed on the left; the right side is
 * a tabbed panel (Letras / Cola / Información) that swaps content without disturbing the left side.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ExpandedNowPlayingLayout(
    song: MediaMetadata,
    highRes: Boolean,
    state: PlayerUiState,
    lyrics: String?,
    selectedTab: NowPlayingTab,
    onTabSelected: (NowPlayingTab) -> Unit,
    onNavigate: ((Route) -> Unit)?,
    onCollapse: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        Column(
            modifier = Modifier.weight(0.42f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CoverArt(
                url = song.thumbnailUrl,
                title = song.title,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .sizeIn(maxHeight = 380.dp, maxWidth = 380.dp)
                    .heroCoverElement(song.id, sharedTransitionScope, animatedVisibilityScope),
                highRes = highRes
            )
            Spacer(Modifier.height(18.dp))
            SongHeader(
                state = state,
                song = song,
                textAlign = TextAlign.Center,
                onNavigate = onNavigate,
                onCollapse = onCollapse,
                compact = false
            )
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .padding(vertical = 24.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
        )

        Column(modifier = Modifier.weight(0.58f).fillMaxHeight()) {
            NowPlayingTabRow(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                queueCount = state.queue.size,
                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
            )
            Box(modifier = Modifier.weight(1f)) {
                NowPlayingTabContent(
                    tab = selectedTab,
                    song = song,
                    state = state,
                    lyrics = lyrics,
                    lyricsTextStyle = MaterialTheme.typography.headlineSmall,
                    onNavigate = onNavigate,
                    onCollapse = onCollapse,
                )
            }
        }
    }
}

/** Narrow-window fallback: cover + info on top, the selected tab fills the rest below. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CompactNowPlayingLayout(
    song: MediaMetadata,
    highRes: Boolean,
    state: PlayerUiState,
    lyrics: String?,
    selectedTab: NowPlayingTab,
    onTabSelected: (NowPlayingTab) -> Unit,
    onNavigate: ((Route) -> Unit)?,
    onCollapse: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 48.dp), // espacio para el botón cerrar global
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverArt(
                url = song.thumbnailUrl,
                title = song.title,
                modifier = Modifier.size(80.dp)
                    .heroCoverElement(song.id, sharedTransitionScope, animatedVisibilityScope),
                highRes = highRes
            )
            SongHeader(
                state = state,
                song = song,
                textAlign = TextAlign.Start,
                onNavigate = onNavigate,
                onCollapse = onCollapse,
                compact = true
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
        NowPlayingTabRow(selectedTab = selectedTab, onTabSelected = onTabSelected, queueCount = state.queue.size)
        Box(modifier = Modifier.weight(1f)) {
            NowPlayingTabContent(
                tab = selectedTab,
                song = song,
                state = state,
                lyrics = lyrics,
                lyricsTextStyle = MaterialTheme.typography.bodyLarge,
                onNavigate = onNavigate,
                onCollapse = onCollapse,
            )
        }
    }
}

/** Spotify-style pill "button tabs" for switching the right-hand panel. */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun tabIcon(tab: NowPlayingTab) = when (tab) {
    NowPlayingTab.LYRICS -> Icons.Rounded.Lyrics
    NowPlayingTab.QUEUE -> Icons.AutoMirrored.Filled.QueueMusic
    NowPlayingTab.INFO -> Icons.Rounded.Info
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NowPlayingTabRow(
    selectedTab: NowPlayingTab,
    onTabSelected: (NowPlayingTab) -> Unit,
    queueCount: Int,
    modifier: Modifier = Modifier,
) {
    val lyricsLabel = stringResource(Res.string.tab_lyrics)
    val queueLabel = stringResource(Res.string.tab_queue) + if (queueCount > 0) " ($queueCount)" else ""
    val infoLabel = stringResource(Res.string.tab_info)

    ButtonGroup(
        overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) },
        modifier = modifier,
    ) {
        NowPlayingTab.entries.forEach { tab ->
            toggleableItem(
                checked = tab == selectedTab,
                onCheckedChange = { onTabSelected(tab) },
                label = when (tab) {
                    NowPlayingTab.LYRICS -> lyricsLabel
                    NowPlayingTab.QUEUE -> queueLabel
                    NowPlayingTab.INFO -> infoLabel
                },
                icon = { Icon(tabIcon(tab), contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun BoxScope.NowPlayingTabContent(
    tab: NowPlayingTab,
    song: MediaMetadata,
    state: PlayerUiState,
    lyrics: String?,
    lyricsTextStyle: androidx.compose.ui.text.TextStyle,
    onNavigate: ((Route) -> Unit)?,
    onCollapse: () -> Unit,
) {
    when (tab) {
        NowPlayingTab.LYRICS -> LyricsContent(lyrics = lyrics, textAlign = TextAlign.Start, style = lyricsTextStyle)
        NowPlayingTab.QUEUE -> PlaybackQueuePanel(
            state = state,
            onDismiss = {},
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            showCloseButton = false,
        )
        NowPlayingTab.INFO -> SongInfoContent(song = song, state = state, onNavigate = onNavigate, onCollapse = onCollapse)
    }
}

@Composable
private fun SongInfoContent(
    song: MediaMetadata,
    state: PlayerUiState,
    onNavigate: ((Route) -> Unit)?,
    onCollapse: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(top = 8.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        song.album?.let { album ->
            InfoRow(
                icon = Icons.Rounded.Album,
                label = album.title,
                onClick = { onCollapse(); onNavigate?.invoke(Route.Album(album.id)) }
            )
        }
        if (song.duration > 0) {
            InfoRow(
                icon = Icons.Rounded.Timer,
                label = "${stringResource(Res.string.info_duration)} · ${formatPlayerTimeValue(song.duration * 1000L)}"
            )
        }
        state.queueSource?.let { source ->
            val originLabel = when (source) {
                is QueueSource.Album -> stringResource(Res.string.from_album, source.title)
                is QueueSource.Playlist -> stringResource(Res.string.from_playlist, source.title)
                is QueueSource.Single -> stringResource(Res.string.song_radio)
                QueueSource.Custom -> stringResource(Res.string.custom_queue)
            }
            InfoRow(icon = Icons.AutoMirrored.Filled.QueueMusic, label = originLabel)
        }
        if (song.liked) {
            InfoRow(icon = Icons.Filled.Favorite, label = stringResource(Res.string.info_liked), tint = MaterialTheme.colorScheme.primary)
        }
        if (song.isDownloaded) {
            InfoRow(icon = Icons.Default.Download, label = stringResource(Res.string.info_downloaded))
        }
        if (song.explicit) {
            InfoRow(icon = Icons.Rounded.Explicit, label = stringResource(Res.string.info_explicit))
        }
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BoxScope.LyricsContent(
    lyrics: String?,
    textAlign: TextAlign,
    style: androidx.compose.ui.text.TextStyle
) {
    val playerViewModel = LocalPlayerViewModel.current
    val synced by playerViewModel.syncedLyrics.collectAsState()
    val progress by playerViewModel.progressState.collectAsState()
    val syncedLines = synced

    when {
        syncedLines != null && syncedLines.isNotEmpty() -> {
            SyncedLyricsView(
                lines = syncedLines,
                positionMs = progress.positionMs,
                onSeek = { playerViewModel.seekTo(it) },
                modifier = Modifier.fillMaxSize(),
                textAlign = textAlign == TextAlign.Start,
            )
        }
        lyrics == null -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                LoadingIndicator()
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(Res.string.lyrics_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
        lyrics.isBlank() -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Rounded.Lyrics,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(Res.string.lyrics_not_found),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        else -> {
            val scrollState = rememberScrollState()
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(end = 16.dp)
                ) {
                    Text(
                        text = lyrics,
                        modifier = Modifier.fillMaxWidth(),
                        style = style,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        lineHeight = style.lineHeight * 1.2f,
                        textAlign = textAlign,
                    )
                    Spacer(Modifier.height(64.dp))
                }
                AppVerticalScrollbar(
                    state = scrollState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(8.dp)
                )
            }
        }
    }
}

@Composable
private fun BoxScope.TopActionOverlay(
    showMenu: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    onOpenEqualizer: () -> Unit
) {
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .pointerHoverIcon(PointerIcon.Hand)
    ) {
        IconButton(
            onClick = { onMenuToggle(true) },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(Res.string.more_options),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { onMenuToggle(false) },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.equalizer_menu)) },
                onClick = { onMenuToggle(false); onOpenEqualizer() },
                leadingIcon = { Icon(Icons.Rounded.GraphicEq, null) }
            )
        }
    }
}


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
                                onClick = {
                                    showMenu = false
                                    showAddQueueDialog = true
                                },
                                text = { Text(stringResource(Res.string.add_to_playlist)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.PlaylistAdd,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                onClick = {
                                    showMenu = false
                                    downloadViewModel.downloadAll(queueSongs)
                                    scope.launch {
                                        snackbar.showSnackbar(getString(Res.string.queue_download_added, queueSongs.size))
                                    }
                                },
                                text = { Text(stringResource(Res.string.download_queue)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                onClick = {
                                    showMenu = false
                                    showSaveQueueDialog = true
                                },
                                text = { Text(stringResource(Res.string.save_as_playlist)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Save,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                onClick = {
                                    showMenu = false
                                    coroutineScope.launch {
                                        preferencesRepo.setQueueLocked(!queueLocked)
                                    }
                                },
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
                            modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand) // Botón más grande
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))


            Box(
                Modifier.fillMaxSize()
            ) {
                if (state.queue.isEmpty()) {
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
fun CoverArt(url: String?, title: String, modifier: Modifier = Modifier, highRes: Boolean) {
    val imageUrl = if (highRes) upscaleThumbnailUrl(url, 1080) else url
    val ratio = if (isWideThumbnail(url)) 16f / 9f else 1f
    val corner = 20.dp

    Card(
        modifier = modifier.aspectRatio(ratio),
        shape = RoundedCornerShape(corner),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        MelodistImage(
            url = imageUrl,
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
            placeholderType = PlaceholderType.SONG,
            contentScale = ContentScale.Crop

        )
    }
}

@Composable
fun SongHeader(
    state: PlayerUiState,
    song: MediaMetadata,
    textAlign: TextAlign,
    onNavigate: ((Route) -> Unit)? = null,
    onCollapse: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    Column(
        horizontalAlignment = when (textAlign) {
            TextAlign.Start -> Alignment.Start
            else -> Alignment.CenterHorizontally
        },
        modifier = Modifier.fillMaxWidth(if (compact) 0.92f else 0.84f)
    ) {
        state.queueSource?.let { source ->
            val label = when (source) {
                is QueueSource.Album -> stringResource(Res.string.from_album, source.title)
                is QueueSource.Playlist -> stringResource(Res.string.from_playlist, source.title)
                is QueueSource.Single -> stringResource(Res.string.song_radio)
                QueueSource.Custom -> stringResource(Res.string.custom_queue)
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.62f),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).basicMarquee()
                )
            }
        }

        Text(
            text = song.title,
            style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth().basicMarquee()
        )

        Spacer(Modifier.height(if (compact) 4.dp else 6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (textAlign == TextAlign.Start) Arrangement.Start else Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            song.artists.forEachIndexed { i, artist ->
                val hasId = artist.id != null
                Text(
                    text = artist.name,
                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (hasId) Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            onCollapse?.invoke()
                            onNavigate?.invoke(Route.Artist(artist.id!!))
                        }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 2.dp)
                    else Modifier.padding(horizontal = 2.dp)
                )
                if (i < song.artists.size - 1) {
                    Text(
                        text = ", ",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            val playerViewModel = LocalPlayerViewModel.current
            IconButton(
                onClick = { playerViewModel.toggleLike() },
                modifier = Modifier.size(if (compact) 20.dp else 24.dp).pointerHoverIcon(PointerIcon.Hand)
            ) {
                Icon(
                    imageVector = if (song.liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(Res.string.mp_like),
                    tint = if (song.liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(if (compact) 14.dp else 16.dp)
                )
            }
        }

        song.album?.let { album ->
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = if (textAlign == TextAlign.Start) Arrangement.Start else Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Rounded.Album,
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 12.dp else 14.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = album.title,
                    style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            onCollapse?.invoke()
                            onNavigate?.invoke(Route.Album(album.id))
                        }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 2.dp)
                )
            }
        }
    }
}


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun QueueItem(
    song: MediaMetadata,
    isCurrent: Boolean,
    isDragging: Boolean = false,
    dragModifier: Modifier = Modifier,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val downloadViewModel = LocalDownloadViewModel.current
    val downloadState by rememberSongDownloadState(song.id, downloadViewModel)

    var isHovered by remember { mutableStateOf(false) }


    val currentBg = if (isCurrent && !isDragging)
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)
    else Color.Transparent

    var showMenu by remember{ mutableStateOf(false)};
    Surface(
        color = currentBg,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(dragModifier)
            .clickable(onClick = onClick)
            .onPointerEvent(PointerEventType.Press) { event ->
                when {
                    event.buttons.isSecondaryPressed -> {
                        showMenu = true
                    }
                }
            }
            .onHover{ isHovered = it }
            .pointerHoverIcon(if (isDragging) PointerIcon.Crosshair else PointerIcon.Hand),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            Box {
                // Thumbnail
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .then(
                            if (isCurrent) Modifier.border(
                                2.dp,
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(8.dp)
                            ) else Modifier
                        )
                ) {
                    MelodistImage(
                        url = song.thumbnailUrl,
                        contentDescription = song.title,
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(8.dp),
                        placeholderType = PlaceholderType.SONG,
                        iconSize = 22.dp,
                        contentScale = ContentScale.Crop,
                    )

                    if (isCurrent) {
                        Box(
                            modifier = Modifier.matchParentSize().background(
                                Color.Black.copy(alpha = 0.35f), shape = RoundedCornerShape(8.dp)
                            )
                        ) {
                            AnimatedEqualizer(
                                isPlaying = true,
                                modifier = Modifier.size(20.dp).align(Alignment.Center)
                            )
                        }
                    } else if (isHovered && !isDragging) {
                        Box(
                            modifier = Modifier.matchParentSize().background(
                                Color.Black.copy(alpha = 0.3f), shape = RoundedCornerShape(8.dp)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = stringResource(Res.string.play_item),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp).align(Alignment.Center)
                            )
                        }
                    }
                }
            }

            // Título + artistas
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artists.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            DownloadIndicator(state = downloadState)

            if (isHovered && !isDragging) {
                IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Default.PlaylistRemove,
                        stringResource(Res.string.remove_from_queue),
                        modifier = Modifier.size(19.dp)
                    )
                }
            } else {

                if (song.duration > 0) {
                    Text(
                        formatPlayerTimeValue(song.duration * 1000L),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

            }
        }

        SongContextMenuPopup(
            expanded = showMenu,
            onDismiss = { showMenu = false },
            song = song.toSongItem(),
        )
    }
}

private fun MediaMetadata.toSongItem(): SongItem = SongItem(
    id = id,
    title = title,
    artists = artists.map { Artist(name = it.name, id = it.id) },
    album = album?.let { Album(name = it.title, id = it.id) },
    duration = duration.takeIf { it > 0 },
    thumbnail = thumbnailUrl.orEmpty(),
    explicit = explicit
)
