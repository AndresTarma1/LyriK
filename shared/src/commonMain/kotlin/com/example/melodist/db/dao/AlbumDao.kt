package com.example.melodist.db.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.example.melodist.db.MelodistDatabase
import com.example.melodist.db.entities.AlbumEntity
import com.example.melodist.db.entities.AlbumWithArtists
import com.example.melodist.db.entities.AlbumWithSongs
import com.example.melodist.db.entities.SongWithRelations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
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
) = com.example.melodist.db.entities.SongEntity(
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

private fun mapArtist(
    id: String, name: String, thumbnailUrl: String?, channelId: String?,
    lastUpdateTime: Long, bookmarkedAt: Long?, isLocal: Long
) = com.example.melodist.db.entities.ArtistEntity(
    id = id, name = name, thumbnailUrl = thumbnailUrl, channelId = channelId,
    lastUpdateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastUpdateTime), ZoneOffset.UTC),
    bookmarkedAt = bookmarkedAt.toLocalDateTime(), isLocal = isLocal != 0L
)

private fun mapAlbum(
    id: String, playlistId: String?, title: String, year: Long?, thumbnailUrl: String?,
    themeColor: Long?, songCount: Long, duration: Long, explicit: Long, lastUpdateTime: Long,
    bookmarkedAt: Long?, likedDate: Long?, inLibrary: Long?, isLocal: Long, isUploaded: Long
) = AlbumEntity(
    id = id, playlistId = playlistId, title = title, year = year?.toInt(),
    thumbnailUrl = thumbnailUrl, themeColor = themeColor?.toInt(),
    songCount = songCount.toInt(), duration = duration.toInt(), explicit = explicit != 0L,
    lastUpdateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastUpdateTime), ZoneOffset.UTC),
    bookmarkedAt = bookmarkedAt.toLocalDateTime(), likedDate = likedDate.toLocalDateTime(),
    inLibrary = inLibrary.toLocalDateTime(), isLocal = isLocal != 0L, isUploaded = isUploaded != 0L
)

class AlbumDao(private val database: MelodistDatabase) {

    fun allAlbums(): Flow<List<AlbumEntity>> =
        database.albumQueries.selectAllAlbums(::mapAlbum)
            .asFlow()
            .mapToList(Dispatchers.IO)

    fun albumById(id: String): Flow<AlbumEntity?> =
        database.albumQueries.albumById(id, ::mapAlbum)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)

    fun bookmarkedAlbums(): Flow<List<AlbumEntity>> =
        database.albumQueries.bookmarkedAlbums { id, playlistId, title, year, thumbnailUrl,
                                                  themeColor, songCount, duration, explicit,
                                                  lastUpdateTime, bookmarkedAt, likedDate,
                                                  inLibrary, isLocal, isUploaded ->
            mapAlbum(id, playlistId, title, year, thumbnailUrl, themeColor, songCount, duration,
                explicit, lastUpdateTime, bookmarkedAt, likedDate, inLibrary, isLocal, isUploaded)
        }.asFlow().mapToList(Dispatchers.IO)

    suspend fun insertAlbum(album: AlbumEntity) = withContext(Dispatchers.IO) {
        database.albumQueries.insertAlbum(
            id = album.id, playlistId = album.playlistId, title = album.title,
            year = album.year?.toLong(), thumbnailUrl = album.thumbnailUrl,
            themeColor = album.themeColor?.toLong(), songCount = album.songCount.toLong(),
            duration = album.duration.toLong(), explicit = if (album.explicit) 1L else 0L,
            lastUpdateTime = album.lastUpdateTime.toEpochMillis() ?: System.currentTimeMillis(),
            bookmarkedAt = album.bookmarkedAt.toEpochMillis(),
            likedDate = album.likedDate.toEpochMillis(),
            inLibrary = album.inLibrary.toEpochMillis(),
            isLocal = if (album.isLocal) 1L else 0L,
            isUploaded = if (album.isUploaded) 1L else 0L
        )
    }

    suspend fun updateAlbumBookmark(id: String, bookmarkedAt: LocalDateTime?) = withContext(Dispatchers.IO) {
        database.albumQueries.updateAlbumBookmark(
            bookmarkedAt = bookmarkedAt.toEpochMillis(), id = id
        )
    }

    suspend fun deleteAlbum(id: String) = withContext(Dispatchers.IO) {
        database.albumQueries.deleteAlbum(id)
    }

    fun albumWithArtists(albumId: String): Flow<AlbumWithArtists?> =
        flow {
            val album = albumById(albumId).firstOrNull()
            emit(
                album?.let {
                    val maps = database.albumArtistMapQueries.selectByAlbum(albumId).executeAsList()
                    val artists = if (maps.isEmpty()) emptyList() else {
                        maps.mapNotNull { map ->
                            database.artistQueries.artistById(map.artistId, ::mapArtist).executeAsOneOrNull()
                        }
                    }
                    AlbumWithArtists(album = it, artists = artists)
                }
            )
        }

    fun albumWithSongs(albumId: String): Flow<AlbumWithSongs?> =
        flow {
            val album = albumById(albumId).firstOrNull()
            emit(
                album?.let {
                    val artistMaps = database.albumArtistMapQueries.selectByAlbum(albumId).executeAsList()
                    val artists = if (artistMaps.isEmpty()) emptyList() else {
                        artistMaps.mapNotNull { map ->
                            database.artistQueries.artistById(map.artistId, ::mapArtist).executeAsOneOrNull()
                        }
                    }
                    val songMaps = database.songAlbumMapQueries.selectByAlbum(albumId).executeAsList()
                    val songs = songMaps.mapNotNull { map ->
                        database.songQueries.songById(map.songId, ::buildSongEntity).executeAsOneOrNull()?.let { songEntity ->
                            val songArtistMaps = database.songArtistMapQueries.selectBySong(songEntity.id).executeAsList()
                            val songArtists = if (songArtistMaps.isEmpty()) emptyList() else {
                                songArtistMaps.mapNotNull { m ->
                                    database.artistQueries.artistById(m.artistId, ::mapArtist).executeAsOneOrNull()
                                }
                            }
                            SongWithRelations(
                                song = songEntity,
                                artists = songArtists,
                                album = it
                            )
                        }
                    }
                    AlbumWithSongs(album = it, artists = artists, songs = songs)
                }
            )
        }

    fun countDownloadedByAlbum(albumId: String): Long =
        database.songQueries.countDownloadedByAlbum(albumId).executeAsOne()
}
