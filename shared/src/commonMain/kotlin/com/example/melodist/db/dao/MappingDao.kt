package com.example.melodist.db.dao

import com.example.melodist.db.MelodistDatabase
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class MappingDao(private val database: MelodistDatabase) {

    suspend fun insertSongArtistMap(songId: String, artistId: String, position: Int) =
        database.songArtistMapQueries.insertSongArtistMap(songId, artistId, position.toLong())

    suspend fun insertSongAlbumMap(songId: String, albumId: String, index: Int) =
        database.songAlbumMapQueries.insertSongAlbumMap(songId, albumId, index.toLong())

    suspend fun insertAlbumArtistMap(albumId: String, artistId: String, order: Int) =
        database.albumArtistMapQueries.insertAlbumArtistMap(albumId, artistId, order.toLong())

    suspend fun insertPlaylistSongMap(playlistId: String, songId: String, position: Int, setVideoId: String? = null) =
        database.playlistSongMapQueries.insertPlaylistSongMap(playlistId, songId, position.toLong(), setVideoId)

    fun songIdsInPlaylist(playlistId: String): List<String> =
        database.playlistSongMapQueries.selectByPlaylist(playlistId).executeAsList().map { it.songId }

    private fun mapArtist(
        id: String, name: String, thumbnailUrl: String?, channelId: String?,
        lastUpdateTime: Long, bookmarkedAt: Long?, isLocal: Long
    ) = com.example.melodist.db.entities.ArtistEntity(
        id = id, name = name, thumbnailUrl = thumbnailUrl, channelId = channelId,
        lastUpdateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastUpdateTime), ZoneOffset.UTC),
        bookmarkedAt = bookmarkedAt?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC) },
        isLocal = isLocal != 0L
    )

    private fun mapAlbum(
        id: String, playlistId: String?, title: String, year: Long?, thumbnailUrl: String?,
        themeColor: Long?, songCount: Long, duration: Long, explicit: Long, lastUpdateTime: Long,
        bookmarkedAt: Long?, likedDate: Long?, inLibrary: Long?, isLocal: Long, isUploaded: Long
    ) = com.example.melodist.db.entities.AlbumEntity(
        id = id, playlistId = playlistId, title = title, year = year?.toInt(),
        thumbnailUrl = thumbnailUrl, themeColor = themeColor?.toInt(),
        songCount = songCount.toInt(), duration = duration.toInt(), explicit = explicit != 0L,
        lastUpdateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastUpdateTime), ZoneOffset.UTC),
        bookmarkedAt = bookmarkedAt?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC) },
        likedDate = likedDate?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC) },
        inLibrary = inLibrary?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC) },
        isLocal = isLocal != 0L, isUploaded = isUploaded != 0L
    )

    fun artistsForSong(songId: String): List<com.example.melodist.db.entities.ArtistEntity> {
        val maps = database.songArtistMapQueries.selectBySong(songId).executeAsList()
        if (maps.isEmpty()) return emptyList()
        return maps.mapNotNull { map ->
            database.artistQueries.artistById(map.artistId, ::mapArtist).executeAsOneOrNull()
        }
    }

    fun albumForSong(songId: String): com.example.melodist.db.entities.AlbumEntity? {
        val map = database.songAlbumMapQueries.selectBySong(songId).executeAsOneOrNull() ?: return null
        return database.albumQueries.albumById(map.albumId, ::mapAlbum).executeAsOneOrNull()
    }
}
