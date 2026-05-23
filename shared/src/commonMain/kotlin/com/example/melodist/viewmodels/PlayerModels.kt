package com.example.melodist.viewmodels

import com.example.melodist.player.PlaybackState
import com.example.melodist.models.MediaMetadata
import com.example.melodist.viewmodels.queues.YouTubePlaylistQueue
import com.metrolist.innertube.models.WatchEndpoint
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Describes how the queue was built.
 */
@Serializable
sealed class QueueSource {
    /** From an album — queue = album songs */
    @Serializable
    data class Album(val browseId: String, val title: String) : QueueSource()

    /** From a playlist — queue = playlist songs */
    @Serializable
    data class Playlist(val playlistId: String, val title: String) : QueueSource()

    /** From search / home — queue = related songs via YouTube.next() */
    @Serializable
    data class Single(val videoId: String) : QueueSource()

    /** Manually assembled (library, etc.) */
    @Serializable
    data object Custom : QueueSource()
}

@Serializable
data class QueueSession(
    val source: QueueSource = QueueSource.Custom,
    val items: List<MediaMetadata> = emptyList(),
    val order: List<Int> = emptyList(),
    val currentIndex: Int = -1,
    val continuation: String? = null,
    val endpoint: WatchEndpoint? = null,
    @Transient val playlistQueue: YouTubePlaylistQueue? = null,
) {
    fun currentSong(): MediaMetadata? = order.getOrNull(currentIndex)?.let(items::getOrNull)

    fun queueItems(): List<MediaMetadata> = order.mapNotNull { items.getOrNull(it) }

    fun naturalOrder(): List<Int> = items.indices.toList()
}

data class PlayerUiState(
    val currentSong: MediaMetadata? = null,
    val queue: List<MediaMetadata> = emptyList(),
    val currentIndex: Int = -1,
    val playbackState: PlaybackState = PlaybackState.IDLE,
    // positionMs y durationMs se mantienen aquí para compatibilidad con
    // los composables del player, pero ya NO se actualizan en _uiState cada
    // segundo — se leen desde progressState en los componentes que los necesitan.
    val volume: Int = 100,
    val queueSource: QueueSource? = null,
    val isShuffled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val error: String? = null,
    val queueSession: QueueSession = QueueSession(),
)

/**
 * Estado de progreso separado: se emite cada segundo mientras reproduce.
 * Al tenerlo en un StateFlow distinto, los componentes que NO muestran
 * la barra de progreso (NavigationRail, listados, etc.) NO se recomponen
 * con cada tick del reproductor — esto elimina el principal culpable del
 * alto consumo de CPU/RAM (~800MB en CoroutineScheduler).
 */
data class PlayerProgressState(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

enum class RepeatMode { OFF, ALL, ONE }
