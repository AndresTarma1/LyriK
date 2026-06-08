package com.example.melodist.player

import com.example.melodist.models.MediaMetadata
import com.example.melodist.viewmodels.PlayerQueueCoordinator
import com.example.melodist.viewmodels.PlayerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class QueueManager(
    private val externalScope: CoroutineScope,
) {
    fun buildUiState(queue: Queue, shuffle: Boolean = false): PlayerUiState {
        val items = queue.initialItems
        val startIdx = queue.startIndex.coerceIn(0, items.lastIndex)

        val session = PlayerQueueCoordinator.collectionSession(queue.source, items, startIdx)
        var base = PlayerUiState(
            currentSong = session.currentSong(),
            queue = session.queueItems(),
            currentIndex = session.currentIndex,
            queueSource = session.source,
            isShuffled = false,
            queueSession = session,
        )
        if (shuffle) {
            base = PlayerQueueCoordinator.shuffleFromStart(base)
        }
        return base
    }

    suspend fun loadNextPage(queue: Queue): List<MediaMetadata> {
        val result = queue.nextPage()
        return result.getOrNull() ?: emptyList()
    }
}