package com.example.melodist.ui.screens.home

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.melodist.models.toMediaMetadata
import com.example.melodist.navigation.Route
import com.example.melodist.ui.components.ChipRowSkeleton
import com.example.melodist.ui.components.HorizontalGridLikeRow
import com.example.melodist.ui.components.ItemContentSource
import com.example.melodist.ui.components.SectionSkeleton
import com.example.melodist.ui.components.YoutubeListItem
import com.example.melodist.ui.components.layout.AppVerticalScrollbar
import com.example.melodist.ui.components.layout.HorizontalScrollableRow
import com.example.melodist.ui.helpers.rememberSongDownloadState
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.viewmodels.HomeState
import com.example.melodist.viewmodels.HomeUiEvent
import com.example.melodist.viewmodels.HomeViewModel
import com.example.melodist.viewmodels.PlayerViewModel
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.pages.ChartsPage
import com.metrolist.innertube.pages.HomePage
import lyrik.composeapp.generated.resources.Res
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.HorizontalScrollbar

@Composable
fun HomeScreenRoute(
    viewModel: HomeViewModel,
    onNavigate: (Route) -> Unit,
) {
    val playerViewModel = LocalPlayerViewModel.current
    val uiState by viewModel.uiState.collectAsState()
    val recentSongs by viewModel.recentSongs.collectAsState()

    HomeScreen(
        uiState = uiState,
        recentSongs = recentSongs,
        onEvent = viewModel::onEvent,
        onNavigate = onNavigate,
        playerViewModel = playerViewModel,
    )
}

// Screen — sin TopAppBar redundante (ya hay título en la TitleBar)
@Composable
fun HomeScreen(
    uiState: HomeState,
    recentSongs: List<SongItem> = emptyList(),
    onEvent: (HomeUiEvent) -> Unit,
    onNavigate: (Route) -> Unit,
    playerViewModel: PlayerViewModel? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (uiState) {

            is HomeState.Loading -> HomeScreenLoading(
                modifier = Modifier.padding(top = 16.dp)
            )

            is HomeState.Success -> HomeScreenContent(
                page = uiState.page,
                recentSongs = recentSongs,
                selectedParams = uiState.selectedParams,
                isLoadingMore = uiState.isLoadingMore,
                onChipClick = { params -> onEvent(HomeUiEvent.ChipSelected(params)) },
                onScrollNearEnd = { onEvent(HomeUiEvent.LoadMore) },
                onNavigate = onNavigate,
                playerViewModel = playerViewModel,
                contentPadding = PaddingValues(top = 16.dp),
            )

            is HomeState.Error -> HomeScreenError(
                message = uiState.message,
                isOffline = uiState.isOffline,
                onRetry = { onEvent(HomeUiEvent.Retry) },
            )
        }

        IconButton(
            onClick = { onEvent(HomeUiEvent.Retry) },
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = stringResource(Res.string.refresh)
            )
        }
    }
}

// Content — composable tonto, sin lógica de negocio
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreenContent(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    page: HomePage,
    recentSongs: List<SongItem> = emptyList(),
    selectedParams: String?,
    isLoadingMore: Boolean,
    onChipClick: (String?) -> Unit,
    onScrollNearEnd: () -> Unit,
    onNavigate: (Route) -> Unit,
    playerViewModel: PlayerViewModel? = null,
) {
    // 1. Usar LazyListState para LazyColumn
    val listState = rememberLazyListState()

    // 2. Detectar final de scroll para paginación
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf false

            lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 2
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) onScrollNearEnd()
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {


        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = contentPadding
        ) {
            // 4. Una sola fila para todos los chips
            if (!page.chips.isNullOrEmpty()) {
                item {
                    ChipFilterRow(
                        chips = page.chips!!,
                        selectedParams = selectedParams,
                        onChipClick = onChipClick,
                    )
                }
            }

            if (recentSongs.isNotEmpty()) {
                item {
                    QuickPicksSection(
                        songs = recentSongs,
                        playerViewModel = playerViewModel,
                    )
                }
            }

            items(
                page.sections,
                key = { section -> section.title }) { section ->
                HomeSectionRow(
                    section = section,
                    onNavigate = onNavigate,
                    playerViewModel = playerViewModel,
                )

            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
            if (isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {

                        LoadingIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        AppVerticalScrollbar(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

// Sub-composables tontos — solo renderizan, no deciden

@Composable
private fun ChipFilterRow(
    chips: List<HomePage.Chip>,
    selectedParams: String?,
    onChipClick: (String?) -> Unit,
) {
    val lazyListState = rememberLazyListState()
    Column(Modifier.padding(end = 12.dp)) {
        HorizontalScrollableRow(
            modifier = Modifier.padding(vertical = 10.dp),
            state = lazyListState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(chips.size) { index ->
                val chip = chips[index]
                val isSelected = chip.endpoint?.params == selectedParams
                FilterChip(
                    selected = isSelected,
                    onClick = { onChipClick(chip.endpoint?.params) },
                    label = {
                        Text(
                            chip.title,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    ),
                    border = null,
                    leadingIcon = {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "selected",
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeSectionRow(
    section: HomePage.Section,
    onNavigate: (Route) -> Unit,
    playerViewModel: PlayerViewModel?,
) {
    Column(modifier = Modifier.padding(top = 10.dp, bottom = 10.dp, end = 12.dp)) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        val rows = section.numItemsPerColumn ?: 1
        if (rows > 1) {
            // Grid shelf: YouTube stacks several rows per column (e.g. quick-picks-style song
            // lists). Render as list rows across multiple columns, like the search results.
            HorizontalGridLikeRow(
                items = section.items,
                rows = rows,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                columnWidth = 320.dp,
                rowSpacing = 8.dp,
                columnSpacing = 12.dp,
                itemKey = { it.id },
            ) { item ->
                YoutubeListItem(
                    item = item,
                    source = ItemContentSource.YOUTUBE,
                    onItemClick = { onHomeItemClick(it, onNavigate, playerViewModel) },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                )
            }
        } else {
            val sectionScrollState = rememberLazyListState()
            HorizontalScrollableRow(
                modifier = Modifier.fillMaxWidth(),
                state = sectionScrollState,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    count = section.items.size,
                    key = { index -> section.items[index].id }
                ) { index ->
                    HomeSectionItem(
                        item = section.items[index],
                        onNavigate = onNavigate,
                        playerViewModel = playerViewModel,
                    )
                }
            }
        }
    }
}

/** Shared click routing for a home YTItem (play a song, or navigate to album/playlist/artist). */
private fun onHomeItemClick(
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

/**
 * Renders a home screen item UI based on its type, with appropriate click handlers for playback or navigation.
 */
@Composable
private fun HomeSectionItem(
    item: YTItem,
    onNavigate: (Route) -> Unit,
    playerViewModel: PlayerViewModel?,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is SongItem -> SongHomeItem(
            item = item,
            onClick = { playerViewModel?.playSingle(item) },
            modifier = modifier,
        )

        is AlbumItem -> AlbumHomeItem(
            item = item,
            onClick = { onNavigate(Route.Album((it as AlbumItem).browseId)) },
            modifier = modifier,
        )

        is PlaylistItem -> PlaylistHomeItem(
            item = item,
            onClick = { onNavigate(Route.Playlist((it as PlaylistItem).id)) },
            modifier = modifier,
        )

        is ArtistItem -> ArtistHomeItem(
            item = item,
            onClick = { onNavigate(Route.Artist((it as ArtistItem).id)) },
            modifier = modifier,
        )

        else -> {}
    }
}

/**
 * Renders a section of recently played songs in a 2-row horizontal grid layout.
 *
 * Songs are displayed with local content when available, otherwise with YouTube content.
 * Clicking a song plays it.
 *
 * @param songs The songs to display.
 */
@Composable
private fun QuickPicksSection(
    songs: List<SongItem>,
    playerViewModel: PlayerViewModel?,
) {
    val rowCount = 2
    val itemHeight = 64.dp
    val rowSpacing = 8.dp
    val columnWidth = 320.dp

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.recently_played),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        HorizontalGridLikeRow(
            items = songs,
            rows = rowCount,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            columnWidth = columnWidth,
            rowSpacing = rowSpacing,
            columnSpacing = 12.dp,
            itemKey = { it.id }
        ) { song ->
            val downloadViewModel = LocalDownloadViewModel.current
            val downloadState by rememberSongDownloadState(song.id, downloadViewModel)
            val source = if (downloadState != null) ItemContentSource.LOCAL else ItemContentSource.YOUTUBE

            YoutubeListItem(
                item = song,
                source = source,
                onItemClick = { playerViewModel?.playSingle(song) },
                modifier = Modifier.fillMaxWidth().height(itemHeight)
            )
        }
    }
}

// Loading / Error — sin cambios de lógica

@Composable
fun HomeScreenLoading(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ChipRowSkeleton()
        repeat(3) { SectionSkeleton() }
    }
}

@Composable
fun HomeScreenError(message: String, isOffline: Boolean = false, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            if (isOffline) Icons.Default.WifiOff else Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = if (isOffline) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(if (isOffline) Res.string.home_offline_title else Res.string.home_error),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (isOffline) stringResource(Res.string.home_offline_message) else message,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) { Text(stringResource(Res.string.home_retry)) }
    }
}

@Composable
fun HomeChartsSection(
    section: ChartsPage.ChartSection,
    onNavigate: (Route) -> Unit,
    playerViewModel: PlayerViewModel?,
) {
    Column(modifier = Modifier.padding(top = 12.dp, bottom = 12.dp, end = 16.dp)) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )

        val sectionScrollState = rememberLazyListState()
        HorizontalScrollableRow(
            modifier = Modifier.fillMaxWidth(),
            state = sectionScrollState,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(
                count = section.items.take(12).size,
                key = { index -> "chart_${section.title}_${section.items[index].id}" }
            ) { index ->
                val item = section.items[index]
                HomeSectionItem(
                    item = item,
                    onNavigate = onNavigate,
                    playerViewModel = playerViewModel,
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}
