package com.example.melodist.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.melodist.navigation.Route
import com.example.melodist.ui.components.*
import com.example.melodist.ui.components.context.CollectionContextMenuPopup
import com.example.melodist.ui.components.context.SongContextMenuPopup
import com.example.melodist.ui.components.layout.AppVerticalScrollbar
import com.example.melodist.ui.components.layout.HorizontalScrollableRow
import com.example.melodist.ui.components.song.DownloadIndicator
import com.example.melodist.ui.helpers.rememberSongDownloadState
import com.example.melodist.ui.utils.circleAwareShape
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.viewmodels.ArtistState
import com.example.melodist.viewmodels.ArtistViewModel
import com.metrolist.innertube.models.*
import com.metrolist.innertube.pages.ArtistPage
import com.metrolist.innertube.pages.ArtistSection
import lyrik.composeapp.generated.resources.Res
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun ArtistScreenRoute(
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
    viewModel: ArtistViewModel,
) {
    val playerViewModel = LocalPlayerViewModel.current
    val uiState by viewModel.uiState.collectAsState()
    val isSaved by viewModel.isSaved.collectAsState()
    val success = uiState as? ArtistState.Success

    ArtistScreen(
        uiState = uiState,
        onNavigate = onNavigate,
        onBack = onBack,
        isSaved = isSaved,
        actions = remember(viewModel, playerViewModel, success) {
            ArtistScreenActions(
                onToggleSave = { viewModel.toggleSave() },
                onPlayArtist = {
                    val endpoint = success?.artistPage?.artist?.playEndpoint
                        ?: return@ArtistScreenActions
                    playerViewModel.playEndpoint(endpoint)
                },
                onShuffleArtist = {
                    val endpoint = success?.artistPage?.artist?.shuffleEndpoint ?: return@ArtistScreenActions
                    playerViewModel.playEndpoint(endpoint)
                },
                onRadioArtist = {
                    val endpoint = success?.artistPage?.artist?.radioEndpoint
                        ?: return@ArtistScreenActions
                    playerViewModel.playEndpoint(endpoint)
                }
            )
        }
    )
}

data class ArtistScreenActions(
    val onToggleSave: () -> Unit,
    val onPlayArtist: () -> Unit,
    val onShuffleArtist: () -> Unit,
    val onRadioArtist: () -> Unit = {},
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ArtistScreen(
    uiState: ArtistState,
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
    isSaved: Boolean = false,
    actions: ArtistScreenActions,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent {
                if (it.key == Key.Escape && it.type == KeyEventType.KeyUp) {
                    onBack()
                    true
                } else {
                    false
                }
            }
    ) {
        when (uiState) {
            is ArtistState.Loading -> ArtistScreenSkeleton()
            is ArtistState.Success -> ArtistScreenContent(uiState.artistPage, onNavigate, isSaved, actions)
            is ArtistState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(uiState.message, color = MaterialTheme.colorScheme.error)
            }
        }

        // Botón atrás flotante
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(12.dp)
                .align(Alignment.TopStart)
                .size(36.dp)
                .clip(circleAwareShape())
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back),
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// Contenido principal
@Composable
private fun ArtistScreenContent(
    artistPage: ArtistPage,
    onNavigate: (Route) -> Unit,
    isSaved: Boolean,
    actions: ArtistScreenActions
) {
    val lazyListState = rememberLazyListState()
    val surface = MaterialTheme.colorScheme.surface

    Box(modifier = Modifier.fillMaxSize()) {

        Box(modifier = Modifier.fillMaxSize().background(surface))

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "banner") {
                ArtistBanner(
                    artistPage = artistPage,
                    isSaved = isSaved,
                    actions = actions,
                    surfaceColor = surface
                )
            }

            itemsIndexed(
                artistPage.sections,
            ) { index , section ->

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 32.dp,
                            vertical = 16.dp
                        )
                ) {

                    Text(
                        text = section.title
                    )

                    if (index == 0) {
                        Column {
                            section.items.forEach { item ->
                                ArtistSectionListItem(
                                    item = item,
                                    onNavigate = onNavigate
                                )
                            }
                        }

                    } else {
                        HorizontalScrollableRow(
                            state = rememberLazyListState()
                        ) {

                            items(
                                section.items,
                                key = { it.id }
                            ) {
                                ArtistSectionGridItem(
                                    item = it,
                                    onNavigate = onNavigate
                                )
                            }
                        }
                    }
                }
            }
        }

        AppVerticalScrollbar(
            state = lazyListState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
        )
    }
}

// BANNER — imagen con gradientes multicapa al estilo YouTube Music
@Composable
private fun ArtistBanner(
    artistPage: ArtistPage,
    isSaved: Boolean,
    actions: ArtistScreenActions,
    surfaceColor: Color
) {
    var descExpanded by remember { mutableStateOf(false) }
    val hasPlayable = artistPage.sections.any { s -> s.items.any { it is SongItem } }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(520.dp)
    ) {
        MelodistImage(
            url = artistPage.artist.thumbnail,
            contentDescription = artistPage.artist.title,
            modifier = Modifier.fillMaxSize(),
            shape = RectangleShape,
            placeholderType = PlaceholderType.ARTIST,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.28f to Color.Transparent,
                            0.52f to surfaceColor.copy(alpha = 0.20f),
                            0.70f to surfaceColor.copy(alpha = 0.62f),
                            0.84f to surfaceColor.copy(alpha = 0.88f),
                            1.00f to surfaceColor
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Black.copy(alpha = 0.55f),
                            1.00f to Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 32.dp)
        ) {
            // Nombre
            Text(
                text = artistPage.artist.title,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1).sp
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(6.dp))

            // Oyentes mensuales
            artistPage.monthlyListenerCount?.let { listeners ->
                Text(
                    text = listeners,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.75f)
                )
                Spacer(Modifier.height(8.dp))
            }

            // Descripción expandible
            artistPage.description?.let { desc ->
                if (desc.isNotBlank()) {
                    Column(modifier = Modifier.fillMaxWidth(0.55f)) {
                        AnimatedContent(
                            targetState = descExpanded,
                            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                            label = "descAnim"
                        ) { expanded ->
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.72f),
                                maxLines = if (expanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Solo muestra "Más / Menos" si el texto es largo
                        if (desc.length > 120) {
                            TextButton(
                                onClick = { descExpanded = !descExpanded },
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text(
                                    text = if (descExpanded) stringResource(Res.string.less) else stringResource(Res.string.more),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }

            // Botones de acción
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Aleatorio — botón blanco sólido
                Button(
                    onClick = { if (hasPlayable) actions.onShuffleArtist() },
                    enabled = hasPlayable,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        disabledContainerColor = Color.White.copy(alpha = 0.3f),
                        disabledContentColor = Color.White.copy(alpha = 0.5f)
                    ),
                    shape = circleAwareShape(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.shuffle), fontWeight = FontWeight.SemiBold)
                }

                // Radio — botón blanco sólido
                Button(
                    onClick = { if (hasPlayable) actions.onRadioArtist() },
                    enabled = hasPlayable,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        disabledContainerColor = Color.White.copy(alpha = 0.3f),
                        disabledContentColor = Color.White.copy(alpha = 0.5f)
                    ),
                    shape = circleAwareShape(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Rounded.Radio, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.radio_text), fontWeight = FontWeight.SemiBold)
                }

                // Suscribirse — borde blanco semitransparente (como YouTube Music)
                val subscribedText = stringResource(Res.string.subscribed)
                val subscribeText = stringResource(Res.string.subscribe_text)
                val subLabel = buildString {
                    append(if (isSaved) subscribedText else subscribeText)
                    artistPage.subscriberCountText?.let { append("  $it") }
                }
                OutlinedButton(
                    onClick = actions.onToggleSave,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isSaved) Color.White else Color.White,
                        containerColor = if (isSaved) Color.White.copy(alpha = 0.15f) else Color.Transparent
                    ),
                    border = BorderStroke(
                        width = 1.5.dp,
                        color = Color.White.copy(alpha = if (isSaved) 0.6f else 0.85f)
                    ),
                    shape = circleAwareShape(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    if (isSaved) {
                        Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(subLabel, fontWeight = FontWeight.Medium)
                }

                // Más opciones
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(circleAwareShape())
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable { /* TODO */ }
                        .pointerHoverIcon(PointerIcon.Hand),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.MoreVert, stringResource(Res.string.more_options), tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ArtistSectionListItem(
    item: YTItem,
    onNavigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerViewModel = LocalPlayerViewModel.current

    YoutubeListItem(
        item = item,
        source = ItemContentSource.YOUTUBE,
        onItemClick = { clicked ->
            when (clicked) {
                is AlbumItem -> onNavigate(Route.Album(clicked.browseId))
                is PlaylistItem -> onNavigate(Route.Playlist(clicked.id))
                is ArtistItem -> onNavigate(Route.Artist(clicked.id))
                is SongItem -> playerViewModel.playSingle(clicked)
                else -> {}
            }
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ArtistSectionGridItem(
    item: YTItem,
    onNavigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerViewModel = LocalPlayerViewModel.current
    val downloadViewModel = LocalDownloadViewModel.current
    val isArtist = item is ArtistItem

    var showMenu by remember { mutableStateOf(false) }

    val downloadState by if (item is SongItem)
        rememberSongDownloadState(item.id, downloadViewModel)
    else
        remember { mutableStateOf(null) }

    val onClick: (YTItem) -> Unit = { clicked ->
        when (clicked) {
            is AlbumItem -> onNavigate(Route.Album(clicked.browseId))
            is PlaylistItem -> onNavigate(Route.Playlist(clicked.id))
            is ArtistItem -> onNavigate(Route.Artist(clicked.id))
            is SongItem -> playerViewModel.playSingle(clicked)
            else -> {}
        }
    }

    YouTubeGridItem(
        item = item,
        onClick = onClick,
        imageShape = if (isArtist) circleAwareShape() else RoundedCornerShape(10.dp),
        alignment = if (isArtist) Alignment.CenterHorizontally else Alignment.Start,
        titleAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
        placeholderType = when (item) {
            is ArtistItem -> PlaceholderType.ARTIST
            is AlbumItem -> PlaceholderType.ALBUM
            is PlaylistItem -> PlaceholderType.PLAYLIST
            else -> PlaceholderType.SONG
        },
        centerPlayVisible = item is SongItem,
        contextMenuEnabled = item is SongItem || item is AlbumItem || item is PlaylistItem,
        onContextMenuAction = { showMenu = true },
        onMoreClick = if (isArtist) ({ onClick(item) }) else null,
        quickPlay = when (item) {
            is AlbumItem -> CornerQuickPlayConfig(
                size = 42.dp,
                iconSize = 20.dp,
                onClick = {
                    playerViewModel.playAlbumFromBrowseId(
                        browseId = item.browseId,
                        playlistId = item.playlistId,
                        title = item.title,
                        onEmpty = { onClick(item) }
                    )
                }
            )

            is PlaylistItem -> CornerQuickPlayConfig(
                size = 42.dp,
                iconSize = 20.dp,
                onClick = {
                    playerViewModel.playPlaylistFromId(
                        playlistId = item.id,
                        endpoint = item.playEndpoint,
                        title = item.title,
                        onEmpty = { onClick(item) }
                    )
                }
            )

            else -> null
        },
        subtitle = when (item) {
            is SongItem -> item.artists.firstOrNull()?.name.orEmpty()
            is AlbumItem -> item.artists?.firstOrNull()?.name ?: stringResource(Res.string.item_album)
            is ArtistItem -> stringResource(Res.string.item_artist)
            is PlaylistItem -> item.author?.name ?: stringResource(Res.string.item_list)
            else -> ""
        },
        modifier = modifier,
        topStartOverlay = {
            if (item is SongItem && downloadState != null) {
                DownloadIndicator(
                    state = downloadState,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.55f), circleAwareShape())
                        .padding(4.dp)
                )
            }
        },
        overlayContent = {
            if (item is SongItem) {
                SongContextMenuPopup(
                    expanded = showMenu,
                    onDismiss = { showMenu = false },
                    song = item
                )
            } else if (item is AlbumItem || item is PlaylistItem) {
                CollectionContextMenuPopup(
                    expanded = showMenu,
                    onDismiss = { showMenu = false },
                    title = item.title,
                    isPlaylist = item is PlaylistItem,
                    onOpen = { onClick(item) },
                    onPlay = {
                        when (item) {
                            is AlbumItem -> playerViewModel.playAlbumFromBrowseId(
                                browseId = item.browseId,
                                playlistId = item.playlistId,
                                title = item.title,
                                onEmpty = { onClick(item) }
                            )

                            is PlaylistItem -> playerViewModel.playPlaylistFromId(
                                playlistId = item.id,
                                endpoint = item.playEndpoint,
                                title = item.title,
                                onEmpty = { onClick(item) }
                            )
                        }
                    },
                    onShuffle = {
                        when (item) {
                            is AlbumItem -> playerViewModel.playAlbumFromBrowseId(
                                browseId = item.browseId,
                                playlistId = item.playlistId,
                                title = item.title,
                                shuffle = true,
                                onEmpty = { onClick(item) }
                            )

                            is PlaylistItem -> playerViewModel.playPlaylistFromId(
                                playlistId = item.id,
                                endpoint = item.shuffleEndpoint ?: item.playEndpoint,
                                title = item.title,
                                shuffle = true,
                                onEmpty = { onClick(item) }
                            )
                        }
                    }
                )
            }
        }
    )
}
