package com.example.melodist.player

import com.example.melodist.data.AppDirs
import com.example.melodist.db.DatabaseDao
import com.example.melodist.db.entities.ArtistEntity
import com.example.melodist.db.entities.FormatEntity
import com.example.melodist.db.entities.SongEntity
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URI
import java.time.LocalDateTime
import java.util.logging.Logger
import kotlin.time.Duration.Companion.milliseconds

/**
 * Repository that contains the heavy lifting and backend logic for downloads.
 * DownloadService will act as a thin wrapper delegating to this class.
 */
class DownloadRepository(
    private val streamResolver: AudioStreamResolver,
    private val databaseDao: DatabaseDao
) {
    private val log = Logger.getLogger("DownloadRepository")

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val semaphore = Semaphore(3) // max 3 concurrent downloads

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    /** Tracks SongItem metadata for songs currently being downloaded (Queued/Downloading).
     *  Cleared once the download completes or fails. */
    private val _pendingSongItems = MutableStateFlow<Map<String, SongItem>>(emptyMap())
    val pendingSongItems: StateFlow<Map<String, SongItem>> = _pendingSongItems.asStateFlow()

    private val activeJobs = mutableMapOf<String, Job>()
    private val silentCancellationRequests = mutableSetOf<String>()

    init {
        // Scan existing cache on startup so songs already on disk show as Completed
        scanExistingCache()
    }

    private fun scanExistingCache() {
        scope.launch(Dispatchers.IO) {
            val files = cacheDir.listFiles() ?: return@launch
            val existing = files.filter { it.extension in listOf("m4a", "webm", "ogg") && it.length() > 0 }
                .associate { it.nameWithoutExtension to DownloadState.Completed }

            _downloadStates.update { it + existing }
        }
    }

    companion object {
        /** Chunk size for Range requests: 512 KB */
        private const val CHUNK_SIZE = 512 * 1024L

        /** Max retries per chunk before giving up. */
        private const val MAX_CHUNK_RETRIES = 3

        /** Back-off base delay between retries. */
        private const val RETRY_BASE_DELAY_MS = 500L

        /** Threshold for emitting progress updates (default 100KB) */
        private const val PROGRESS_THRESHOLD_BYTES = 100_000L

        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
        private const val REFERER = "https://music.youtube.com/"
        private const val ORIGIN = "https://music.youtube.com"

        private val cacheDir: File by lazy {
            AppDirs.songsDir.also { if (!it.exists()) it.mkdirs() }
        }

        /** Returns the local cached file for a given song ID, or null if not downloaded.
         * Checks both .m4a and .webm extensions.
         */
        fun getCachedFile(songId: String): File? {
            for (ext in listOf("m4a", "webm", "ogg")) {
                val file = File(cacheDir, "$songId.$ext")
                if (file.exists() && file.length() > 0) return file
            }
            return null
        }

        /** Returns the total cache size in bytes. */
        fun getCacheSizeBytes(): Long =
            cacheDir.listFiles()?.sumOf { it.length() } ?: 0L

        /** Formats bytes to human-readable string. */
        fun formatSize(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${'$'}{bytes / 1024} KB"
            bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }

        /** Determine file extension from mimeType. */
        private fun extensionForMime(mimeType: String): String = when {
            mimeType.contains("audio/mp4") -> "m4a"
            mimeType.contains("audio/webm") -> "webm"
            mimeType.contains("audio/ogg") -> "ogg"
            else -> "m4a"
        }
    }

    // ─── Download a single song ────────────────────────────

    fun downloadSong(song: SongItem) {
        scope.launch(Dispatchers.IO) {
            val songId = song.id

            val current = _downloadStates.value[songId]
            if (current is DownloadState.Downloading || current is DownloadState.Queued) return@launch

            if (getCachedFile(songId) != null) {
                updateSongDownloadStatus(songId, true, System.currentTimeMillis())
                _downloadStates.update { it + (songId to DownloadState.Completed) }
                return@launch
            }

            try {
                _downloadStates.update { it + (songId to DownloadState.Queued) }
                _pendingSongItems.update { it + (songId to song) }

                semaphore.withPermit {
                    if (!isActive) return@withPermit

                    val playbackData = YTPlayerutils.playerResponseForMetadata(songId).getOrNull()
                    val duration = playbackData?.videoDetails?.lengthSeconds?.toInt()

                    _downloadStates.update { it + (songId to DownloadState.Downloading(0f)) }

                    val stream = streamResolver.resolveAudioStream(songId)

                    val updatedSong = song.copy(duration = duration)
                    ensureSongInDb(updatedSong)

                    val ext = extensionForMime(stream.format.mimeType)
                    val targetFile = File(cacheDir, "$songId.$ext")
                    val partFile = File(cacheDir, "$songId.$ext.part")

                    val totalBytes = stream.format.contentLength ?: probeContentLength(stream.streamUrl)

                    downloadWithFallback(
                        streamUrl = stream.streamUrl,
                        partFile = partFile,
                        songId = songId,
                        totalBytes = totalBytes,
                    )

                    if (!isActive) throw CancellationException("Scope cancelado")

                    if (partFile.exists() && partFile.length() > 0) {
                        if (partFile.renameTo(targetFile)) {
                            saveFormatMetadata(songId, stream)
                            databaseDao.updateSongDownloadStatus(songId, true, System.currentTimeMillis())

                            _downloadStates.update { it + (songId to DownloadState.Completed) }
                        } else {
                            throw Exception("Error al mover el archivo final")
                        }
                    }
                }
            } catch (e: CancellationException) {
                onCancelDownloadExc(songId, e)
            } catch (e: Exception) {
                onException(songId, e)
            } finally {
                _pendingSongItems.update { it - songId }
            }
        }
    }



    suspend fun updateSongDownloadStatus(songId: String, isDownloaded: Boolean, dateDownload: Long?) {
        withContext(Dispatchers.IO) {
            databaseDao.updateSongDownloadStatus(songId, isDownloaded, dateDownload)
            yield()
        }
    }


    fun onException(songId: String, e: Exception) {
        log.warning($$"Download failed: $$songId — $$${e.message}")
        _downloadStates.update { it + (songId to DownloadState.Failed(e.message ?: "Error desconocido")) }
        _pendingSongItems.update { it - songId }
    }

    fun onCancelDownloadExc(songId: String, e: CancellationException) {
        val shouldHideCancelledState = synchronized(silentCancellationRequests) {
            silentCancellationRequests.remove(songId)
        }

        if (shouldHideCancelledState) {
            _downloadStates.update { it - songId }
        } else {
            _downloadStates.update { it + (songId to DownloadState.Cancelled) }
        }
        _pendingSongItems.update { it - songId }
        cleanupPartFiles(songId)

        throw e
    }

    fun downloadAll(songs: List<SongItem>) {
        songs.forEach { downloadSong(it) }
    }

    fun cancelDownload(songId: String, emitCancelledState: Boolean = true): Boolean {
        val hadJob = synchronized(activeJobs) {
            val job = activeJobs[songId]
            job?.cancel()
            activeJobs.remove(songId)
            job != null
        }

        if (!emitCancelledState) {
            synchronized(silentCancellationRequests) {
                silentCancellationRequests.add(songId)
            }
        } else {
            _downloadStates.update { it + (songId to DownloadState.Cancelled) }
        }

        cleanupPartFiles(songId)
        return hadJob
    }

    fun removeDownload(songId: String) {
        _downloadStates.update { it - songId }
        scope.launch(Dispatchers.IO) {
            val hadJob = cancelDownload(songId, emitCancelledState = false)
            getCachedFile(songId)?.delete()
            cleanupPartFiles(songId)
            databaseDao.updateSongDownloadStatus(songId, false, null)
            if (!hadJob) {
                synchronized(silentCancellationRequests) { silentCancellationRequests.remove(songId) }
            }
            _downloadStates.update { it - songId }
        }
        log.info("Removed download: $songId")
    }

    fun removeDownloads(songIds: List<String>) {
        _downloadStates.update { it - songIds.toSet() }
        scope.launch(Dispatchers.IO) {
            songIds.forEach { songId ->
                val hadJob = cancelDownload(songId, emitCancelledState = false)
                getCachedFile(songId)?.delete()
                cleanupPartFiles(songId)
                databaseDao.updateSongDownloadStatus(songId, false, null)
                if (!hadJob) {
                    synchronized(silentCancellationRequests) { silentCancellationRequests.remove(songId) }
                }
            }
            _downloadStates.update { states -> states - songIds.toSet() }
        }
        log.info("Removed downloads: ${songIds.size} songs")
    }

    fun clearCache() {
        synchronized(activeJobs) {
            activeJobs.values.forEach { it.cancel() }
            activeJobs.clear()
        }
        // Mark all downloaded songs as not downloaded in DB
        scope.launch {
            cacheDir.listFiles()?.forEach { file ->
                val songId = file.nameWithoutExtension.removeSuffix(".part")
                databaseDao.updateSongDownloadStatus(songId, false, null)
            }
            cacheDir.listFiles()?.forEach { it.delete() }
        }
        _downloadStates.update { emptyMap() }
        _pendingSongItems.update { emptyMap() }
        log.info("Cache cleared")
    }

    fun isDownloaded(songId: String): Boolean = getCachedFile(songId) != null

    fun release() { scope.cancel() }

    // ─── Chunked download with Range headers ───────────────

    private suspend fun downloadChunked(
        initialUrl: String,
        partFile: File,
        songId: String,
        totalBytes: Long
    ) {
        withContext(Dispatchers.IO) {
            var currentUrl = initialUrl
            var downloadedBytes = if (partFile.exists()) partFile.length() else 0L
            var urlRefreshCount = 0
            var lastProgressUpdate = 0L // throttle UI updates

            if (downloadedBytes >= totalBytes) return@withContext

            RandomAccessFile(partFile, "rw").use { raf ->
                raf.seek(downloadedBytes)

                while (downloadedBytes < totalBytes) {
                    ensureActive()

                    val rangeEnd = minOf(downloadedBytes + CHUNK_SIZE - 1, totalBytes - 1)

                    var chunkSuccess = false
                    for (retry in 1..MAX_CHUNK_RETRIES) {
                        try {
                            val bytesRead = downloadChunk(currentUrl, raf, downloadedBytes, rangeEnd)
                            downloadedBytes += bytesRead
                            chunkSuccess = true

                            // Throttle UI updates: only emit when threshold corresponds
                            if (downloadedBytes - lastProgressUpdate > PROGRESS_THRESHOLD_BYTES || downloadedBytes >= totalBytes) {
                                lastProgressUpdate = downloadedBytes
                                val progress = (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                                _downloadStates.update { it + (songId to DownloadState.Downloading(progress)) }
                            }
                            break
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            val is403 = e.message?.contains("403") == true

                            // On 403, the URL likely expired — re-resolve it
                            if (is403 && urlRefreshCount < 3) {
                                val newStream = streamResolver.resolveAudioStream(songId)
                                currentUrl = newStream.streamUrl
                                urlRefreshCount++
                                break
                            }

                            if (retry >= MAX_CHUNK_RETRIES) throw e
                            delay((RETRY_BASE_DELAY_MS * retry).milliseconds)
                        }
                    }

                    // If we broke out of the retry loop due to URL refresh, continue the outer loop
                    // (the while loop will re-attempt the same range with the new URL)
                    if (!chunkSuccess && urlRefreshCount > 0) {
                        // URL was refreshed, retry this chunk range
                        continue
                    }

                    if (!chunkSuccess) {
                        throw Exception("Failed to download chunk after $MAX_CHUNK_RETRIES retries")
                    }
                }
            }

            log.info("Chunked download complete: $songId ($downloadedBytes bytes)")
        }
    }

    private fun downloadChunk(
        url: String,
        raf: RandomAccessFile,
        rangeStart: Long,
        rangeEnd: Long
    ): Long {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            applyDownloadHeaders(connection)
            connection.setRequestProperty("Range", "bytes=$rangeStart-$rangeEnd")
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.connect()

            val code = connection.responseCode
            if (code !in listOf(200, 206)) {
                throw httpException(connection, "Range bytes=$rangeStart-$rangeEnd")
            }

            var totalRead = 0L
            connection.inputStream.use { input ->
                val buffer = ByteArray(16_384)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    raf.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                }
            }
            return totalRead
        } finally {
            connection.disconnect()
        }
    }

    // ─── Fallback single-request download ──────────────────

    private suspend fun downloadSingleRequest(url: String, targetFile: File, songId: String) {
        withContext(Dispatchers.IO) {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            try {
                applyDownloadHeaders(connection)
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.connect()

                if (connection.responseCode !in 200..299) {
                    throw httpException(connection)
                }

                val totalBytes = connection.contentLengthLong
                var downloadedBytes = 0L

                connection.inputStream.buffered(16_384).use { input ->
                    targetFile.outputStream().buffered(16_384).use { output ->
                        val buffer = ByteArray(16_384)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            ensureActive()
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val progress = if (totalBytes > 0) {
                                (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                            } else -1f
                            _downloadStates.update { it + (songId to DownloadState.Downloading(progress)) }
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    /**
     * Intenta primero la descarga por rangos y, si el servidor rechaza `Range`
     * con 403/416, cae a una descarga completa sin fragmentar.
     */
    private suspend fun downloadWithFallback(
        streamUrl: String,
        partFile: File,
        songId: String,
        totalBytes: Long?,
    ) {
        if (totalBytes == null || totalBytes <= 0) {
            downloadSingleRequest(streamUrl, partFile, songId)
            return
        }

        try {
            downloadChunked(streamUrl, partFile, songId, totalBytes)
        } catch (e: Exception) {
            val message = e.message.orEmpty()
            val rangeRejected = message.contains("403") || message.contains("416")

            if (!rangeRejected) throw e

            log.warning("Range rechazado para $songId, reintentando sin fragmentar: $message")
            if (partFile.exists()) partFile.delete()

            val refreshedStream = runCatching { streamResolver.resolveAudioStream(songId) }
                .getOrNull()

            val fallbackUrl = refreshedStream?.streamUrl ?: streamUrl
            downloadSingleRequest(fallbackUrl, partFile, songId)
        }
    }

    private fun applyDownloadHeaders(connection: HttpURLConnection) {
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Referer", REFERER)
        connection.setRequestProperty("Origin", ORIGIN)
        connection.setRequestProperty("Accept", "*/*")
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        connection.setRequestProperty("Cache-Control", "no-cache")

        YouTube.cookie?.takeIf { it.isNotBlank() }?.let { cookie ->
            connection.setRequestProperty("Cookie", cookie)
        }
    }

    private fun httpException(connection: HttpURLConnection, rangeInfo: String? = null): Exception {
        val body = runCatching {
            connection.errorStream?.bufferedReader()?.use { it.readText().take(500) }
        }.getOrNull()?.takeIf { it.isNotBlank() }

        val prefix = if (rangeInfo != null) "HTTP ${connection.responseCode} for $rangeInfo" else "HTTP ${connection.responseCode}: ${connection.responseMessage}"
        return Exception(
            if (body == null) prefix else "$prefix. Body: $body"
        )
    }

    // ─── Helpers ───────────────────────────────────────────

    private fun probeContentLength(url: String): Long? {
        return try {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "HEAD"
                applyDownloadHeaders(connection)
                connection.connectTimeout = 10_000
                connection.connect()
                val length = connection.contentLengthLong
                if (length > 0) length else null
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            log.warning("HEAD probe failed: ${e.message}")
            null
        }
    }

    private suspend fun ensureSongInDb(song: SongItem) {
        val existing = databaseDao.songById(song.id).firstOrNull()
        if (existing == null) {

            databaseDao.insertSong(
                SongEntity(
                    id = song.id,
                    title = song.title,
                    duration = song.duration ?: -1,
                    thumbnailUrl = song.thumbnail,
                    albumId = song.album?.id,
                    albumName = song.album?.name,
                    explicit = song.explicit,
                )
            )
        } else {
            databaseDao.updateSongMetadata(
                SongEntity(
                    id = song.id,
                    title = song.title,
                    duration = song.duration ?: existing.duration,
                    thumbnailUrl = song.thumbnail,
                )
            )
        }
        // Always ensure artists and song-artist mappings exist
        song.artists.forEachIndexed { index, artist ->
            val artistId = artist.id ?: $$"unknown_${artist.name.hashCode()}"
            val existingArtist = databaseDao.artistById(artistId).firstOrNull()
            if (existingArtist == null) {
                databaseDao.insertArtist(
                    ArtistEntity(
                        id = artistId,
                        name = artist.name,
                        thumbnailUrl = null,
                        channelId = artist.id,
                        lastUpdateTime = LocalDateTime.now(),
                        bookmarkedAt = null,
                        isLocal = false
                    )
                )
            }
            try {
                databaseDao.insertSongArtistMap(song.id, artistId, index)
            } catch (_: Exception) {
                // Map already exists — ignore
            }
        }
    }

    private suspend fun saveFormatMetadata(songId: String, stream: YTPlayerutils.PlaybackData) {
        databaseDao.insertFormat(
            FormatEntity(
                id = songId,
                itag = stream.format.itag,
                mimeType = stream.format.mimeType.split(";")[0],
                codecs = stream.format.mimeType.split("codecs=")[1].removeSurrounding("\""),
                bitrate = stream.format.bitrate,
                sampleRate = stream.format.audioSampleRate,
                contentLength = stream.format.contentLength ?: 0L,
                loudnessDb = stream.audioConfig?.loudnessDb,
                playbackUrl = null // don't persist URL — it expires
            )
        )
    }

    private fun cleanupPartFiles(songId: String) {
        for (ext in listOf("m4a", "webm", "ogg")) {
            File(cacheDir, "$songId.$ext.part").delete()
        }
    }
}

