package com.example.melodist.data.repository

import com.example.melodist.db.SavedAlbum
import com.example.melodist.db.SavedArtist
import com.example.melodist.db.SavedPlaylist
import com.example.melodist.db.SavedSong
import com.example.melodist.db.Song
import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import kotlinx.serialization.json.Json

val jsonSerializer = Json { ignoreUnknownKeys = true }

@kotlinx.serialization.Serializable
data class SerializableArtist(
    val name: String,
    val id: String?
)

fun savedAlbumToAlbumItem(saved: SavedAlbum): AlbumItem {
    val artists = try {
        jsonSerializer.decodeFromString<List<SerializableArtist>>(saved.artists).map {
            Artist(name = it.name, id = it.id)
        }
    } catch (_: Exception) {
        emptyList()
    }
    return AlbumItem(
        browseId = saved.browseId,
        playlistId = saved.playlistId,
        title = saved.title,
        artists = artists,
        year = saved.year?.toInt(),
        thumbnail = saved.thumbnail,
        explicit = saved.explicit != 0L
    )
}

fun savedArtistToArtistItem(saved: SavedArtist): ArtistItem {
    return ArtistItem(
        id = saved.id,
        title = saved.title,
        thumbnail = saved.thumbnail,
        shuffleEndpoint = null,
        radioEndpoint = null
    )
}

fun savedSongToSongItem(saved: SavedSong): SongItem {
    val artists = try {
        jsonSerializer.decodeFromString<List<SerializableArtist>>(saved.artists).map {
            Artist(name = it.name, id = it.id)
        }
    } catch (_: Exception) {
        emptyList()
    }
    return SongItem(
        id = saved.id,
        title = saved.title,
        artists = artists,
        album = if (saved.albumName != null && saved.albumId != null) {
            Album(name = saved.albumName, id = saved.albumId)
        } else null,
        duration = saved.duration?.toInt(),
        thumbnail = saved.thumbnail,
        explicit = saved.explicit != 0L
    )
}

fun savedPlaylistToPlaylistItem(saved: SavedPlaylist): PlaylistItem {
    return PlaylistItem(
        id = saved.id,
        title = saved.title,
        author = if (saved.authorName != null) Artist(name = saved.authorName, id = saved.authorId) else null,
        songCountText = saved.songCountText,
        thumbnail = saved.thumbnail,
        playEndpoint = null,
        shuffleEndpoint = null,
        radioEndpoint = null
    )
}

fun dbSongToSongItem(song: Song, artists: List<Artist> = emptyList()): SongItem {
    return SongItem(
        id = song.id,
        title = song.title,
        artists = artists,
        album = if (song.albumName != null && song.albumId != null)
            Album(name = song.albumName, id = song.albumId)
        else null,
        duration = song.duration.takeIf { it >= 0 }?.toInt(),
        thumbnail = song.thumbnailUrl?: "",
        explicit = song.explicit != 0L
    )
}

