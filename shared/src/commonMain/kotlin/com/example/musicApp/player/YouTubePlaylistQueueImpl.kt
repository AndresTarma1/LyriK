package com.example.musicApp.player

import com.example.musicApp.models.MediaMetadata
import com.example.musicApp.models.toMediaMetadata
import com.example.musicApp.viewmodels.QueueSource
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class YouTubePlaylistQueueImpl(
    val playlistId: String,
    val playlistTitle: String,
    private val initialSongs: List<SongItem>,
    private val initialContinuation: String?,
    override var startIndex: Int = 0,
) : Queue {
    override val source: QueueSource = QueueSource.Playlist(playlistId, playlistTitle)
    override val initialItems: List<MediaMetadata> = initialSongs.map { it.toMediaMetadata() }

    var continuation: String? = initialContinuation
        private set
    private var retryCount = 0
    private val maxRetries = 3

    override val hasNextPage: Boolean = continuation != null

    override suspend fun getInitialStatus(): Result<QueueStatus> = withContext(Dispatchers.IO) {
        try {
            Result.success(QueueStatus(
                items = initialItems,
                hasNextPage = hasNextPage,
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun nextPage(): Result<List<MediaMetadata>> = withContext(Dispatchers.IO) {
        val currentContinuation = continuation ?: return@withContext Result.success(emptyList())
        var lastException: Throwable? = null

        for (attempt in 0..maxRetries) {
            try {
                val page = YouTube.playlistContinuation(currentContinuation).getOrThrow()
                continuation = page.continuation?.takeIf { it.isNotBlank() }
                retryCount = 0
                return@withContext Result.success(page.songs.map { it.toMediaMetadata() })
            } catch (e: Exception) {
                lastException = e
                retryCount++
                if (retryCount >= maxRetries) {
                    continuation = null
                }
            }
        }
        Result.failure(lastException ?: Exception("Failed to load next page"))
    }
}