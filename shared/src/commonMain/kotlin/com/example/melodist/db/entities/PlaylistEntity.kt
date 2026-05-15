package com.example.melodist.db.entities

import java.time.LocalDateTime

/**
 * Represents a playlist entity stored in the local database.
 */
data class PlaylistEntity(
    val id: String,
    val name: String,
    val browseId: String? = null,
    val createdAt: LocalDateTime? = LocalDateTime.now(),
    val lastUpdateTime: LocalDateTime? = LocalDateTime.now(),
    val isEditable: Boolean = true,
    val bookmarkedAt: LocalDateTime? = null,
    val remoteSongCount: Int? = null,
    val playEndpointParams: String? = null,
    val thumbnailUrl: String? = null,
    val shuffleEndpointParams: String? = null,
    val radioEndpointParams: String? = null,
    val isLocal: Boolean = false,
    val isAutoSync: Boolean = false
) {
    companion object {
        const val LIKED_PLAYLIST_ID = "LP_LIKED"
        const val DOWNLOADED_PLAYLIST_ID = "LP_DOWNLOADED"
    }

    val shareLink: String?
        get() = if (browseId != null) "https://music.youtube.com/playlist?list=$browseId" else null

    fun localToggleLike() = copy(
        bookmarkedAt = if (bookmarkedAt != null) null else LocalDateTime.now()
    )
}

