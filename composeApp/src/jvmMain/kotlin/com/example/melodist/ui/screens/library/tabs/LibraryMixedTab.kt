package com.example.melodist.ui.screens.library.tabs

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.LibraryMusic
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
import com.example.melodist.ui.screens.library.LibraryScreenState
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.viewmodels.YtmLibraryState
import com.example.melodist.viewmodels.PlayerViewModel
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.YTItem
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import com.example.melodist.ui.utils.circleAwareShape

@Composable
fun LibraryMixedTab(
    state: LibraryScreenState,
    onNavigate: (Route) -> Unit,
    playerViewModel: PlayerViewModel? = null,
    onRemovePlaylist: (String) -> Unit = {},
    onQuickPlayAlbum: (browseId: String, playlistId: String?, title: String, onFallback: () -> Unit) -> Unit,
    onQuickShuffleAlbum: (browseId: String, playlistId: String?, title: String, onFallback: () -> Unit) -> Unit,
    onQuickPlayPlaylist: (playlistId: String, endpoint: WatchEndpoint?, title: String, onFallback: () -> Unit) -> Unit,
    onQuickShufflePlaylist: (playlistId: String, endpoint: WatchEndpoint?, title: String, onFallback: () -> Unit) -> Unit,
) {
    val ytm = state.ytmState as? YtmLibraryState.Success
    val downloadViewModel = LocalDownloadViewModel.current
    val downloadedSongs by downloadViewModel.downloadedSongs.collectAsState()
    val downloadedCount by downloadViewModel.downloadedCount.collectAsState()
    val fullyDownloadedAlbums by downloadViewModel.fullyDownloadedAlbums.collectAsState()
    val fullyDownloadedPlaylists by downloadViewModel.fullyDownloadedPlaylists.collectAsState()

    data class MixedGridEntry(
        val key: String,
        val item: YTItem? = null,
        val title: String,
        val subtitle: String,
        val thumbnailUrl: String?,
        val placeholderType: PlaceholderType,
        val shape: Shape,
        val source: ItemContentSource,
        val onClick: () -> Unit,
        val onPlay: (() -> Unit)? = null,
        val onShuffle: (() -> Unit)? = null,
        val isRemovable: Boolean = false,
        val onRemove: () -> Unit = {},
    )

    val items = remember(
        state.albums,
        state.artists,
        state.playlists,
        ytm,
        downloadedSongs,
        downloadedCount,
        fullyDownloadedAlbums,
        fullyDownloadedPlaylists,
    ) {
        buildList {
            val mergedAlbums = (ytm?.albums.orEmpty() + state.albums).distinctBy { it.id }
            val mergedArtists = (ytm?.artists.orEmpty() + state.artists).distinctBy { it.id }
            val mergedPlaylists = (ytm?.playlists.orEmpty() + state.playlists).distinctBy { it.id }

            val seenAlbumIds = mutableSetOf<String>()
            val seenPlaylistIds = mutableSetOf<String>()

            mergedAlbums.forEach { album ->
                seenAlbumIds.add(album.id)
                add(
                    MixedGridEntry(
                        key = "alb_${album.id}",
                        item = album,
                        title = album.title,
                        subtitle = album.artists?.firstOrNull()?.name ?: "Album",
                        thumbnailUrl = album.thumbnail,
                        placeholderType = PlaceholderType.ALBUM,
                        shape = RoundedCornerShape(12.dp),
                        source = if (ytm?.albums?.any { it.id == album.id } == true) ItemContentSource.YOUTUBE else ItemContentSource.LOCAL,
                        onClick = { onNavigate(Route.Album(album.browseId)) },
                        onPlay = {
                            onQuickPlayAlbum(album.browseId, album.playlistId, album.title) {
                                onNavigate(Route.Album(album.browseId))
                            }
                        },
                        onShuffle = {
                            onQuickShuffleAlbum(album.browseId, album.playlistId, album.title) {
                                onNavigate(Route.Album(album.browseId))
                            }
                        },
                    )
                )
            }

            mergedArtists.forEach { artist ->
                add(
                    MixedGridEntry(
                        key = "art_${artist.id}",
                        item = artist,
                        title = artist.title,
                        subtitle = "Artista",
                        thumbnailUrl = artist.thumbnail,
                        placeholderType = PlaceholderType.ARTIST,
                        shape = circleAwareShape(),
                        source = if (ytm?.artists?.any { it.id == artist.id } == true) ItemContentSource.YOUTUBE else ItemContentSource.LOCAL,
                        onClick = { onNavigate(Route.Artist(artist.id)) },
                    )
                )
            }

            mergedPlaylists.forEach { playlist ->
                val isYtm = ytm?.playlists?.any { it.id == playlist.id } == true
                seenPlaylistIds.add(playlist.id)
                add(
                    MixedGridEntry(
                        key = "pl_${playlist.id}",
                        item = playlist,
                        title = playlist.title,
                        subtitle = playlist.songCountText ?: playlist.author?.name ?: "Playlist",
                        thumbnailUrl = playlist.thumbnail,
                        placeholderType = PlaceholderType.PLAYLIST,
                        shape = RoundedCornerShape(12.dp),
                        source = if (isYtm) ItemContentSource.YOUTUBE else ItemContentSource.LOCAL,
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
                        isRemovable = !isYtm,
                        onRemove = { onRemovePlaylist(playlist.id) },
                    )
                )
            }

            if (downloadedCount > 0) {
                add(
                    MixedGridEntry(
                        key = "downloads_all",
                        title = "Descargas",
                        subtitle = "$downloadedCount canciones",
                        thumbnailUrl = downloadedSongs.firstOrNull()?.thumbnail,
                        placeholderType = PlaceholderType.DOWNLOADS,
                        shape = RoundedCornerShape(12.dp),
                        source = ItemContentSource.LOCAL,
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
                    )
                )
            }

            fullyDownloadedPlaylists.forEach { playlistInfo ->
                if (playlistInfo.playlistId !in seenPlaylistIds) {
                    add(
                        MixedGridEntry(
                            key = "dlpl_${playlistInfo.playlistId}",
                            title = playlistInfo.playlistName,
                            subtitle = "${playlistInfo.downloadedSongCount} canciones",
                            thumbnailUrl = playlistInfo.thumbnail,
                            placeholderType = PlaceholderType.PLAYLIST,
                            shape = RoundedCornerShape(12.dp),
                            source = ItemContentSource.LOCAL,
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
                        )
                    )
                }
            }

            fullyDownloadedAlbums.forEach { albumInfo ->
                if (albumInfo.albumId !in seenAlbumIds) {
                    add(
                        MixedGridEntry(
                            key = "dlal_${albumInfo.albumId}",
                            title = albumInfo.albumName,
                            subtitle = "${albumInfo.songs.size} canciones",
                            thumbnailUrl = albumInfo.thumbnail,
                            placeholderType = PlaceholderType.ALBUM,
                            shape = RoundedCornerShape(12.dp),
                            source = ItemContentSource.LOCAL,
                            onClick = { onNavigate(Route.Album(albumInfo.albumId)) },
                            onPlay = {
                                onQuickPlayAlbum(albumInfo.albumId, null, albumInfo.albumName) {
                                    onNavigate(Route.Album(albumInfo.albumId))
                                }
                            },
                            onShuffle = {
                                onQuickShuffleAlbum(albumInfo.albumId, null, albumInfo.albumName) {
                                    onNavigate(Route.Album(albumInfo.albumId))
                                }
                            },
                        )
                    )
                }
            }
        }
    }

    if (items.isEmpty()) {
        LibraryEmptyState(Icons.Default.LibraryMusic, "Biblioteca vacia", "Guarda albumes, artistas o playlists")
        return
    }

    val gridState = rememberLazyGridState()
    val reorderableLazyGridState = rememberReorderableLazyGridState(gridState) { _, _ -> {
    }}

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items = items, key = { it.key }) { entry ->
                ReorderableItem(reorderableLazyGridState, key = entry.key){
                    if (entry.item != null) {
                        MediaGridItem(
                            item = entry.item,
                            onClick = entry.onClick,
                            onPlay = entry.onPlay,
                            onShuffle = entry.onShuffle,
                            onRemove = entry.onRemove,
                            isRemovable = entry.isRemovable,
                            source = entry.source
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
                            source = entry.source
                        )
                    }

                }
            }
        }

        AppVerticalScrollbar(
            state = gridState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(12.dp)
                .padding(vertical = 4.dp, horizontal = 2.dp)
        )
    }
}
