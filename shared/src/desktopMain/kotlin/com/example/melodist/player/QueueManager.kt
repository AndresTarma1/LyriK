package com.example.melodist.player

import com.example.melodist.models.MediaMetadata
import com.example.melodist.viewmodels.PlayerQueueCoordinator
import com.example.melodist.viewmodels.PlayerUiState

class QueueManager {
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

    /**
     * Checks if we should auto-load more songs and loads them if needed.
     * Call this from next() when near the end of the queue.
     */
    suspend fun checkAndLoadMore(queue: Queue, currentIndex: Int, itemsCount: Int): List<MediaMetadata> {
        if (!queue.hasNextPage) return emptyList()
        if (currentIndex < itemsCount - 3) return emptyList()
        
        return loadNextPage(queue)
    }

}