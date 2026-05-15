package com.example.melodist.db

import app.cash.sqldelight.Query
import com.example.melodist.db.dao.AlbumDao
import com.example.melodist.db.dao.ArtistDao
import com.example.melodist.db.dao.FormatDao
import com.example.melodist.db.dao.HistoryDao
import com.example.melodist.db.dao.LyricsDao
import com.example.melodist.db.dao.MappingDao
import com.example.melodist.db.dao.PlaylistDao
import com.example.melodist.db.dao.SongDao
import com.example.melodist.db.entities.AlbumEntity
import com.example.melodist.db.entities.AlbumWithSongs
import com.example.melodist.db.entities.ArtistEntity
import com.example.melodist.db.entities.ArtistWithStats
import com.example.melodist.db.entities.FormatEntity
import com.example.melodist.db.entities.PlaylistEntity
import com.example.melodist.db.entities.SearchHistoryEntry
import com.example.melodist.db.entities.SongEntity
import com.example.melodist.db.entities.SongWithRelations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

class DatabaseDao(private val database: MelodistDatabase) {

    private val songs = SongDao(database)
    private val artists = ArtistDao(database)
    private val albums = AlbumDao(database)
    private val playlists = PlaylistDao(database)
    private val mappings = MappingDao(database)
    private val history = HistoryDao(database)
    private val lyrics = LyricsDao(database)
    private val formats = FormatDao(database)

    fun allSongs(): Flow<List<SongEntity>> = songs.allSongs()
    fun songsInLibrary(): Flow<List<SongEntity>> = songs.songsInLibrary()
    fun songById(id: String): Flow<SongEntity?> = songs.songById(id)
    fun likedSongs(): Flow<List<SongEntity>> = songs.likedSongs()
    fun songWithRelations(songId: String): Flow<SongWithRelations?> =
        flow {
            val song = songs.songById(songId).firstOrNull()
            emit(
                song?.let {
                    SongWithRelations(
                        song = it,
                        artists = mappings.artistsForSong(songId),
                        album = mappings.albumForSong(songId)
                    )
                }
            )
        }

    suspend fun insertSong(song: SongEntity) = songs.insertSong(song)
    suspend fun updateSong(song: SongEntity) = songs.updateSong(song)
    suspend fun updateSongMetadata(song: SongEntity) = songs.updateSongMetadata(song)
    suspend fun deleteSong(id: String) = songs.deleteSong(id)

    fun allArtists(): Flow<List<ArtistEntity>> = artists.allArtists()
    fun artistsByPlayTime(): Flow<List<ArtistWithStats>> = artists.artistsByPlayTime()
    fun artistsByCreateDate(): Flow<List<ArtistWithStats>> = artists.artistsByCreateDate()
    fun artistsByName(): Flow<List<ArtistWithStats>> = artists.artistsByName()
    fun artistById(id: String): Flow<ArtistEntity?> = artists.artistById(id)
    fun bookmarkedArtists(): Flow<List<ArtistEntity>> = artists.bookmarkedArtists()
    suspend fun insertArtist(artist: ArtistEntity) = artists.insertArtist(artist)
    suspend fun updateArtistBookmark(id: String, bookmarkedAt: LocalDateTime?) = artists.updateArtistBookmark(id, bookmarkedAt)
    suspend fun deleteArtist(id: String) = artists.deleteArtist(id)

    fun allAlbums(): Flow<List<AlbumEntity>> = albums.allAlbums()
    fun albumById(id: String): Flow<AlbumEntity?> = albums.albumById(id)
    fun bookmarkedAlbums(): Flow<List<AlbumEntity>> = albums.bookmarkedAlbums()
    suspend fun insertAlbum(album: AlbumEntity) = albums.insertAlbum(album)
    suspend fun updateAlbumBookmark(id: String, bookmarkedAt: LocalDateTime?) = albums.updateAlbumBookmark(id, bookmarkedAt)
    suspend fun deleteAlbum(id: String) = albums.deleteAlbum(id)
    fun albumWithArtists(albumId: String) = albums.albumWithArtists(albumId)
    fun albumWithSongs(albumId: String): Flow<AlbumWithSongs?> = albums.albumWithSongs(albumId)
    fun countDownloadedByAlbum(albumId: String): Long = albums.countDownloadedByAlbum(albumId)

    // Playlist DAO
    fun allPlaylists(): Flow<List<PlaylistEntity>> = playlists.allPlaylists()
    fun playlistsByNameAsc(): Flow<List<PlaylistEntity>> = playlists.playlistsByNameAsc()
    fun playlistById(id: String): Flow<PlaylistEntity?> = playlists.playlistById(id)
    suspend fun insertPlaylist(playlist: PlaylistEntity) = playlists.insertPlaylist(playlist)
    suspend fun deletePlaylist(id: String) = playlists.deletePlaylist(id)
    fun songIdsInPlaylist(playlistId: String): List<String> = mappings.songIdsInPlaylist(playlistId)
    fun countByPlaylist(playlistId: String): Long = playlists.countByPlaylist(playlistId)
    fun countDownloadedByPlaylist(playlistId: String): Long = playlists.countDownloadedByPlaylist(playlistId)

    suspend fun insertSongArtistMap(songId: String, artistId: String, position: Int) = mappings.insertSongArtistMap(songId, artistId, position)
    suspend fun insertSongAlbumMap(songId: String, albumId: String, index: Int) = mappings.insertSongAlbumMap(songId, albumId, index)
    suspend fun insertAlbumArtistMap(albumId: String, artistId: String, order: Int) = mappings.insertAlbumArtistMap(albumId, artistId, order)
    suspend fun insertPlaylistSongMap(playlistId: String, songId: String, position: Int, setVideoId: String? = null) = mappings.insertPlaylistSongMap(playlistId, songId, position, setVideoId)

    fun artistsForSong(songId: String): List<com.example.melodist.db.entities.ArtistEntity> = mappings.artistsForSong(songId)
    fun albumForSong(songId: String): com.example.melodist.db.entities.AlbumEntity? = mappings.albumForSong(songId)

    suspend fun insertEvent(songId: String, timestamp: LocalDateTime, playTime: Long) = history.insertEvent(songId, timestamp, playTime)
    fun searchHistory(): Flow<List<SearchHistoryEntry>> = history.searchHistory()
    suspend fun insertSearchHistory(query: String) = history.insertSearchHistory(query)
    suspend fun deleteSearchHistory(query: String) = history.deleteSearchHistory(query)
    suspend fun clearSearchHistory() = history.clearSearchHistory()

    suspend fun insertLyrics(id: String, lyricsText: String, provider: String = "Unknown") = lyrics.insertLyrics(id, lyricsText, provider)
    fun downloadedSongs(): Flow<List<SongEntity>> = songs.downloadedSongs()
    fun downloadedSongsCount(): Flow<Long> = songs.downloadedSongsCount()
    suspend fun updateSongDownloadStatus(songId: String, isDownloaded: Boolean, dateDownload: Long?) = songs.updateSongDownloadStatus(songId, isDownloaded, dateDownload)

    fun quickPicks(now: Long = System.currentTimeMillis()): Flow<List<SongEntity>> = songs.quickPicks(now)
    fun searchSongs(query: String, limit: Long = 20): Flow<List<SongEntity>> = songs.searchSongs(query, limit)

    fun recentPlayedSongs(limit: Int = 10): Flow<List<SongWithRelations>> = flow {
        val events = database.eventQueries.recentEvents(limit.toLong()).executeAsList()
        val songIds = events.map { it.songId }.distinct()
        val result = songIds.mapNotNull { id ->
            val song = songs.songById(id).firstOrNull() ?: return@mapNotNull null
            SongWithRelations(
                song = song,
                artists = mappings.artistsForSong(id),
                album = mappings.albumForSong(id)
            )
        }
        emit(result)
    }

    suspend fun insertFormat(format: FormatEntity) = formats.insertFormat(format)

    fun <T> transaction(block: () -> T): T {
        var result: T? = null
        database.transaction { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
