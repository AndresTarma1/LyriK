package com.example.melodist.ui.screens.library.tabs

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.filled.Album
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.melodist.navigation.Route
import com.example.melodist.ui.components.ItemContentSource
import com.example.melodist.ui.components.MediaGridItem
import com.example.melodist.ui.components.PlaceholderType
import com.example.melodist.ui.components.layout.AppVerticalScrollbar
import com.metrolist.innertube.models.AlbumItem

@Composable
fun AlbumsTab(
    albums: List<AlbumItem>,
    ytmAlbums: List<AlbumItem> = emptyList(),
    isLoadingYtm: Boolean = false,
    onNavigate: (Route) -> Unit,
    onRemove: (String) -> Unit,
    onQuickPlayAlbum: (browseId: String, playlistId: String?, title: String, onFallback: () -> Unit) -> Unit,
    onQuickShuffleAlbum: (browseId: String, playlistId: String?, title: String, onFallback: () -> Unit) -> Unit,
) {
    val isEmpty = albums.isEmpty() && ytmAlbums.isEmpty() && !isLoadingYtm
    if (isEmpty) {
        LibraryEmptyState(Icons.Default.Album, "No hay albumes guardados", "Guarda albumes y apareceran aqui")
        return
    }
    if (isLoadingYtm && ytmAlbums.isEmpty()) {
        YtmSectionHeader("Albumes en YouTube Music", isLoading = true)
        LibraryGridSkeleton(count = 4)
        return
    }

    val mergedAlbums = remember(albums, ytmAlbums) {
        buildList {
            albums.forEach { add(it to ItemContentSource.LOCAL) }
            ytmAlbums.forEach { add(it to ItemContentSource.YOUTUBE) }
        }.distinctBy { (album, _) -> album.id }
    }

    val gridState = rememberLazyGridState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = mergedAlbums,
                key = { (album, source) -> "${source.name.lowercase()}_${album.browseId}" }
            ) { (album, source) ->
                MediaGridItem(
                    item = album,
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
                    onRemove = { onRemove(album.browseId) },
                    isRemovable = source == ItemContentSource.LOCAL,
                    source = source,
                    modifier = Modifier.animateItem(
                        placementSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                )
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


