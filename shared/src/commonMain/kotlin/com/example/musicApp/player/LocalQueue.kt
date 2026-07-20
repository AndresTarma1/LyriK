package com.example.musicApp.player

import com.example.musicApp.models.MediaMetadata
import com.example.musicApp.viewmodels.QueueSource

class LocalQueue(
    override val source: QueueSource,
    override val initialItems: List<MediaMetadata>,
    override var startIndex: Int = 0,
) : Queue {
    override val hasNextPage: Boolean = false

    override suspend fun getInitialStatus(): Result<QueueStatus> = Result.success(QueueStatus(
        items = initialItems,
        hasNextPage = false,
    ))

    override suspend fun nextPage(): Result<List<MediaMetadata>> = Result.success(emptyList())
}