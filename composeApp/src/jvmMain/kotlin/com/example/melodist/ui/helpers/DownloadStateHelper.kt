package com.example.melodist.ui.helpers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.example.melodist.player.DownloadState
import com.example.melodist.viewmodels.DownloadViewModel

/**
 * Helper composable that observes the download state of a single song.
 * This prevents observing the entire global state map in parent lists,
 * reducing recompositions significantly during mass downloads.
 */
@Composable
fun rememberSongDownloadState(
    songId: String,
    downloadViewModel: DownloadViewModel
): State<DownloadState?> {
    // Remember the flow so we don't recreate it on every recomposition
    val flow = remember(songId, downloadViewModel) {
        downloadViewModel.downloadStateFlow(songId)
    }
    // Collect specific song state, defaulting to null
    return flow.collectAsState(initial = null)
}
