package com.example.melodist.ui.screens

import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.unit.dp
import com.example.melodist.navigation.Route
import com.example.melodist.ui.components.PlaylistScreenSkeleton
import com.example.melodist.ui.screens.playlist.PlaylistLayout
import com.example.melodist.viewmodels.PlaylistState
import com.example.melodist.viewmodels.PlaylistViewModel
import com.metrolist.innertube.models.SongItem
import com.example.melodist.ui.components.artwork.ArtworkColors
import com.example.melodist.ui.components.artwork.rememberArtworkColors
import lyrik.composeapp.generated.resources.Res
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

data class PlaylistScreenState(
    val songs: List<SongItem> = emptyList(),
    val hasMore: Boolean = false,
    val isSaved: Boolean = false,
    val isSaving: Boolean = false,
    val isLoadingForPlay: Boolean = false,
)


data class PlaylistActions(
    val onBack: () -> Unit,
    val onNavigate: (Route) -> Unit,
    val onToggleSave: () -> Unit,
    val onPlay: () -> Unit,
    val onShuffle: () -> Unit,
    val onLoadMore: () -> Unit,
    val onDownloadPlaylist: () -> Unit,
    val onPlaySong: (Int) -> Unit,
    val onRemoveSongFromPlaylist: ((String) -> Unit)? = null,
    val onEditCover: (() -> Unit)? = null,
    val isLocalPlaylist: Boolean = false
)

@Composable
fun PlaylistScreenRoute(
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
    viewModel: PlaylistViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()

    val successState = uiState as? PlaylistState.Success

    LaunchedEffect(successState?.playlistPage?.playlist?.id) {
        if (successState?.playlistPage?.playlist?.id == "LOCAL_DOWNLOADS") {
            viewModel.refreshLocalDownloadsPlaylist()
        }
    }

    // Local playlists are always editable; a saved (bookmarked) YouTube playlist becomes editable
    // too once its songs are cached locally. isSaved can change while this screen is open (the
    // bookmark button), so it must be in the remember key, unlike the playlist's immutable id.
    // Auto-generated shelves (Mixes/Radios, id starts with "RD"; "SE" saved-episodes) are never
    // really editable on YouTube's side — don't offer removal for those, even if bookmarked.
    // "LM" (Liked Music) is the one exception: PlaylistViewModel.removeSongFromPlaylist maps
    // removal there to a real unlike, so it stays editable.
    val currentPlaylistId = successState?.playlistPage?.playlist?.id
    val isNonEditableAutoPlaylist = currentPlaylistId != null &&
        (currentPlaylistId == "SE" || currentPlaylistId.startsWith("RD"))
    val canEditPlaylist = !isNonEditableAutoPlaylist &&
        (currentPlaylistId?.startsWith("LOCAL_") == true || successState?.isSaved == true)

    val actions = remember(viewModel, successState != null, canEditPlaylist) {
        PlaylistActions(
            onBack = onBack,
            onNavigate = onNavigate,
            onToggleSave = { viewModel.toggleSave() },
            onLoadMore = { viewModel.loadMoreSongs() },
            onPlay = { viewModel.playAllSongs(shuffle = false) },
            onShuffle = { viewModel.playAllSongs(shuffle = true) },
            onDownloadPlaylist = { viewModel.downloadPlaylist() },
            onPlaySong = { index -> viewModel.playSongFromPlaylist(index) },
            onRemoveSongFromPlaylist = if (canEditPlaylist) {
                { songId -> viewModel.removeSongFromPlaylist(songId) }
            } else null,
            onEditCover = { viewModel.pickAndSetCustomThumbnail() },
            // Also true for a saved (bookmarked) YouTube playlist: its songs are cached locally
            // too, and removal pushes to the real playlist (see PlaylistViewModel.removeSongFromPlaylist).
            isLocalPlaylist = canEditPlaylist
        )
    }

    val songs by viewModel.songs.collectAsState()
    val hasMoreSongs by viewModel.hasMoreSongs.collectAsState()


    val state = PlaylistScreenState(
        songs = songs,
        hasMore = hasMoreSongs,
        isSaved = successState?.isSaved ?: false,
        isSaving = successState?.isSaving ?: false,
        isLoadingForPlay = successState?.isLoadingForPlay ?: false,
    )


    PlaylistScreen(
        uiState = uiState,
        state = state,
        actions = actions
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlaylistScreen(
    uiState: PlaylistState,
    state: PlaylistScreenState,
    actions: PlaylistActions
) {
    val thumbnailUrl = (uiState as? PlaylistState.Success)?.playlistPage?.playlist?.thumbnail
    val artworkColors = rememberArtworkColors(thumbnailUrl)
    val isReady = uiState is PlaylistState.Success && (thumbnailUrl == null || artworkColors != ArtworkColors.Default)
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent {
                if (it.key == Key.Escape && it.type == KeyEventType.KeyUp) {
                    actions.onBack()
                    true
                } else {
                    false
                }
            },
    ) {
        if (!isReady && uiState !is PlaylistState.Error) {
             PlaylistScreenSkeleton()
        } else {
            when (uiState) {
                is PlaylistState.Success -> PlaylistLayout(
                    playlistPage = uiState.playlistPage,
                    state = state,
                    actions = actions
                )
                is PlaylistState.Error -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(uiState.message, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        IconButton(
            onClick = actions.onBack,
            modifier = Modifier.padding(8.dp).align(Alignment.TopStart)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(Res.string.back),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
