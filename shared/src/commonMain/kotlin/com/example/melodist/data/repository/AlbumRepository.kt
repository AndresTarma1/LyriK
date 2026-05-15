package com.example.melodist.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.example.melodist.db.MelodistDatabase
import com.example.melodist.db.SavedAlbum
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

class AlbumRepository(private val database: MelodistDatabase) {
    fun getSavedAlbums(): Flow<List<SavedAlbum>> {
        return database.savedAlbumQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    fun isAlbumSaved(browseId: String): Flow<Boolean> {
        return database.savedAlbumQueries.exists(browseId)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it ?: false }
    }

    suspend fun isAlbumSavedOnce(browseId: String): Boolean = withContext(Dispatchers.IO) {
        database.savedAlbumQueries.exists(browseId).executeAsOneOrNull() ?: false
    }

    suspend fun saveAlbum(album: AlbumItem) = withContext(Dispatchers.IO) {
        database.savedAlbumQueries.insert(
            browseId = album.browseId,
            playlistId = album.playlistId,
            title = album.title,
            artists = jsonSerializer.encodeToString(album.artists?.map { SerializableArtist(it.name, it.id) } ?: emptyList()),
            year = album.year?.toLong(),
            thumbnail = album.thumbnail,
            explicit = if (album.explicit) 1L else 0L,
            savedAt = System.currentTimeMillis()
        )
    }

    suspend fun saveAlbumWithSongs(album: AlbumItem, songs: List<SongItem>) = withContext(Dispatchers.IO) {
        database.transaction {
            database.savedAlbumQueries.insert(
                browseId = album.browseId,
                playlistId = album.playlistId,
                title = album.title,
                artists = jsonSerializer.encodeToString(album.artists?.map { SerializableArtist(it.name, it.id) } ?: emptyList()),
                year = album.year?.toLong(),
                thumbnail = album.thumbnail,
                explicit = if (album.explicit) 1L else 0L,
                savedAt = System.currentTimeMillis()
            )

            database.albumQueries.insertAlbum(
                id = album.browseId,
                playlistId = album.playlistId,
                title = album.title,
                year = album.year?.toLong(),
                thumbnailUrl = album.thumbnail,
                themeColor = null,
                songCount = songs.size.toLong(),
                duration = songs.sumOf { it.duration ?: 0 }.toLong(),
                explicit = if (album.explicit) 1L else 0L,
                lastUpdateTime = System.currentTimeMillis(),
                bookmarkedAt = System.currentTimeMillis(),
                likedDate = null,
                inLibrary = System.currentTimeMillis(),
                isLocal = 0L,
                isUploaded = 0L
            )

            database.songAlbumMapQueries.deleteSongAlbumMapsByAlbum(album.browseId)

            songs.forEachIndexed { index, song ->
                database.songQueries.insertSongIfNotExists(
                    id = song.id,
                    title = song.title,
                    duration = song.duration?.toLong() ?: -1L,
                    thumbnailUrl = song.thumbnail,
                    albumId = album.browseId,
                    albumName = album.title,
                    explicit = if (song.explicit) 1L else 0L,
                    year = album.year?.toLong(),
                    date = null,
                    dateModified = null,
                    liked = 0L,
                    likedDate = null,
                    totalPlayTime = 0L,
                    inLibrary = null,
                    dateDownload = null,
                    isLocal = 0L,
                    libraryAddToken = null,
                    libraryRemoveToken = null,
                    lyricsOffset = 0L,
                    romanizeLyrics = 1L,
                    isAgeRestricted = 0L,
                    isDownloaded = 0L,
                    isUploaded = 0L,
                    isVideo = 0L
                )

                database.songAlbumMapQueries.insertSongAlbumMap(song.id, album.browseId, index.toLong())

                song.artists.forEachIndexed { artistIdx, artist ->
                    val artistId = artist.id ?: return@forEachIndexed
                    database.artistQueries.insertArtistIfNotExists(
                        id = artistId,
                        name = artist.name,
                        thumbnailUrl = null,
                        channelId = null,
                        lastUpdateTime = 0L,
                        bookmarkedAt = null,
                        isLocal = 0L
                    )
                    database.songArtistMapQueries.insertSongArtistMap(song.id, artistId, artistIdx.toLong())
                }
            }
        }
    }

    suspend fun getCachedAlbumSongs(browseId: String): List<SongItem>? = withContext(Dispatchers.IO) {
        val isSaved = database.savedAlbumQueries.exists(browseId).executeAsOne()
        if (!isSaved) return@withContext null

        val count = database.songAlbumMapQueries.countByAlbum(browseId).executeAsOne()
        if (count == 0L) return@withContext null

        val songs = database.songAlbumMapQueries.songsByAlbum(browseId).executeAsList()

        val artistRows = database.songArtistMapQueries.artistsForAlbumSongs(browseId).executeAsList()
        val artistsBySong: Map<String, List<Artist>> = artistRows
            .groupBy { it.songId }
            .mapValues { (_, rows) -> rows.map { row -> Artist(name = row.name, id = row.id) } }

        songs.map { song -> dbSongToSongItem(song, artistsBySong[song.id] ?: emptyList()) }
    }

    fun getCachedAlbumItem(browseId: String): AlbumItem? {
        val saved = database.savedAlbumQueries.selectById(browseId).executeAsOneOrNull()
            ?: return null
        return savedAlbumToAlbumItem(saved)
    }

    suspend fun removeAlbum(browseId: String) = withContext(Dispatchers.IO) {
        database.savedAlbumQueries.delete(browseId)
        database.songAlbumMapQueries.deleteSongAlbumMapsByAlbum(browseId)
    }
}
