package com.example.melodist.db.entities

import java.time.LocalDateTime

/**
 * Represents an album entity stored in the local database.
 */
data class AlbumEntity(
    val id: String,
    val playlistId: String? = null,
    val title: String,
    val year: Int? = null,
    val thumbnailUrl: String? = null,
    val themeColor: Int? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val explicit: Boolean = false,
    val lastUpdateTime: LocalDateTime = LocalDateTime.now(),
    val bookmarkedAt: LocalDateTime? = null,
    val likedDate: LocalDateTime? = null,
    val inLibrary: LocalDateTime? = null,
    val isLocal: Boolean = false,
    val isUploaded: Boolean = false
) {
    fun localToggleLike() = copy(
        bookmarkedAt = if (bookmarkedAt != null) null else LocalDateTime.now()
    )

    fun toggleLibrary() = copy(
        inLibrary = if (inLibrary != null) null else LocalDateTime.now()
    )
}

