package com.example.melodist.ui.screens.library.tabs

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import lyrik.composeapp.generated.resources.Res
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.melodist.navigation.Route
import com.example.melodist.ui.components.ItemContentSource
import com.example.melodist.ui.components.MediaGridItem
import com.example.melodist.ui.components.PlaceholderType
import com.example.melodist.ui.components.layout.AppVerticalScrollbar
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.viewmodels.PlayerViewModel
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.YTItem
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

@Composable
fun PlaylistsTab(
    playlists: List<PlaylistItem>,
    ytmPlaylists: List<PlaylistItem> = emptyList(),
    isLoadingYtm: Boolean = false,
    onNavigate: (Route) -> Unit,
    onRemove: (String) -> Unit,
    playerViewModel: PlayerViewModel? = null,
    onQuickPlayPlaylist: (playlistId: String, endpoint: WatchEndpoint?, title: String, onFallback: () -> Unit) -> Unit,
    onQuickShufflePlaylist: (playlistId: String, endpoint: WatchEndpoint?, title: String, onFallback: () -> Unit) -> Unit,
) {
    val downloadViewModel = LocalDownloadViewModel.current
    val downloadedSongs by downloadViewModel.downloadedSongs.collectAsState()
    val downloadedCount by downloadViewModel.downloadedCount.collectAsState()
    val fullyDownloadedAlbums by downloadViewModel.fullyDownloadedAlbums.collectAsState()
    val fullyDownloadedPlaylists by downloadViewModel.fullyDownloadedPlaylists.collectAsState()

    val hasDownloads = downloadedCount > 0 || fullyDownloadedAlbums.isNotEmpty() || fullyDownloadedPlaylists.isNotEmpty()
    val isEmpty = playlists.isEmpty() && ytmPlaylists.isEmpty() && !isLoadingYtm && !hasDownloads
    if (isEmpty) {
        LibraryEmptyState(Icons.AutoMirrored.Filled.PlaylistPlay, stringResource(Res.string.no_saved_playlists), stringResource(Res.string.save_playlists_hint))
        return
    }
    if (isLoadingYtm && ytmPlaylists.isEmpty()) {
        YtmSectionHeader(stringResource(Res.string.ytm_playlists_section), isLoading = true)
        LibraryGridSkeleton(count = 4)
        return
    }

    // Resolved here (composable context) since remember{}'s calculation lambda below can't call
    // @Composable stringResource() itself. n_songs keeps its raw "%1$d ..." template so the count
    // can still be substituted per-item inside the list build.
    val playlistLabel = stringResource(Res.string.item_playlist)
    val downloadsLabel = stringResource(Res.string.downloads)
    val nSongsTemplate = stringResource(Res.string.n_songs)

    data class PlaylistGridEntry(
        val key: String,
        val item: YTItem? = null,
        val title: String,
        val subtitle: String,
        val thumbnailUrl: String?,
        val placeholderType: PlaceholderType,
        val shape: Shape,
        val onClick: () -> Unit,
        val onPlay: (() -> Unit)? = null,
        val onShuffle: (() -> Unit)? = null,
        val isRemovable: Boolean,
        val onRemove: () -> Unit = {},
        val source: ItemContentSource,
    )

    val mergedPlaylists = remember(
        playlists,
        ytmPlaylists,
        downloadedSongs,
        downloadedCount,
        fullyDownloadedPlaylists,
        fullyDownloadedAlbums,
    ) {
        val seenIds = mutableSetOf<String>()

        buildList {
            playlists.forEach { playlist ->
                seenIds.add(playlist.id)
                add(
                    PlaylistGridEntry(
                        key = "local_${playlist.id}",
                        item = playlist,
                        title = playlist.title,
                        subtitle = playlist.songCountText ?: playlist.author?.name ?: playlistLabel,
                        thumbnailUrl = playlist.thumbnail,
                        placeholderType = PlaceholderType.PLAYLIST,
                        shape = RoundedCornerShape(12.dp),
                        onClick = { onNavigate(Route.Playlist(playlist.id)) },
                        onPlay = {
                            onQuickPlayPlaylist(playlist.id, playlist.playEndpoint, playlist.title) {
                                onNavigate(Route.Playlist(playlist.id))
                            }
                        },
                        onShuffle = {
                            onQuickShufflePlaylist(playlist.id, playlist.shuffleEndpoint ?: playlist.playEndpoint, playlist.title) {
                                onNavigate(Route.Playlist(playlist.id))
                            }
                        },
                        isRemovable = true,
                        onRemove = { onRemove(playlist.id) },
                        source = ItemContentSource.LOCAL,
                    )
                )
            }

            ytmPlaylists.forEach { playlist ->
                if (playlist.id !in seenIds) {
                    seenIds.add(playlist.id)
                    add(
                        PlaylistGridEntry(
                            key = "ytm_${playlist.id}",
                            item = playlist,
                            title = playlist.title,
                            subtitle = playlist.songCountText ?: playlist.author?.name ?: playlistLabel,
                            thumbnailUrl = playlist.thumbnail,
                            placeholderType = PlaceholderType.PLAYLIST,
                            shape = RoundedCornerShape(12.dp),
                            onClick = { onNavigate(Route.Playlist(playlist.id)) },
                            onPlay = {
                                onQuickPlayPlaylist(playlist.id, playlist.playEndpoint, playlist.title) {
                                    onNavigate(Route.Playlist(playlist.id))
                                }
                            },
                            onShuffle = {
                                onQuickShufflePlaylist(playlist.id, playlist.shuffleEndpoint ?: playlist.playEndpoint, playlist.title) {
                                    onNavigate(Route.Playlist(playlist.id))
                                }
                            },
                            isRemovable = false,
                            source = ItemContentSource.YOUTUBE,
                        )
                    )
                }
            }

            if (downloadedCount > 0) {
                add(
                    PlaylistGridEntry(
                        key = "downloads_all",
                        title = downloadsLabel,
                        subtitle = String.format(nSongsTemplate, downloadedCount),
                        thumbnailUrl = downloadedSongs.firstOrNull()?.thumbnail,
                        placeholderType = PlaceholderType.DOWNLOADS,
                        shape = RoundedCornerShape(12.dp),
                        onClick = { onNavigate(Route.Playlist("LOCAL_DOWNLOADS")) },
                        onPlay = {
                            if (downloadedSongs.isNotEmpty()) {
                                playerViewModel?.playCustom(downloadedSongs, 0)
                            } else {
                                onNavigate(Route.Playlist("LOCAL_DOWNLOADS"))
                            }
                        },
                        onShuffle = {
                            if (downloadedSongs.isNotEmpty()) {
                                playerViewModel?.playCustom(downloadedSongs, 0)
                                playerViewModel?.toggleShuffle()
                            } else {
                                onNavigate(Route.Playlist("LOCAL_DOWNLOADS"))
                            }
                        },
                        isRemovable = false,
                        source = ItemContentSource.LOCAL,
                    )
                )
            }

            fullyDownloadedPlaylists.forEach { playlistInfo ->
                add(
                    PlaylistGridEntry(
                        key = "dlpl_${playlistInfo.playlistId}",
                        title = playlistInfo.playlistName,
                        subtitle = String.format(nSongsTemplate, playlistInfo.downloadedSongCount),
                        thumbnailUrl = playlistInfo.thumbnail,
                        placeholderType = PlaceholderType.PLAYLIST,
                        shape = RoundedCornerShape(12.dp),
                        onClick = { onNavigate(Route.Playlist(playlistInfo.playlistId)) },
                        onPlay = {
                            onQuickPlayPlaylist(playlistInfo.playlistId, null, playlistInfo.playlistName) {
                                onNavigate(Route.Playlist(playlistInfo.playlistId))
                            }
                        },
                        onShuffle = {
                            onQuickShufflePlaylist(playlistInfo.playlistId, null, playlistInfo.playlistName) {
                                onNavigate(Route.Playlist(playlistInfo.playlistId))
                            }
                        },
                        isRemovable = false,
                        source = ItemContentSource.LOCAL,
                    )
                )
            }

            fullyDownloadedAlbums.forEach { albumInfo ->
                add(
                    PlaylistGridEntry(
                        key = "dlal_${albumInfo.albumId}",
                        title = albumInfo.albumName,
                        subtitle = String.format(nSongsTemplate, albumInfo.songs.size),
                        thumbnailUrl = albumInfo.thumbnail,
                        placeholderType = PlaceholderType.ALBUM,
                        shape = RoundedCornerShape(12.dp),
                        onClick = { onNavigate(Route.Album(albumInfo.albumId)) },
                        isRemovable = false,
                        source = ItemContentSource.LOCAL,
                    )
                )
            }
        }
    }

    val gridState = rememberLazyGridState()
    val reorderableLazyGridState = rememberReorderableLazyGridState(gridState){ form, to ->}

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items = mergedPlaylists, key = { it.key }) { entry ->
                ReorderableItem(reorderableLazyGridState, key = entry.key){
                    if (entry.item != null) {
                        MediaGridItem(
                            item = entry.item,
                            onClick = entry.onClick,
                            onPlay = entry.onPlay,
                            onShuffle = entry.onShuffle,
                            onRemove = entry.onRemove,
                            isRemovable = entry.isRemovable,
                            source = entry.source,
                        )
                    } else {
                        MediaGridItem(
                            title = entry.title,
                            subtitle = entry.subtitle,
                            thumbnailUrl = entry.thumbnailUrl,
                            placeholderType = entry.placeholderType,
                            shape = entry.shape,
                            onClick = entry.onClick,
                            onPlay = entry.onPlay,
                            onShuffle = entry.onShuffle,
                            onRemove = entry.onRemove,
                            isRemovable = entry.isRemovable,
                            source = entry.source,
                        )
                    }
                }
            }
        }

        AppVerticalScrollbar(
            state = gridState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
        )
    }
}

