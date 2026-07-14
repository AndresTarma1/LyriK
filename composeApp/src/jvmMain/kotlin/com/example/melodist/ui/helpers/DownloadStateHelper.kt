package com.example.melodist.ui.helpers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.example.melodist.download.DownloadState
import com.example.melodist.viewmodels.DownloadViewModel

/**
 * Composable auxiliar que observa el estado de descarga de una sola canción.
 * Esto evita observar el mapa de estado global completo en listas padre,
 * reduciendo significativamente las recomposiciones durante descargas masivas.
 */
@Composable
fun rememberSongDownloadState(
    songId: String,
    downloadViewModel: DownloadViewModel
): State<DownloadState?> {
    // Recordamos el flow para no recrearlo en cada recomposición
    val flow = remember(songId, downloadViewModel) {
        downloadViewModel.downloadStateFlow(songId)
    }
    // Recolectamos el estado de la canción específica, con null como valor predeterminado
    return flow.collectAsState(initial = null)
}
