package com.example.melodist.ui.components.context

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.rememberCursorPositionProvider
import com.example.melodist.models.toMediaMetadata
import com.example.melodist.ui.components.song.AddToPlaylistDialog
import com.example.melodist.ui.helpers.rememberSongDownloadState
import com.example.melodist.ui.helpers.rememberSongLikedState
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.utils.LocalPlaylistsViewModel
import com.example.melodist.utils.LocalSnackbarHostState
import com.example.melodist.utils.LocalSnackbarScope
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import kotlinx.coroutines.launch
import lyrik.composeapp.generated.resources.Res
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString

@Composable
fun SongContextMenuPopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    song: SongItem,
    showQueueActions: Boolean = true,
    onRemoveFromLibrary: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
) {
    val playerViewModel = LocalPlayerViewModel.current
    val downloadViewModel = LocalDownloadViewModel.current
    val playlistsViewModel = LocalPlaylistsViewModel.current
    val liked = rememberSongLikedState(song.id, playerViewModel)
    val downloadState by rememberSongDownloadState(song.id, downloadViewModel)
    val snackbar = LocalSnackbarHostState.current
    val scope = LocalSnackbarScope.current

    var showPlaylistDialog by remember { mutableStateOf(false) }

    if (expanded) {
        val cursorPositionProvider = rememberCursorPositionProvider()

        Popup(
            onDismissRequest = onDismiss,
            popupPositionProvider = cursorPositionProvider,
            properties = PopupProperties(focusable = true)
        ) {
            SongContextMenuContent(
                song = song,
                liked = liked,
                downloadState = downloadState,
                showQueueActions = showQueueActions,
                showRemoveFromLibrary = onRemoveFromLibrary != null,
                showRemoveFromPlaylist = onRemoveFromPlaylist != null,
                onAction = { action ->
                    when (action) {
                        SongMenuAction.StartRadio -> {
                            val endpoint = song.endpoint
                            val preview = song.toMediaMetadata()
                            if (endpoint != null) {
                                playerViewModel.playEndpoint(endpoint, previewSong = preview)
                            } else {
                                playerViewModel.playEndpoint(WatchEndpoint(videoId = song.id), previewSong = preview)
                            }
                            onDismiss()
                        }
                        SongMenuAction.ToggleLike -> {
                            playerViewModel.toggleLikeForSong(song)
                            onDismiss()
                        }
                        SongMenuAction.Download -> {
                            downloadViewModel.downloadSong(song)
                            scope.launch { snackbar.showSnackbar(getString(Res.string.snackbar_added_to_downloads, song.title)) }
                            onDismiss()
                        }
                        SongMenuAction.CancelDownload -> {
                            downloadViewModel.cancelDownload(song.id)
                            scope.launch { snackbar.showSnackbar(getString(Res.string.snackbar_download_cancelled)) }
                            onDismiss()
                        }
                        SongMenuAction.RemoveDownload -> {
                            downloadViewModel.removeDownload(song.id)
                            scope.launch { snackbar.showSnackbar(getString(Res.string.snackbar_download_removed)) }
                            onDismiss()
                        }
                        SongMenuAction.PlayNext -> {
                            playerViewModel.playNextResolved(song)
                            scope.launch { snackbar.showSnackbar(getString(Res.string.snackbar_play_next, song.title)) }
                            onDismiss()
                        }
                        SongMenuAction.AddToQueue -> {
                            playerViewModel.addToQueueResolved(song)
                            scope.launch { snackbar.showSnackbar(getString(Res.string.snackbar_added_to_queue, song.title)) }
                            onDismiss()
                        }
                        SongMenuAction.AddToPlaylist -> {
                            showPlaylistDialog = true
                        }
                        SongMenuAction.RemoveFromLibrary -> {
                            onRemoveFromLibrary?.invoke()
                            onDismiss()
                        }
                        SongMenuAction.RemoveFromPlaylist -> {
                            onRemoveFromPlaylist?.invoke()
                            scope.launch { snackbar.showSnackbar(getString(Res.string.snackbar_removed_from_playlist, song.title)) }
                            onDismiss()
                        }
                    }
                }
            )
        }
    }

    if (showPlaylistDialog) {
        AddToPlaylistDialog(
            song = song,
            playlistsViewModel = playlistsViewModel,
            onDismiss = {
                showPlaylistDialog = false
                onDismiss()
            }
        )
    }
}

@Composable
fun CollectionContextMenuPopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    title: String,
    isPlaylist: Boolean,
    onOpen: () -> Unit,
    onPlay: (() -> Unit)? = null,
    onShuffle: (() -> Unit)? = null,
    onRemoveFromLibrary: (() -> Unit)? = null,
) {
    if (!expanded) return

    val cursorPositionProvider = rememberCursorPositionProvider()

    Popup(
        onDismissRequest = onDismiss,
        popupPositionProvider = cursorPositionProvider,
        properties = PopupProperties(focusable = true)
    ) {
        CollectionContextMenuContent(
            title = title,
            isPlaylist = isPlaylist,
            onOpen = { onOpen(); onDismiss() },
            onPlay = onPlay?.let { { it(); onDismiss() } },
            onShuffle = onShuffle?.let { { it(); onDismiss() } },
            onRemoveFromLibrary = onRemoveFromLibrary?.let { { it(); onDismiss() } },
        )
    }
}
