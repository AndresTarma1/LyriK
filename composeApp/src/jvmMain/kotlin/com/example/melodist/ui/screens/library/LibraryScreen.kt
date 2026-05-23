package com.example.melodist.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.melodist.navigation.Route
import com.example.melodist.ui.components.layout.HorizontalScrollableRow
import com.example.melodist.ui.screens.shared.displayName
import com.example.melodist.ui.screens.library.tabs.AlbumsTab
import com.example.melodist.ui.screens.library.tabs.ArtistsTab
import com.example.melodist.ui.screens.library.tabs.LibraryMixedTab
import com.example.melodist.ui.screens.library.tabs.PlaylistsTab
import com.example.melodist.viewmodels.LibrarySortOrder
import com.example.melodist.viewmodels.LibraryTab
import com.example.melodist.viewmodels.LibraryViewModel
import com.example.melodist.viewmodels.PlayerViewModel
import com.example.melodist.viewmodels.YtmLibraryFilter
import com.example.melodist.viewmodels.YtmLibraryState
import com.example.melodist.utils.LocalPlayerViewModel
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.WatchEndpoint
import lyrik.composeapp.generated.resources.Res
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

data class LibraryScreenState(
    val selectedTab: LibraryTab? = null,
    val searchQuery: String = "",
    val sortOrder: LibrarySortOrder = LibrarySortOrder.NAME_ASC,
    val selectedYtmFilter: YtmLibraryFilter? = null,
    val albums: List<AlbumItem> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val ytmState: YtmLibraryState = YtmLibraryState.Idle,
)

data class LibraryActions(
    val onTabSelected: (LibraryTab) -> Unit,
    val onNavigate: (Route) -> Unit,
    val onRemoveAlbum: (String) -> Unit,
    val onRemoveArtist: (String) -> Unit,
    val onRemovePlaylist: (String) -> Unit,
    val onQuickPlayAlbum: (browseId: String, playlistId: String?, title: String, onFallback: () -> Unit) -> Unit,
    val onQuickShuffleAlbum: (browseId: String, playlistId: String?, title: String, onFallback: () -> Unit) -> Unit,
    val onQuickPlayPlaylist: (playlistId: String, endpoint: WatchEndpoint?, title: String, onFallback: () -> Unit) -> Unit,
    val onQuickShufflePlaylist: (playlistId: String, endpoint: WatchEndpoint?, title: String, onFallback: () -> Unit) -> Unit,
    val onRefreshYtm: () -> Unit,
    val onCreatePlaylist: (String) -> Unit,
    val onSearchQueryChange: (String) -> Unit,
    val onClearSearch: () -> Unit,
    val onSortOrderChange: (LibrarySortOrder) -> Unit,
    val onYtmFilterChange: (YtmLibraryFilter?) -> Unit,
)

@Composable
fun LibraryScreenRoute(
    viewModel: LibraryViewModel,
    onNavigate: (Route) -> Unit,
) {
    val playerViewModel = LocalPlayerViewModel.current
    val selectedTab by viewModel.selectedTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val selectedYtmFilter by viewModel.selectedYtmFilter.collectAsState()
    val albums by viewModel.sortedFilteredAlbums.collectAsState()
    val artists by viewModel.sortedFilteredArtists.collectAsState()
    val playlists by viewModel.sortedFilteredPlaylists.collectAsState()
    val ytmState by viewModel.ytmState.collectAsState()

    val state = LibraryScreenState(
        selectedTab = selectedTab,
        searchQuery = searchQuery,
        sortOrder = sortOrder,
        selectedYtmFilter = selectedYtmFilter,
        albums = albums,
        artists = artists,
        playlists = playlists,
        ytmState = ytmState,
    )

    val actions = remember(viewModel, onNavigate, playerViewModel) {
        LibraryActions(
            onTabSelected = viewModel::selectTab,
            onNavigate = onNavigate,
            onRemoveAlbum = viewModel::removeAlbum,
            onRemoveArtist = viewModel::removeArtist,
            onRemovePlaylist = viewModel::removePlaylist,
            onQuickPlayAlbum = { browseId, playlistId, title, onFallback ->
                // Siempre obtener canciones directas (sin recomendaciones/automix)
                viewModel.resolveAlbumSongsForPlayback(
                    browseId = browseId,
                    onResolved = { songs ->
                        playerViewModel.playAlbum(
                            songs = songs, startIndex = 0,
                            browseId = browseId, title = title,
                        )
                    },
                    onFallback = onFallback,
                )
            },
            onQuickShuffleAlbum = { browseId, playlistId, title, onFallback ->
                viewModel.resolveAlbumSongsForPlayback(
                    browseId = browseId,
                    onResolved = { songs ->
                        playerViewModel.playAlbum(
                            songs = songs, startIndex = 0,
                            browseId = browseId, title = title,
                        )
                        playerViewModel.toggleShuffle()
                    },
                    onFallback = onFallback,
                )
            },
            onQuickPlayPlaylist = { playlistId, endpoint, title, onFallback ->
                playerViewModel.playPlaylistFromId(
                    playlistId = playlistId,
                    endpoint = endpoint,
                    title = title,
                    onEmpty = onFallback,
                )
            },
            onQuickShufflePlaylist = { playlistId, endpoint, title, onFallback ->
                playerViewModel.playPlaylistFromId(
                    playlistId = playlistId,
                    endpoint = endpoint,
                    title = title,
                    shuffle = true,
                    onEmpty = onFallback,
                )
            },
            onRefreshYtm = viewModel::refreshYtmLibrary,
            onCreatePlaylist = viewModel::createLocalPlaylist,
            onSearchQueryChange = viewModel::setSearchQuery,
            onClearSearch = viewModel::clearSearch,
            onSortOrderChange = viewModel::setSortOrder,
            onYtmFilterChange = viewModel::setYtmFilter,
        )
    }

    LibraryScreen(
        state = state,
        actions = actions,
        playerViewModel = playerViewModel,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryScreenState,
    actions: LibraryActions,
    playerViewModel: PlayerViewModel? = null,
) {
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0f),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.library_title),
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 32.sp),
                    )
                },
                actions = {
                    // TODO: search

                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Sort,
                                contentDescription = stringResource(Res.string.sort_label),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        androidx.compose.material3.DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            LibrarySortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.displayName()) },
                                    onClick = {
                                        actions.onSortOrderChange(order)
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (state.sortOrder == order) {
                                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { showCreatePlaylistDialog = true },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = stringResource(Res.string.cd_create_playlist),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.ytmState !is YtmLibraryState.Idle) {
                        IconButton(
                            onClick = actions.onRefreshYtm,
                            enabled = state.ytmState !is YtmLibraryState.Loading,
                        ) {
                            if (state.ytmState is YtmLibraryState.Loading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = stringResource(Res.string.refresh_ytm),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0f)),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LibraryTabRow(
                    selectedTab = state.selectedTab,
                    onTabSelected = actions.onTabSelected,
                    modifier = Modifier.weight(1f),
                )

                Box {
                    IconButton(
                        onClick = { showFilterMenu = true },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = stringResource(Res.string.cd_filter),
                            tint = if (state.selectedYtmFilter != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    androidx.compose.material3.DropdownMenu(
                        expanded = showFilterMenu,
                        onDismissRequest = { showFilterMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.filter_library), fontWeight = if (state.selectedYtmFilter == null) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { actions.onYtmFilterChange(null); showFilterMenu = false },
                            leadingIcon = {
                                if (state.selectedYtmFilter == null) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        )
                        YtmLibraryFilter.entries.forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(filter.displayName(), fontWeight = if (state.selectedYtmFilter == filter) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { actions.onYtmFilterChange(filter); showFilterMenu = false },
                                leadingIcon = {
                                    if (state.selectedYtmFilter == filter) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            when (state.selectedTab ?: LibraryTab.LIBRARY) {
                LibraryTab.ALBUMS -> AlbumsTab(
                    albums = state.albums,
                    ytmAlbums = (state.ytmState as? YtmLibraryState.Success)?.albums.orEmpty(),
                    isLoadingYtm = state.ytmState is YtmLibraryState.Loading,
                    onNavigate = actions.onNavigate,
                    onRemove = actions.onRemoveAlbum,
                    onQuickPlayAlbum = actions.onQuickPlayAlbum,
                    onQuickShuffleAlbum = actions.onQuickShuffleAlbum,
                )

                LibraryTab.ARTISTS -> ArtistsTab(
                    artists = state.artists,
                    ytmArtists = (state.ytmState as? YtmLibraryState.Success)?.artists.orEmpty(),
                    isLoadingYtm = state.ytmState is YtmLibraryState.Loading,
                    onNavigate = actions.onNavigate,
                    onRemove = actions.onRemoveArtist,
                )

                LibraryTab.PLAYLISTS -> PlaylistsTab(
                    playlists = state.playlists,
                    ytmPlaylists = (state.ytmState as? YtmLibraryState.Success)?.playlists.orEmpty(),
                    isLoadingYtm = state.ytmState is YtmLibraryState.Loading,
                    onNavigate = actions.onNavigate,
                    onRemove = actions.onRemovePlaylist,
                    playerViewModel = playerViewModel,
                    onQuickPlayPlaylist = actions.onQuickPlayPlaylist,
                    onQuickShufflePlaylist = actions.onQuickShufflePlaylist,
                )

                LibraryTab.LIBRARY -> LibraryMixedTab(
                    state = state,
                    onNavigate = actions.onNavigate,
                    playerViewModel = playerViewModel,
                    onQuickPlayAlbum = actions.onQuickPlayAlbum,
                    onQuickShuffleAlbum = actions.onQuickShuffleAlbum,
                    onQuickPlayPlaylist = actions.onQuickPlayPlaylist,
                    onQuickShufflePlaylist = actions.onQuickShufflePlaylist,
                )
            }
        }
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text(stringResource(Res.string.create_playlist_dialog)) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.playlist_name_label)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newPlaylistName.trim()
                        if (name.isNotEmpty()) {
                            actions.onCreatePlaylist(name)
                            newPlaylistName = ""
                            showCreatePlaylistDialog = false
                        }
                    },
                ) { Text(stringResource(Res.string.btn_create)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
}

@Composable
private fun LibraryTabRow(
    selectedTab: LibraryTab?,
    onTabSelected: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(
        LibraryTab.ALBUMS to stringResource(Res.string.tab_albums),
        LibraryTab.ARTISTS to stringResource(Res.string.tab_artists),
        LibraryTab.PLAYLISTS to stringResource(Res.string.tab_playlists),
    )

    HorizontalScrollableRow(
        modifier = modifier
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        state = androidx.compose.foundation.lazy.rememberLazyListState(),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        tabs.forEach { (tab, label) ->
            val isSelected = selectedTab == tab
            item {
                FilterChip(
                    selected = isSelected,
                    leadingIcon = { if(isSelected)Icon(Icons.Default.Check,  contentDescription = null) },
                    onClick = { onTabSelected(if (isSelected) LibraryTab.LIBRARY else tab) },
                    label = {
                        Text(
                            label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                    },
                    shape = RoundedCornerShape(50.dp),
                    border = null,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                )
            }
        }
    }
}
