package com.example.melodist.db.entities

import java.time.LocalDateTime

/**
 * Represents a play event for tracking listening history.
 */
data class EventEntity(
    val id: Long = 0,
    val songId: String,
    val timestamp: LocalDateTime,
    val playTime: Long
)

/**
 * An event with its associated song data.
 */
data class EventWithSong(
    val event: EventEntity,
    val song: SongWithRelations
)

