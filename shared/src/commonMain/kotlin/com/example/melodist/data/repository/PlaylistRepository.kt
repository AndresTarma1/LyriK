package com.example.melodist.data.repository

import com.example.melodist.db.MelodistDatabase
import com.example.melodist.db.Playlist
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.example.melodist.db.SavedPlaylist
import com.example.melodist.utils.retryWithBackoff
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PlaylistRepository(
    val database: MelodistDatabase
) {

    /**
     * The YouTube `browseId` for a local playlist, or null if it isn't linked to a real YT
     * playlist. Two independent paths lead here: [SyncUtils]'s SavedPlaylists pull writes a
     * `Playlist` row with an explicit `browseId` column; the normal "save this YouTube playlist"
     * flow ([savePlaylistWithSongs], via the playlist screen's bookmark button) only ever writes
     * to `SavedPlaylist` and never touches `Playlist` at all — for those, the playlist's own `id`
     * IS the browseId (that's how the rest of the app already treats non-local playlist ids).
     */
    suspend fun getBrowseId(playlistId: String): String? = withContext(Dispatchers.IO) {
        database.playlistQueries.playlistById(playlistId).executeAsOneOrNull()?.browseId
            ?: playlistId.takeIf { !it.startsWith("LOCAL_") && it != "LM" && it != "SE" }
    }

    suspend fun createPlaylist(playlist: Playlist) {
        database.transaction {
            database.playlistQueries.insertPlaylist(
                id = playlist.id,
                name = playlist.name,
                browseId = playlist.browseId,
                createdAt = playlist.createdAt,
                lastUpdateTime = System.currentTimeMillis(),
                isEditable = 1L,
                bookmarkedAt = null,
                remoteSongCount = 0L,
                playEndpointParams = null,
                thumbnailUrl = null,
                shuffleEndpointParams = null,
                radioEndpointParams = null,
                isLocal = 1L,
                isAutoSync = 0L
            )
        }
    }

    fun getSavedPlaylists(): Flow<List<SavedPlaylist>> {
        return database.savedPlaylistQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    fun isPlaylistSaved(id: String): Flow<Boolean> {
        return database.savedPlaylistQueries.exists(id)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it ?: false }
    }

    suspend fun isPlaylistSavedOnce(id: String): Boolean = withContext(Dispatchers.IO) {
        database.savedPlaylistQueries.exists(id).executeAsOneOrNull() ?: false
    }

    suspend fun savePlaylist(playlist: PlaylistItem) = withContext(Dispatchers.IO) {
        database.savedPlaylistQueries.insert(
            id = playlist.id,
            title = playlist.title,
            authorName = playlist.author?.name,
            authorId = playlist.author?.id,
            songCountText = playlist.songCountText,
            thumbnail = playlist.thumbnail,
            savedAt = System.currentTimeMillis()
        )
    }

    suspend fun removePlaylist(id: String) = withContext(Dispatchers.IO) {
        database.savedPlaylistQueries.delete(id)
        database.playlistSongMapQueries.deletePlaylistSongMapsByPlaylist(id)
    }

    suspend fun savePlaylistWithSongs(playlist: PlaylistItem, songs: List<SongItem>) = withContext(Dispatchers.IO) {
        database.transaction {
            database.savedPlaylistQueries.insert(
                id = playlist.id,
                title = playlist.title,
                authorName = playlist.author?.name,
                authorId = playlist.author?.id,
                songCountText = playlist.songCountText,
                thumbnail = playlist.thumbnail,
                savedAt = System.currentTimeMillis()
            )

            database.playlistSongMapQueries.deletePlaylistSongMapsByPlaylist(playlist.id)

            songs.forEachIndexed { index, song ->
                database.songQueries.insertSongIfNotExists(
                    id = song.id,
                    title = song.title,
                    duration = song.duration?.toLong() ?: -1L,
                    thumbnailUrl = song.thumbnail,
                    albumId = song.album?.id,
                    albumName = song.album?.name,
                    explicit = if (song.explicit) 1L else 0L,
                    year = null,
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
                    database.songArtistMapQueries.insertSongArtistMap(
                        songId = song.id,
                        artistId = artistId,
                        position = artistIdx.toLong()
                    )
                }

                database.playlistSongMapQueries.insertPlaylistSongMap(
                    playlistId = playlist.id,
                    songId = song.id,
                    position = index.toLong(),
                    setVideoId = song.setVideoId
                )
            }

            database.savedPlaylistQueries.updateSongCountText("${songs.size} canciones", playlist.id)
        }
    }

    suspend fun addSongToPlaylist(playlistId: String, song: SongItem) = withContext(Dispatchers.IO) {
        val alreadyExists = database.playlistSongMapQueries.selectByPlaylist(playlistId).executeAsList()
            .any { it.songId == song.id }
        if (alreadyExists) return@withContext

        val currentCount = database.playlistSongMapQueries.countByPlaylist(playlistId).executeAsOne()

        database.songQueries.insertSongIfNotExists(
            id = song.id,
            title = song.title,
            duration = song.duration?.toLong() ?: -1L,
            thumbnailUrl = song.thumbnail,
            albumId = song.album?.id,
            albumName = song.album?.name,
            explicit = if (song.explicit) 1L else 0L,
            year = null,
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
            database.songArtistMapQueries.insertSongArtistMap(
                songId = song.id,
                artistId = artistId,
                position = artistIdx.toLong()
            )
        }

        database.playlistSongMapQueries.insertPlaylistSongMap(
            playlistId = playlistId,
            songId = song.id,
            position = currentCount,
            setVideoId = null
        )

        database.savedPlaylistQueries.selectById(playlistId).executeAsOneOrNull()?.let {
            database.savedPlaylistQueries.updateSongCountTextAndThumbnail(
                songCountText = "${currentCount + 1} canciones",
                thumbnail = song.thumbnail,
                id = playlistId
            )
        }

        // Push to the linked YouTube playlist (best-effort: local state is already saved above,
        // so a network failure here doesn't lose the user's action, just leaves it un-synced).
        val browseId = getBrowseId(playlistId)
        if (browseId != null) {
            retryWithBackoff { YouTube.addToPlaylist(browseId, song.id) }
                .onSuccess { setVideoId ->
                    if (setVideoId != null) updateSetVideoId(playlistId, song.id, setVideoId)
                }
                .onFailure { Napier.w("Failed to push added song ${song.id} to playlist $browseId: ${it.message}") }
        }
    }

    /** Persist the YTM `setVideoId` for a song already in a local playlist (used for remote sync). */
    suspend fun updateSetVideoId(playlistId: String, songId: String, setVideoId: String) = withContext(Dispatchers.IO) {
        database.playlistSongMapQueries.updateSetVideoId(setVideoId, playlistId, songId)
    }

    /** The stored YTM `setVideoId` for a song in a local playlist, or null. */
    suspend fun getSetVideoId(playlistId: String, songId: String): String? = withContext(Dispatchers.IO) {
        database.playlistSongMapQueries.selectByPlaylist(playlistId).executeAsList()
            .firstOrNull { it.songId == songId }?.setVideoId
    }

    suspend fun removeSongFromPlaylist(playlistId: String, songId: String) = withContext(Dispatchers.IO) {
        val rows = database.playlistSongMapQueries.selectByPlaylist(playlistId).executeAsList()
        val row = rows.firstOrNull { it.songId == songId } ?: return@withContext
        database.playlistSongMapQueries.deletePlaylistSongMap(row.id)

        // Push the removal to the linked YouTube playlist (best-effort, same rationale as add).
        // Without a stored setVideoId (e.g. the song was added before this sync feature existed)
        // there's no way to identify the item to YouTube's API, so we just skip the remote call.
        val browseId = getBrowseId(playlistId)
        val setVideoId = row.setVideoId
        if (browseId != null && setVideoId != null) {
            retryWithBackoff { YouTube.removeFromPlaylist(browseId, songId, setVideoId) }
                .onFailure { Napier.w("Failed to push removed song $songId from playlist $browseId: ${it.message}") }
        }

        val remaining = database.playlistSongMapQueries.countByPlaylist(playlistId).executeAsOne()
        database.savedPlaylistQueries.selectById(playlistId).executeAsOneOrNull()?.let {
            val fallbackThumbnail = database.playlistSongMapQueries.songsByPlaylist(playlistId)
                .executeAsList()
                .lastOrNull()
                ?.thumbnailUrl
            database.savedPlaylistQueries.updateSongCountTextAndThumbnail(
                songCountText = "$remaining canciones",
                thumbnail = fallbackThumbnail,
                id = playlistId
            )
        }
    }

    suspend fun getCachedPlaylistSongs(playlistId: String): List<SongItem>? = withContext(Dispatchers.IO) {
        val isSaved = database.savedPlaylistQueries.exists(playlistId).executeAsOne()
        if (!isSaved) return@withContext null

        val count = database.playlistSongMapQueries.countByPlaylist(playlistId).executeAsOne()
        if (count == 0L) return@withContext null

        val songs = database.playlistSongMapQueries.songsByPlaylist(playlistId).executeAsList()

        val artistRows = database.songArtistMapQueries.artistsForPlaylistSongs(playlistId).executeAsList()
        val artistsBySong: Map<String, List<Artist>> = artistRows
            .groupBy { it.songId }
            .mapValues { (_, rows) -> rows.map { row -> Artist(name = row.name, id = row.id) } }

        return@withContext songs.map { song ->
            dbSongToSongItem(song, artistsBySong[song.id] ?: emptyList())
        }
    }

    fun getCachedPlaylistItem(playlistId: String): PlaylistItem? {
        val saved = database.savedPlaylistQueries.selectById(playlistId).executeAsOneOrNull()
            ?: return null
        return savedPlaylistToPlaylistItem(saved)
    }
}
