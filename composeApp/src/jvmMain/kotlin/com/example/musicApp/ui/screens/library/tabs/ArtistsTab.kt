package com.example.musicApp.ui.screens.library.tabs

import androidx.compose.foundation.layout.Arrangement
import lyrik.composeapp.generated.resources.Res
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.musicApp.navigation.Route
import com.example.musicApp.ui.components.ItemContentSource
import com.example.musicApp.ui.components.MediaGridItem
import com.example.musicApp.ui.components.layout.AppVerticalScrollbar
import com.metrolist.innertube.models.ArtistItem
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

@Composable
fun ArtistsTab(
    artists: List<ArtistItem>,
    ytmArtists: List<ArtistItem> = emptyList(),
    isLoadingYtm: Boolean = false,
    onNavigate: (Route) -> Unit,
    onRemove: (String) -> Unit,
) {
    val isEmpty = artists.isEmpty() && ytmArtists.isEmpty() && !isLoadingYtm
    if (isEmpty) {
        LibraryEmptyState(Icons.Default.Person, stringResource(Res.string.no_saved_artists), stringResource(Res.string.save_artists_hint))
        return
    }
    if (isLoadingYtm && ytmArtists.isEmpty()) {
        LibraryGridSkeleton(count = 4, isCircle = true)
        return
    }

    val mergedArtists = remember(artists, ytmArtists) {
        buildList {
            artists.forEach { add(it to ItemContentSource.LOCAL) }
            ytmArtists.forEach { add(it to ItemContentSource.YOUTUBE) }
        }.distinctBy { (artist, _) -> artist.id }
    }

    val gridState = rememberLazyGridState()
    val reorderableLazyGridState = rememberReorderableLazyGridState(gridState){from, to ->}
    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = mergedArtists,
                key = { (artist, source) -> "${source.name.lowercase()}_${artist.id}" }
            ) { (artist, source) ->

                ReorderableItem(reorderableLazyGridState, key = artist)
                {
                    MediaGridItem(
                        item = artist,

                        onClick = { onNavigate(Route.Artist(artist.id)) },
                        onRemove = { onRemove(artist.id) },
                        isRemovable = source == ItemContentSource.LOCAL,
                        source = source,
                    )

                }
            }
        }

        AppVerticalScrollbar(
            state = gridState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
        )
    }
}
