package com.example.melodist.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.example.melodist.viewmodels.SearchViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.melodist.db.entities.SearchHistoryEntry
import com.example.melodist.navigation.Route
import com.example.melodist.ui.components.layout.AppVerticalScrollbar
import com.example.melodist.ui.components.ChipRowSkeleton
import com.example.melodist.ui.components.HorizontalGridLikeRow
import com.example.melodist.ui.components.layout.HorizontalScrollableRow
import com.example.melodist.ui.components.ItemContentSource
import com.example.melodist.ui.components.SongSkeleton
import com.example.melodist.ui.components.YoutubeListItem
import com.example.melodist.ui.helpers.rememberSongDownloadState
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.viewmodels.PlayerViewModel
import com.example.melodist.viewmodels.SearchState
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.pages.ChartsPage
import com.metrolist.innertube.pages.ExplorePage
import com.metrolist.innertube.pages.MoodAndGenres
import lyrik.composeapp.generated.resources.Res
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.onHover

data class SearchScreenState(
    val uiState: SearchState = SearchState.Idle,
    val query: String = "",
    val suggestions: List<String> = emptyList(),
    val filter: YouTube.SearchFilter? = null,
    val searchHistory: List<SearchHistoryEntry> = emptyList(),
    val charts: ChartsPage? = null,
    val explore: ExplorePage? = null,
    val moodAndGenres: List<MoodAndGenres> = emptyList(),
)

data class SearchActions(
    val onQueryChange: (String) -> Unit,
    val onSearch: () -> Unit,
    val onFilterChange: (YouTube.SearchFilter?) -> Unit,
    val onLoadMore: () -> Unit,
    val onNavigate: (Route) -> Unit,
    val onDeleteHistoryEntry: (String) -> Unit,
    val onClearHistory: () -> Unit
)

@Composable
fun SearchScreenRoute(
    viewModel: SearchViewModel,
    onNavigate: (Route) -> Unit,
) {

    val playerViewModel = LocalPlayerViewModel.current

    val uiState by viewModel.uiState.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val charts by viewModel.charts.collectAsState()
    val explore by viewModel.explore.collectAsState()
    val moodAndGenres by viewModel.moodAndGenres.collectAsState()

    val state = SearchScreenState(
        uiState = uiState,
        query = query,
        suggestions = suggestions,
        filter = filter,
        searchHistory = searchHistory,
        charts = charts,
        explore = explore,
        moodAndGenres = moodAndGenres,
    )

    val actions = remember(viewModel, onNavigate) {
        SearchActions(
            onQueryChange = { viewModel.onQueryChange(it) },
            onSearch = { viewModel.search() },
            onFilterChange = { viewModel.onFilterChange(it) },
            onLoadMore = { viewModel.searchContinuation() },
            onNavigate = onNavigate,
            onDeleteHistoryEntry = { viewModel.deleteHistoryEntry(it) },
            onClearHistory = { viewModel.clearHistory() }
        )
    }

    SearchScreen(
        state = state,
        actions = actions,
        playerViewModel = playerViewModel
    )
}

@Composable
fun SearchScreen(
    state: SearchScreenState,
    actions: SearchActions,
    playerViewModel: PlayerViewModel
) {
    var active by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
    ) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SearchSection(
                query = state.query,
                active = active,
                suggestions = state.suggestions,
                searchHistory = state.searchHistory,
                onActiveChange = { active = it },
                onQueryChange = actions.onQueryChange,
                onSearch = {
                    actions.onSearch()
                    active = false
                },
                onDeleteHistoryEntry = actions.onDeleteHistoryEntry,
                onClearHistory = actions.onClearHistory
            )

            val hasSearchResults = when (state.uiState) {
                is SearchState.Success -> true
                is SearchState.SummarySuccess -> true
                else -> false
            }

            if (hasSearchResults) {
                FilterRow(
                    selectedFilter = state.filter,
                    onFilterSelected = actions.onFilterChange
                )
            }

            ResultsList(
                uiState = state.uiState,
                charts = state.charts,
                explore = state.explore,
                moodAndGenres = state.moodAndGenres,
                showFilterRow = false,
                filter = state.filter,
                onItemClick = { item ->
                    when (item) {

                        is SongItem -> {
                            playerViewModel.playEndpoint(WatchEndpoint(item.id))
                        }
                        else -> {
                            val route = when (item) {
                                is AlbumItem -> Route.Album(item.id)
                                is PlaylistItem -> Route.Playlist(item.id)
                                is ArtistItem -> Route.Artist(item.id)
                                else -> Route.Home
                            }
                            actions.onNavigate(route)
                        }
                    }
                },
                onMoodClick = { browseId, params -> actions.onNavigate(Route.YouTubeBrowse(browseId, params)) },
                onFilterChange = actions.onFilterChange,
                onLoadMore = actions.onLoadMore,
            )
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSection(
    query: String,
    active: Boolean,
    suggestions: List<String>,
    searchHistory: List<SearchHistoryEntry>,
    onActiveChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onDeleteHistoryEntry: (String) -> Unit,
    onClearHistory: () -> Unit
) {


    SearchBar(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(horizontal = if (active) 0.dp else 16.dp)
            .animateContentSize(),
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = { onSearch(query) },
                expanded = active,
                onExpandedChange = onActiveChange,
                    placeholder = {
                        Text(
                            stringResource(Res.string.search_placeholder),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.cd_clear))
                        }
                    }
                }
            )
        },
        expanded = active,
        onExpandedChange = onActiveChange,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (query.isEmpty()) {
                if (searchHistory.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionHeader(stringResource(Res.string.recent_searches))
                            Text(
                                text = stringResource(Res.string.clear_all),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onClearHistory() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    items(searchHistory, key = { it.query }) { entry ->
                        HistoryListItem(
                            query = entry.query,
                            onClick = {
                                onQueryChange(entry.query)
                                onSearch(entry.query)
                            },
                            onDelete = { onDeleteHistoryEntry(entry.query) }
                        )
                    }
                } else {
                    item {
                        SectionHeader(stringResource(Res.string.recent_searches))
                    }
                    item {
                        EmptyStateText()
                    }
                }
            } else {
                item {
                    SectionHeader(stringResource(Res.string.suggestions_title))
                }

                items(suggestions.take(8), key = { it }) { suggestion ->
                    SuggestionListItem(
                        suggestion = suggestion,
                        onClick = {
                            onQueryChange(suggestion)
                            onSearch(suggestion)
                        }
                    )
                }

                if (suggestions.isEmpty() && query.length >= 2) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 12.dp)
    )
}

@Composable
private fun SuggestionListItem(suggestion: String, onClick: () -> Unit) {
    var isHover by remember { mutableStateOf(false) }
    val bgColor = if(isHover) MaterialTheme.colorScheme.onSurface.copy(0.12f) else Color.Transparent
    ListItem(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .onHover{ isHover = it }
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick),
        headlineContent = {
            Text(
                text = suggestion,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium
                )
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.TrendingUp, // Icono más dinámico
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Default.NorthWest, // Flecha típica de "completar búsqueda"
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun HistoryListItem(query: String, onClick: () -> Unit, onDelete: () -> Unit) {

    var isHovered by remember { mutableStateOf(false) }
    val bgColor = if(isHovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f) else Color.Transparent

    ListItem(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .onHover { isHovered = it }
            .clickable(onClick = onClick)
            .background(bgColor),
        headlineContent = {
            Text(
                text = query,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium
                )
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.cd_delete),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun EmptyStateText() {
    Text(
        text = stringResource(Res.string.no_recent_searches),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
fun FilterRow(
    selectedFilter: YouTube.SearchFilter?,
    onFilterSelected: (YouTube.SearchFilter?) -> Unit
) {
    val filters = listOf(
        stringResource(Res.string.filter_all) to null,
        stringResource(Res.string.filter_videos) to YouTube.SearchFilter.FILTER_VIDEO,
        stringResource(Res.string.filter_songs) to YouTube.SearchFilter.FILTER_SONG,
        stringResource(Res.string.filter_albums) to YouTube.SearchFilter.FILTER_ALBUM,
        stringResource(Res.string.filter_artists) to YouTube.SearchFilter.FILTER_ARTIST,
        stringResource(Res.string.filter_playlists) to YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalScrollableRow(
            modifier = Modifier.fillMaxWidth(),
            state = rememberLazyListState(),
            contentPadding = PaddingValues(horizontal = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filters){ (label, f) ->
                val isSelected = selectedFilter == f
                FilterChip(
                    selected = isSelected,
                    onClick = { onFilterSelected(f) },
                    label = { Text(label) },
                    shape = RoundedCornerShape(50.dp),
                )

            }
        }
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ResultsList(
    uiState: SearchState,
    charts: ChartsPage?,
    explore: ExplorePage? = null,
    moodAndGenres: List<MoodAndGenres> = emptyList(),
    onItemClick: (YTItem) -> Unit,
    onMoodClick: (String, String?) -> Unit = { _, _ -> },
    showFilterRow: Boolean = true,
    filter: YouTube.SearchFilter?,
    onFilterChange: (YouTube.SearchFilter?) -> Unit,
    onLoadMore: () -> Unit,
) {
    val scrollable = rememberLazyListState()

    val items = when (uiState) {
        is SearchState.Success -> uiState.items
        else -> emptyList()
    }

    val summaries = when (uiState) {
        is SearchState.SummarySuccess -> uiState.summary.summaries
        else -> emptyList()
    }

    val shouldLoadMore = remember(uiState) {
        derivedStateOf {
            if (uiState !is SearchState.Success) return@derivedStateOf false

            val layoutInfo = scrollable.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem =
                layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0

            lastVisibleItem >= totalItems - 3 &&
                    !uiState.isLoadingMore &&
                    uiState.continuation != null
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            onLoadMore()
        }
    }

    when (uiState) {

        is SearchState.Loading -> {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    ChipRowSkeleton()
                }

                items(10) {
                    SongSkeleton()
                }
            }
        }

        is SearchState.Error -> {
            EmptyStateView(
                icon = Icons.Default.Close,
                message = stringResource(Res.string.something_went_wrong)
            )
        }

        SearchState.Idle -> {
            SearchChartsContent(
                charts = charts,
                explore = explore,
                moodAndGenres = moodAndGenres,
                onItemClick = onItemClick,
                onMoodClick = onMoodClick,
            )
        }

        else -> {

            val hasItems = when (uiState) {
                is SearchState.Success -> items.isNotEmpty()
                is SearchState.SummarySuccess -> summaries.isNotEmpty()
                else -> false
            }

            if (!hasItems) {
                EmptyStateView(
                    icon = Icons.Default.Search,
                    message = stringResource(Res.string.no_results)
                )
            } else {
                Box {

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        state = scrollable
                    ) {

                        if (showFilterRow) {
                            item {
                                FilterRow(
                                    selectedFilter = filter,
                                    onFilterSelected = onFilterChange
                                )
                            }
                        }

                        charts?.sections?.firstOrNull()?.let { section ->
                            item {
                                SearchChartPreview(section = section, onItemClick = onItemClick)
                            }
                        }

                        if (summaries.isNotEmpty()) {
                            summaries.forEach { summary ->
                                item(key = "header_${summary.title}") {
                                    SectionHeader(summary.title)
                                }
                                items(
                                    items = summary.items,
                                    key = { "item_${it.id}" }
                                ) { item ->
                                    val downloadViewModel = LocalDownloadViewModel.current
                                    val downloadState by if (item is SongItem) {
                                        rememberSongDownloadState(item.id, downloadViewModel)
                                    } else {
                                        remember { mutableStateOf(null) }
                                    }
                                    val source = if (item is SongItem && downloadState != null) {
                                        ItemContentSource.LOCAL
                                    } else {
                                        ItemContentSource.YOUTUBE
                                    }
                                    YoutubeListItem(
                                        item = item,
                                        source = source,
                                        onItemClick = onItemClick,
                                    )
                                }
                            }
                        } else {
                            items(
                                items = items,
                                key = { it.id }
                            ) { item ->
                                val downloadViewModel = LocalDownloadViewModel.current
                                val downloadState by if (item is SongItem) {
                                    rememberSongDownloadState(item.id, downloadViewModel)
                                } else {
                                    remember { mutableStateOf(null) }
                                }
                                val source = if (item is SongItem && downloadState != null) {
                                    ItemContentSource.LOCAL
                                } else {
                                    ItemContentSource.YOUTUBE
                                }
                                YoutubeListItem(
                                    item = item,
                                    source = source,
                                    onItemClick = onItemClick,
                                )
                            }
                        }

                        if (uiState is SearchState.Success && uiState.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularWavyProgressIndicator(
                                        stroke = Stroke(5F, cap = StrokeCap.Round),
                                    )
                                }
                            }
                        }
                    }

                    AppVerticalScrollbar(
                        state = scrollable,
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchChartsContent(
    charts: ChartsPage?,
    explore: ExplorePage? = null,
    moodAndGenres: List<MoodAndGenres> = emptyList(),
    onItemClick: (YTItem) -> Unit,
    onMoodClick: (String, String?) -> Unit = { _, _ -> },
) {
    if (charts == null && explore == null && moodAndGenres.isEmpty()) {
        EmptyStateView(Icons.AutoMirrored.Filled.TrendingUp, stringResource(Res.string.explore_trends))
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        charts?.sections?.take(3)?.forEach { section ->
            item {
                SearchChartPreview(section = section, onItemClick = onItemClick)
            }
        }

        explore?.newReleaseAlbums?.take(6)?.let { albums ->
            if (albums.isNotEmpty()) {
                item {
                    SearchAlbumGrid(
                        title = stringResource(Res.string.new_releases),
                        albums = albums,
                        onItemClick = onItemClick,
                    )
                }
            }
        }

        explore?.moodAndGenres?.let { moods ->
            if (moods.isNotEmpty()) {
                item {
                    SearchMoodGrid(
                        moods = moods,
                        onMoodClick = onMoodClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchAlbumGrid(
    title: String,
    albums: List<AlbumItem>,
    onItemClick: (YTItem) -> Unit,
) {
    val rowCount = 2
    val itemHeight = 64.dp
    val rowSpacing = 8.dp
    val columnWidth = 320.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Album, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        HorizontalGridLikeRow(
            items = albums,
            rows = rowCount,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            columnWidth = columnWidth,
            rowSpacing = rowSpacing,
            columnSpacing = 12.dp,
            itemKey = { it.id }
        ) { album ->
            YoutubeListItem(
                item = album,
                source = ItemContentSource.YOUTUBE,
                onItemClick = onItemClick,
                modifier = Modifier.fillMaxWidth().height(itemHeight)
            )
        }
    }
}

@Composable
private fun SearchMoodGrid(
    moods: List<MoodAndGenres.Item>,
    onMoodClick: (String, String?) -> Unit = { _, _ -> },
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
    ) {

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Filled.TrendingUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.size(8.dp))

            Text(
                text = stringResource(Res.string.moods_genres),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(12.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(120.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.heightIn(max = 500.dp)
        ) {
            items(moods.size) { index ->
                val mood = moods[index]
                val baseColor = Color(mood.stripeColor)

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(baseColor.copy(alpha = 0.15f))
                        .border(
                            width = 1.dp,
                            color = baseColor.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { onMoodClick(mood.endpoint.browseId, mood.endpoint.params) }
                        .padding(vertical = 20.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mood.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = baseColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchChartPreview(
    section: ChartsPage.ChartSection,
    onItemClick: (YTItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(8.dp))
            Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        section.items.take(5).forEachIndexed { index, item ->
            YoutubeListItem(
                item = item,
                source = ItemContentSource.YOUTUBE,
                onItemClick = onItemClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun EmptyStateView(icon: ImageVector, message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

