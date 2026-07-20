package com.example.musicApp.player

import com.example.musicApp.models.MediaMetadata
import com.example.musicApp.viewmodels.QueueSource
import kotlinx.serialization.Serializable

interface Queue {
    val source: QueueSource
    val initialItems: List<MediaMetadata>
    var startIndex: Int
    val hasNextPage: Boolean

    suspend fun getInitialStatus(): Result<QueueStatus>
    suspend fun nextPage(): Result<List<MediaMetadata>>
}

@Serializable
data class QueueStatus(
    val items: List<MediaMetadata>,
    val hasNextPage: Boolean,
)

data class QueueState(
    val queue: Queue,
    val items: List<MediaMetadata>,
    val order: List<Int>,
    val currentIndex: Int,
    val isShuffled: Boolean,
)