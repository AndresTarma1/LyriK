package com.example.melodist.player

import com.example.melodist.db.DatabaseDao
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * State of a single download task.
 */
sealed class DownloadState {
    data object Queued : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    data object Completed : DownloadState()
    data class Failed(val error: String) : DownloadState()
    data object Cancelled : DownloadState()
}

/** Clase ligera que delega la lógica pesada al DownloadRepository. */
class DownloadService(
    streamResolver: AudioStreamResolver,
    databaseDao: DatabaseDao
) {
    private val repo = DownloadRepository(streamResolver, databaseDao)

    val downloadStates: StateFlow<Map<String, DownloadState>> = repo.downloadStates
    val pendingSongItems = repo.pendingSongItems

    fun downloadSong(song: SongItem) = repo.downloadSong(song)
    fun downloadAll(songs: List<SongItem>) = repo.downloadAll(songs)
    fun cancelDownload(songId: String) = repo.cancelDownload(songId)
    fun removeDownload(songId: String) = repo.removeDownload(songId)
    fun removeDownloads(songIds: List<String>) = repo.removeDownloads(songIds)
    fun clearCache() = repo.clearCache()
    fun isDownloaded(songId: String): Boolean = repo.isDownloaded(songId)

    fun release() = repo.release()

    companion object {
        fun getCachedFile(songId: String): File? = DownloadRepository.getCachedFile(songId)
        fun getCacheSizeBytes(): Long = DownloadRepository.getCacheSizeBytes()
        fun formatSize(bytes: Long): String = DownloadRepository.formatSize(bytes)
    }
}
