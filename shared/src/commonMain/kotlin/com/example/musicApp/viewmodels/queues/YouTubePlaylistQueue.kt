package com.example.musicApp.viewmodels.queues

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class YouTubePlaylistQueue(
    val playlistId: String,
    val playlistTitle: String,
    val initialSongs: List<SongItem>,
    val initialContinuation: String?,
    val startIndex: Int = 0,
) {
    var continuation: String? = initialContinuation
        private set
    private var retryCount = 0
    private val maxRetries = 3

    fun hasNextPage(): Boolean = continuation != null

    suspend fun loadNextPage(): List<SongItem> {
        val currentContinuation = continuation ?: return emptyList()
        var lastException: Throwable? = null

        for (attempt in 0..maxRetries) {
            try {
                val page = YouTube.playlistContinuation(currentContinuation).getOrThrow()
                continuation = page.continuation?.takeIf { it.isNotBlank() }
                retryCount = 0
                return page.songs
            } catch (e: Exception) {
                lastException = e
                retryCount++
                if (retryCount >= maxRetries) {
                    continuation = null
                }
                delay((500L * (attempt + 1)).milliseconds)
            }
        }
        throw lastException ?: Exception("Failed to load next page")
    }
}
