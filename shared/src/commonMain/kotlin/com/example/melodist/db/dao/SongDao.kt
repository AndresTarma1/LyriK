package com.example.melodist.db.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.example.melodist.db.MelodistDatabase
import com.example.melodist.db.entities.SongEntity
import com.example.melodist.db.entities.SongWithRelations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

private fun Long?.toLocalDateTime(): LocalDateTime? =
    this?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC) }

private fun LocalDateTime?.toEpochMillis(): Long? =
    this?.atZone(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()

private fun buildSongEntity(
    id: String, title: String, duration: Long, thumbnailUrl: String?, albumId: String?,
    albumName: String?, explicit: Long, year: Long?, date: Long?, dateModified: Long?,
    liked: Long, likedDate: Long?, totalPlayTime: Long, inLibrary: Long?, dateDownload: Long?,
    isLocal: Long, libraryAddToken: String?, libraryRemoveToken: String?,
    lyricsOffset: Long, romanizeLyrics: Long, isAgeRestricted: Long,
    isDownloaded: Long, isUploaded: Long, isVideo: Long
) = SongEntity(
    id = id, title = title, duration = duration.toInt(),
    thumbnailUrl = thumbnailUrl, albumId = albumId, albumName = albumName,
    explicit = explicit != 0L, year = year?.toInt(),
    date = date.toLocalDateTime(), dateModified = dateModified.toLocalDateTime(),
    liked = liked != 0L, likedDate = likedDate.toLocalDateTime(),
    totalPlayTime = totalPlayTime, inLibrary = inLibrary.toLocalDateTime(),
    dateDownload = dateDownload.toLocalDateTime(), isLocal = isLocal != 0L,
    libraryAddToken = libraryAddToken, libraryRemoveToken = libraryRemoveToken,
    lyricsOffset = lyricsOffset.toInt(), romanizeLyrics = romanizeLyrics != 0L,
    isAgeRestricted = isAgeRestricted != 0L,
    isDownloaded = isDownloaded != 0L, isUploaded = isUploaded != 0L, isVideo = isVideo != 0L
)

class SongDao(private val database: MelodistDatabase) {

    fun allSongs(): Flow<List<SongEntity>> =
        database.songQueries.selectAll(::buildSongEntity)
            .asFlow()
            .mapToList(Dispatchers.IO)

    fun songsInLibrary(): Flow<List<SongEntity>> =
        database.songQueries.songsInLibrary { id, title, duration, thumbnailUrl, albumId,
                                               albumName, explicit, year, date, dateModified,
                                               liked, likedDate, totalPlayTime, inLibrary,
                                               dateDownload, isLocal, libraryAddToken,
                                               libraryRemoveToken, lyricsOffset, romanizeLyrics,
                                               isAgeRestricted, isDownloaded, isUploaded, isVideo ->
            buildSongEntity(id, title, duration, thumbnailUrl, albumId, albumName, explicit, year,
                date, dateModified, liked, likedDate, totalPlayTime, inLibrary, dateDownload,
                isLocal, libraryAddToken, libraryRemoveToken, lyricsOffset, romanizeLyrics,
                isAgeRestricted, isDownloaded, isUploaded, isVideo)
        }.asFlow().mapToList(Dispatchers.IO)

    fun songById(id: String): Flow<SongEntity?> =
        database.songQueries.songById(id, ::buildSongEntity)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)

    fun likedSongs(): Flow<List<SongEntity>> =
        database.songQueries.likedSongs { id, title, duration, thumbnailUrl, albumId,
                                           albumName, explicit, year, date, dateModified,
                                           liked, likedDate, totalPlayTime, inLibrary,
                                           dateDownload, isLocal, libraryAddToken,
                                           libraryRemoveToken, lyricsOffset, romanizeLyrics,
                                           isAgeRestricted, isDownloaded, isUploaded, isVideo ->
            buildSongEntity(id, title, duration, thumbnailUrl, albumId, albumName, explicit, year,
                date, dateModified, liked, likedDate, totalPlayTime, inLibrary, dateDownload,
                isLocal, libraryAddToken, libraryRemoveToken, lyricsOffset, romanizeLyrics,
                isAgeRestricted, isDownloaded, isUploaded, isVideo)
        }.asFlow().mapToList(Dispatchers.IO)

    fun downloadedSongs(): Flow<List<SongEntity>> =
        database.songQueries.downloadedSongs(::buildSongEntity)
            .asFlow()
            .mapToList(Dispatchers.IO)

    fun downloadedSongsCount(): Flow<Long> =
        database.songQueries.downloadedSongsCount()
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it ?: 0L }

    suspend fun insertSong(song: SongEntity) = withContext(Dispatchers.IO) {
        database.songQueries.insertSong(
            id = song.id, title = song.title, duration = song.duration.toLong(),
            thumbnailUrl = song.thumbnailUrl, albumId = song.albumId, albumName = song.albumName,
            explicit = if (song.explicit) 1L else 0L, year = song.year?.toLong(),
            date = song.date.toEpochMillis(), dateModified = song.dateModified.toEpochMillis(),
            liked = if (song.liked) 1L else 0L, likedDate = song.likedDate.toEpochMillis(),
            totalPlayTime = song.totalPlayTime, inLibrary = song.inLibrary.toEpochMillis(),
            dateDownload = song.dateDownload.toEpochMillis(),
            isLocal = if (song.isLocal) 1L else 0L,
            libraryAddToken = song.libraryAddToken, libraryRemoveToken = song.libraryRemoveToken,
            lyricsOffset = song.lyricsOffset.toLong(),
            romanizeLyrics = if (song.romanizeLyrics) 1L else 0L,
            isAgeRestricted = if (song.isAgeRestricted) 1L else 0L,
            isDownloaded = if (song.isDownloaded) 1L else 0L,
            isUploaded = if (song.isUploaded) 1L else 0L,
            isVideo = if (song.isVideo) 1L else 0L
        )
    }

    suspend fun updateSong(song: SongEntity) = withContext(Dispatchers.IO){
        database.songQueries.updateSong(
            id = song.id, title = song.title, duration = song.duration.toLong(),
            thumbnailUrl = song.thumbnailUrl, albumId = song.albumId, albumName = song.albumName,
            explicit = if (song.explicit) 1L else 0L, year = song.year?.toLong(),
            date = song.date.toEpochMillis(), dateModified = song.dateModified.toEpochMillis(),
            liked = if (song.liked) 1L else 0L, likedDate = song.likedDate.toEpochMillis(),
            totalPlayTime = song.totalPlayTime, inLibrary = song.inLibrary.toEpochMillis(),
            dateDownload = song.dateDownload.toEpochMillis(),
            isLocal = if (song.isLocal) 1L else 0L,
            libraryAddToken = song.libraryAddToken, libraryRemoveToken = song.libraryRemoveToken,
            lyricsOffset = song.lyricsOffset.toLong(),
            romanizeLyrics = if (song.romanizeLyrics) 1L else 0L,
            isAgeRestricted = if (song.isAgeRestricted) 1L else 0L,
            isDownloaded = if (song.isDownloaded) 1L else 0L,
            isUploaded = if (song.isUploaded) 1L else 0L,
            isVideo = if (song.isVideo) 1L else 0L
        )
    }

    suspend fun updateSongMetadata(song: SongEntity) = withContext(Dispatchers.IO){
        database.songQueries.updateSongMetadata(
            id = song.id, title = song.title,
            duration = song.duration.toLong(),
            thumbnailUrl = song.thumbnailUrl,
        )
    }

    suspend fun deleteSong(id: String) = withContext(Dispatchers.IO) {
        database.songQueries.deleteSong(id)
    }

    suspend fun updateSongDownloadStatus(songId: String, isDownloaded: Boolean, dateDownload: Long?) =
        withContext(Dispatchers.IO) {
            database.songQueries.updateSongDownloadStatus(
                isDownloaded = if (isDownloaded) 1L else 0L,
                dateDownload = dateDownload,
                id = songId
            )
        }

    fun quickPicks(now: Long = System.currentTimeMillis()): Flow<List<SongEntity>> =
        database.songQueries.quickPicks(now, ::buildSongEntity)
            .asFlow()
            .mapToList(Dispatchers.IO)

    fun searchSongs(query: String, limit: Long = 20): Flow<List<SongEntity>> =
        database.songQueries.searchSongs(query, limit, ::buildSongEntity)
            .asFlow()
            .mapToList(Dispatchers.IO)
}
