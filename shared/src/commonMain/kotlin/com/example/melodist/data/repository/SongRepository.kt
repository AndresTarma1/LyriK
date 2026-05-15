package com.example.melodist.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.example.melodist.db.MelodistDatabase
import com.example.melodist.db.SavedSong
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

class SongRepository(private val database: MelodistDatabase) {
    fun getSavedSongs(): Flow<List<SavedSong>> {
        return database.savedSongQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    fun isSongSaved(id: String): Flow<Boolean> {
        return database.savedSongQueries.exists(id)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it ?: false }
    }

    suspend fun saveSong(song: SongItem) = withContext(Dispatchers.IO) {
        database.savedSongQueries.insert(
            id = song.id,
            title = song.title,
            artists = jsonSerializer.encodeToString(song.artists.map { SerializableArtist(it.name, it.id) }),
            albumName = song.album?.name,
            albumId = song.album?.id,
            duration = song.duration?.toLong(),
            thumbnail = song.thumbnail,
            explicit = if (song.explicit) 1L else 0L,
            savedAt = System.currentTimeMillis()
        )
    }

    suspend fun removeSong(id: String) = withContext(Dispatchers.IO) {
        database.savedSongQueries.delete(id)
    }

    suspend fun getDownloadedSongs(): List<SongItem> = withContext(Dispatchers.IO) {
        val songs = database.songQueries.downloadedSongs().executeAsList()
        if (songs.isEmpty()) return@withContext emptyList()

        val artistRows = database.songArtistMapQueries.artistsForDownloadedSongs().executeAsList()
        val artistsBySong: Map<String, List<Artist>> = artistRows
            .groupBy { it.songId }
            .mapValues { (_, rows) -> rows.map { row -> Artist(name = row.name, id = row.id) } }

        songs.map { song -> dbSongToSongItem(song, artistsBySong[song.id] ?: emptyList()) }
    }
}
