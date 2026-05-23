package com.example.melodist.ui.screens.home

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
import com.example.melodist.ui.components.CornerQuickPlayConfig
import com.example.melodist.ui.components.PlaceholderType
import com.example.melodist.ui.components.YouTubeGridItem
import com.example.melodist.ui.components.context.CollectionContextMenu
import com.example.melodist.ui.components.context.SongContextMenu
import com.example.melodist.ui.components.song.DownloadIndicator
import com.example.melodist.ui.helpers.rememberSongDownloadState
import com.example.melodist.ui.utils.circleAwareShape
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.utils.LocalPlayerViewModel
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import kotlin.collections.isNotEmpty
import kotlin.collections.orEmpty
import lyrik.composeapp.generated.resources.Res
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SongHomeItem(
    item: SongItem,
    modifier: Modifier = Modifier,
    onClick: (YTItem) -> Unit
) {
    val downloadViewModel = LocalDownloadViewModel.current
    val downloadState by rememberSongDownloadState(item.id, downloadViewModel)

    var showMenu by remember { mutableStateOf(false) }

    YouTubeGridItem(
        item = item,
        onClick = onClick,
        imageShape = RoundedCornerShape(10.dp),
        alignment = Alignment.Start,
        titleAlign = TextAlign.Start,
        placeholderType = PlaceholderType.SONG,
        centerPlayVisible = true,
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
            SongContextMenu(
                expanded = showMenu,
                onDismiss = { showMenu = false },
                song = item
            )
        }
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AlbumHomeItem(item: AlbumItem, modifier: Modifier = Modifier, onClick: (YTItem) -> Unit) {
    val playerViewModel = LocalPlayerViewModel.current
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
            size = 28.dp,
            iconSize = 16.dp,
            onClick = {
                playerViewModel.playAlbumFromBrowseId(
                    browseId = item.browseId,
                    playlistId = item.playlistId,
                    title = item.title,
                    onEmpty = { onClick(item) }
                )
            }
        ),
        subtitle = item.artists?.firstOrNull()?.name ?: stringResource(Res.string.item_album),
        modifier = modifier,
        overlayContent = {
            CollectionContextMenu(
                expanded = showMenu,
                onDismiss = { showMenu = false },
                title = item.title,
                isPlaylist = false,
                onOpen = { onClick(item) },
                onPlay = {
                    playerViewModel.playAlbumFromBrowseId(
                        browseId = item.browseId,
                        playlistId = item.playlistId,
                        title = item.title,
                        onEmpty = { onClick(item) }
                    )
                },
                onShuffle = {
                    playerViewModel.playAlbumFromBrowseId(
                        browseId = item.browseId,
                        playlistId = item.playlistId,
                        title = item.title,
                        shuffle = true,
                        onEmpty = { onClick(item) }
                    )
                }
            )
        }
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlaylistHomeItem(item: PlaylistItem, modifier: Modifier = Modifier, onClick: (YTItem) -> Unit) {
    val playerViewModel = LocalPlayerViewModel.current
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
                playerViewModel.playPlaylistFromId(
                    playlistId = item.id,
                    endpoint = item.playEndpoint,
                    title = item.title,
                    onEmpty = { onClick(item) }
                )
            }
        ),
        subtitle = item.author?.name ?: stringResource(Res.string.item_list),
        modifier = modifier,
        overlayContent = {
            CollectionContextMenu(
                expanded = showMenu,
                onDismiss = { showMenu = false },
                title = item.title,
                isPlaylist = true,
                onOpen = { onClick(item) },
                onPlay = {
                    playerViewModel.playPlaylistFromId(
                        playlistId = item.id,
                        endpoint = item.playEndpoint,
                        title = item.title,
                        onEmpty = { onClick(item) }
                    )
                },
                onShuffle = {
                    playerViewModel.playPlaylistFromId(
                        playlistId = item.id,
                        endpoint = item.shuffleEndpoint ?: item.playEndpoint,
                        title = item.title,
                        shuffle = true,
                        onEmpty = { onClick(item) }
                    )
                }
            )
        }
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ArtistHomeItem(item: ArtistItem, modifier: Modifier = Modifier, onClick: (YTItem) -> Unit) {
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
