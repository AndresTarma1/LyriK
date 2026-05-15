package com.example.melodist.db.entities

/**
 * Play count tracking per song per month.
 */
data class PlayCountEntry(
    val song: String,
    val year: Int = -1,
    val month: Int = -1,
    val count: Int = -1
)

