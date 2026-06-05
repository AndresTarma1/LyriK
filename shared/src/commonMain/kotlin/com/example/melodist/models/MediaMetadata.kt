package com.example.melodist.models

import com.example.melodist.db.entities.AlbumEntity
import com.example.melodist.db.entities.ArtistEntity
import com.example.melodist.db.entities.SongEntity
import com.example.melodist.db.entities.SongWithRelations
import com.example.melodist.utils.resize
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.io.Serializable as JvmSerializable
import java.time.LocalDateTime

/**
 * Standard metadata model for media playback and UI display.
 * Bridges the gap between API models (SongItem) and Database models (SongEntity).
 */
@Serializable
data class MediaMetadata(
    val id: String,
    val title: String,
    val artists: List<Artist>,
    val duration: Int,
    val thumbnailUrl: String? = null,
    val album: Album? = null,
    val setVideoId: String? = null,
    val musicVideoType: String? = null,
    val explicit: Boolean = false,
    val liked: Boolean = false,
    @Transient
    val likedDate: LocalDateTime? = null,
    @Transient
    val inLibrary: LocalDateTime? = null,
    val libraryAddToken: String? = null,
    val libraryRemoveToken: String? = null,
    @Transient
    val isDownloaded: Boolean = false,
) : JvmSerializable {
    val isVideoSong: Boolean
        get() = musicVideoType != null && musicVideoType != MUSIC_VIDEO_TYPE_ATV

    @Serializable
    data class Artist(
        val id: String?,
        val name: String,
    ) : JvmSerializable

    @Serializable
    data class Album(
        val id: String,
        val title: String,
    ) : JvmSerializable

    /**
     * Converts this metadata to a database entity for storage.
     */
    fun toSongEntity() =
        SongEntity(
            id = id,
            title = title,
            duration = duration,
            thumbnailUrl = thumbnailUrl,
            albumId = album?.id,
            albumName = album?.title,
            explicit = explicit,
            liked = liked,
            likedDate = likedDate,
            inLibrary = inLibrary,
            libraryAddToken = libraryAddToken,
            libraryRemoveToken = libraryRemoveToken,
            isVideo = isVideoSong,
            isDownloaded = isDownloaded
        )
}

/**
 * Extension to convert SQLDelight DB model to MediaMetadata.
 */
fun SongWithRelations.toMediaMetadata() =
    MediaMetadata(
        id = song.id,
        title = song.title,
        artists = artists.map {
            MediaMetadata.Artist(
                id = it.id,
                name = it.name,
            )
        },
        duration = song.duration,
        thumbnailUrl = song.thumbnailUrl,
        album = album?.let {
            MediaMetadata.Album(
                id = it.id,
                title = it.title,
            )
        } ?: song.albumId?.let { albumId ->
            MediaMetadata.Album(
                id = albumId,
                title = song.albumName.orEmpty(),
            )
        },
        explicit = song.explicit,
        liked = song.liked,
        likedDate = song.likedDate,
        inLibrary = song.inLibrary,
        isDownloaded = song.isDownloaded,
        musicVideoType = if (song.isVideo) "MUSIC_VIDEO_TYPE_OMV" else null,
    )

