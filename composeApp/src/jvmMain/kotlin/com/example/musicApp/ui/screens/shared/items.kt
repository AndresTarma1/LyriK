package com.example.musicApp.ui.screens.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.musicApp.navigation.Route
import com.example.musicApp.ui.components.CornerQuickPlayConfig
import com.example.musicApp.ui.components.ItemContentSource
import com.example.musicApp.ui.components.images.PlaceholderType
import com.example.musicApp.ui.components.YoutubeListItem
import com.example.musicApp.ui.components.YouTubeGridItem
import com.example.musicApp.ui.components.context.CollectionContextMenuPopup
import com.example.musicApp.ui.components.context.SongContextMenuPopup
import com.example.musicApp.ui.components.dialogs.ArtistsModal
import com.example.musicApp.ui.components.song.DownloadIndicator
import com.example.musicApp.ui.helpers.rememberSongDownloadState
import com.example.musicApp.ui.utils.circleAwareShape
import com.example.musicApp.utils.LocalDownloadViewModel
import com.example.musicApp.viewmodels.PlayerViewModel
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import lyrik.composeapp.generated.resources.Res
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

fun onYTItemClick(
    item: YTItem,
    onNavigate: (Route) -> Unit,
    playerViewModel: PlayerViewModel?,
) {
    when (item) {
        is SongItem -> playerViewModel?.playSingle(item)
        is AlbumItem -> onNavigate(Route.Album(item.browseId))
        is PlaylistItem -> onNavigate(Route.Playlist(item.id))
        is ArtistItem -> onNavigate(Route.Artist(item.id))
        else -> {}
    }
}


@Composable
fun SongGridItem(
    item: SongItem,
    modifier: Modifier = Modifier,
    onClick: (YTItem) -> Unit,
    onClickSubtitle: (String) -> Unit,
) {
    val downloadViewModel = LocalDownloadViewModel.current
    val downloadState by rememberSongDownloadState(item.id, downloadViewModel)

    var showMenu by remember { mutableStateOf(false) }
    var showModalSheet by remember { mutableStateOf(false) }

    YouTubeGridItem(
        item = item,
        onClick = onClick,
        imageShape = RoundedCornerShape(10.dp),
        alignment = Alignment.Start,
        titleAlign = TextAlign.Start,
        placeholderType = PlaceholderType.SONG,
        centerPlayVisible = true,
        onClickSubtitle = { showModalSheet = true },
        contextMenuEnabled = true,
        onContextMenuAction = { showMenu = true },
        subtitle = item.artists.firstOrNull()?.name.orEmpty(),
        modifier = modifier,
        topStartOverlay = {
            if (downloadState != null) {
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
            SongContextMenuPopup(
                expanded = showMenu,
                onDismiss = { showMenu = false },
                song = item
            )
        }
    )

    if (showModalSheet) {
        ArtistsModal(
            artists = item.artists,
            onClickArtist = { artistId ->
                showModalSheet = false
                onClickSubtitle(artistId)
            },
            onDismiss = { showModalSheet = false }
        )
    }
}

@Composable
fun AlbumGridItem(
    item: AlbumItem,
    modifier: Modifier = Modifier,
    onClick: (YTItem) -> Unit,
    onPlay: (YTItem) -> Unit,
    onShuffle: (YTItem) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    YouTubeGridItem(
        item = item,
        onClick = onClick,
        imageShape = RoundedCornerShape(10.dp),
        alignment = Alignment.Start,
        titleAlign = TextAlign.Start,
        placeholderType = PlaceholderType.ALBUM,
        centerPlayVisible = false,
        contextMenuEnabled = true,
        onContextMenuAction = { showMenu = true },
        quickPlay = CornerQuickPlayConfig(
            size = 42.dp,
            iconSize = 20.dp,
            onClick = {
                onPlay(item)
            }
        ),
        subtitle = item.artists?.firstOrNull()?.name ?: stringResource(Res.string.item_album),
        modifier = modifier,
        overlayContent = {
            CollectionContextMenuPopup(
                expanded = showMenu,
                onDismiss = { showMenu = false },
                title = item.title,
                isPlaylist = false,
                onOpen = { onClick(item) },
                onPlay = {
                    onPlay(item)
                },
                onShuffle = {
                    onShuffle(item)
                }
            )
        }
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlaylistGridItem(
    item: PlaylistItem,
    modifier: Modifier = Modifier,
    onClick: (YTItem) -> Unit,
    onPlay: (YTItem) -> Unit,
    onShuffle: (YTItem) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    YouTubeGridItem(
        item = item,
        onClick = onClick,
        imageShape = RoundedCornerShape(10.dp),
        alignment = Alignment.Start,
        titleAlign = TextAlign.Start,
        placeholderType = PlaceholderType.PLAYLIST,
        centerPlayVisible = false,
        contextMenuEnabled = true,
        onContextMenuAction = { showMenu = true },
        quickPlay = CornerQuickPlayConfig(
            size = 42.dp,
            iconSize = 20.dp,
            onClick = {
                onPlay(item)
            }
        ),
        subtitle = item.author?.name ?: stringResource(Res.string.item_list),
        modifier = modifier,
        overlayContent = {
            CollectionContextMenuPopup(
                expanded = showMenu,
                onDismiss = { showMenu = false },
                title = item.title,
                isPlaylist = true,
                onOpen = { onClick(item) },
                onPlay = {
                    onPlay(item)
                },
                onShuffle = {
                    onShuffle(item)
                }
            )
        }
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ArtistGridItem(
    item: ArtistItem,
    modifier: Modifier = Modifier,
    onClick: (YTItem) -> Unit,
) {
    YouTubeGridItem(
        item = item,
        onClick = onClick,
        imageShape = circleAwareShape(),
        alignment = Alignment.CenterHorizontally,
        titleAlign = TextAlign.Center,
        placeholderType = PlaceholderType.ARTIST,
        centerPlayVisible = false,
        contextMenuEnabled = false,
        onMoreClick = { onClick(item) },
        subtitle = stringResource(Res.string.item_artist),
        modifier = modifier
    )
}

@Composable
fun SectionListItem(
    item: YTItem,
    onNavigate: (Route) -> Unit = {},
    modifier: Modifier = Modifier,
    playerViewModel: PlayerViewModel
) {

    val onClick = { it: YTItem -> onYTItemClick(it, onNavigate, playerViewModel) }

    val onPlay = { it: YTItem ->
        when (it) {
            is SongItem -> playerViewModel.playSingle(it)
            is AlbumItem -> playerViewModel.playAlbumFromBrowseId(
                browseId = it.browseId,
                playlistId = it.playlistId,
                title = it.title,
                onEmpty = { onClick(it) }
            )
            is PlaylistItem -> playerViewModel.playPlaylistFromId(
                playlistId = it.id,
                endpoint = it.playEndpoint,
                title = it.title,
                onEmpty = { onClick(it) }
            )
            is ArtistItem -> onNavigate(Route.Artist(it.id))
            else -> onClick(it)
        }
    }

    val onShuffle = { it: YTItem ->
        when (it) {
            is AlbumItem -> playerViewModel.playAlbumFromBrowseId(
                browseId = it.browseId,
                playlistId = it.playlistId,
                title = it.title,
                shuffle = true,
                onEmpty = { onClick(it) }
            )
            is PlaylistItem -> playerViewModel.playPlaylistFromId(
                playlistId = it.id,
                endpoint = it.shuffleEndpoint ?: it.playEndpoint,
                title = it.title,
                shuffle = true,
                onEmpty = { onClick(it) }
            )
            else -> onClick(it)
        }
    }

    YoutubeListItem(
        item = item,
        onClick = onClick,
        onPlay = onPlay,
        onShuffle = onShuffle,
        modifier = modifier,
        source = ItemContentSource.YOUTUBE,
    )
}

@Composable
fun SectionGridItem(
    item: YTItem,
    onNavigate: (Route) -> Unit,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    val onClick = { it: YTItem -> onYTItemClick(it, onNavigate, playerViewModel) }

    val onPlay = { it: YTItem ->
        when (it) {
            is SongItem -> playerViewModel.playSingle(it)
            is AlbumItem -> playerViewModel.playAlbumFromBrowseId(
                browseId = it.browseId,
                playlistId = it.playlistId,
                title = it.title,
                onEmpty = { onClick(it) }
            )
            is PlaylistItem -> playerViewModel.playPlaylistFromId(
                playlistId = it.id,
                endpoint = it.playEndpoint,
                title = it.title,
                onEmpty = { onClick(it) }
            )
            is ArtistItem -> onNavigate(Route.Artist(it.id))
            else -> onClick(it)
        }
    }

    val onShuffle = { it: YTItem ->
        when (it) {
            is AlbumItem -> playerViewModel.playAlbumFromBrowseId(
                browseId = it.browseId,
                playlistId = it.playlistId,
                title = it.title,
                shuffle = true,
                onEmpty = { onClick(it) }
            )
            is PlaylistItem -> playerViewModel.playPlaylistFromId(
                playlistId = it.id,
                endpoint = it.shuffleEndpoint ?: it.playEndpoint,
                title = it.title,
                shuffle = true,
                onEmpty = { onClick(it) }
            )
            else -> onClick(it)
        }
    }
    when (item) {
        is SongItem -> SongGridItem(
            item = item,
            onClick = onClick,
            onClickSubtitle = { onNavigate(Route.Artist(it)) },
            modifier = modifier,
        )
        is AlbumItem -> AlbumGridItem(item = item, onClick = onClick, modifier = modifier, onPlay = onPlay, onShuffle = onShuffle)
        is PlaylistItem -> PlaylistGridItem(item = item, onClick = onClick, modifier = modifier, onPlay = onPlay, onShuffle = onShuffle)
        is ArtistItem -> ArtistGridItem(item = item, onClick = onClick, modifier = modifier)
        else -> {}
    }
}