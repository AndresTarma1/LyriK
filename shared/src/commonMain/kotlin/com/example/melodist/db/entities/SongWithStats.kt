package com.example.melodist.db.entities

/**
 * Simplified song stats model.
 */
data class SongWithStats(
    val id: String,
    val title: String,
    val artistName: String?,
    val thumbnailUrl: String,
    val songCountListened: Int,
    val timeListened: Long?,
    val isVideo: Boolean = false
)

