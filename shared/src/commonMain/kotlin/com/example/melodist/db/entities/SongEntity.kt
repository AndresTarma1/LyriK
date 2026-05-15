package com.example.melodist.db.entities

import java.time.LocalDateTime

/**
 * Represents a song entity stored in the local database.
 * Desktop-compatible (no Room annotations).
 */
data class SongEntity(
    val id: String,
    val title: String,
    val duration: Int = -1,
    val thumbnailUrl: String? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    val explicit: Boolean = false,
    val year: Int? = null,
    val date: LocalDateTime? = null,
    val dateModified: LocalDateTime? = null,
    val liked: Boolean = false,
    val likedDate: LocalDateTime? = null,
    val totalPlayTime: Long = 0,
    val inLibrary: LocalDateTime? = null,
    val dateDownload: LocalDateTime? = null,
    val isLocal: Boolean = false,
    val libraryAddToken: String? = null,
    val libraryRemoveToken: String? = null,
    val lyricsOffset: Int = 0,
    val romanizeLyrics: Boolean = true,
    val isAgeRestricted: Boolean = false,
    val isDownloaded: Boolean = false,
    val isUploaded: Boolean = false,
    val isVideo: Boolean = false
) {
    fun localToggleLike() = copy(
        liked = !liked,
        likedDate = if (!liked) LocalDateTime.now() else null,
    )

    fun toggleLike() = copy(
        liked = !liked,
        likedDate = if (!liked) LocalDateTime.now() else null,
        inLibrary = if (!liked) inLibrary ?: LocalDateTime.now() else inLibrary
    )

    fun toggleLibrary() = copy(
        liked = if (inLibrary == null) liked else false,
        inLibrary = if (inLibrary == null) LocalDateTime.now() else null,
        likedDate = if (inLibrary == null) likedDate else null
    )
}

