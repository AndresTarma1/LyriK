package com.example.melodist.db.entities

/**
 * Lyrics for a song.
 */
data class LyricsEntity(
    val id: String,
    val lyrics: String,
    val provider: String = "Unknown"
) {
    companion object {
        const val LYRICS_NOT_FOUND = "LYRICS_NOT_FOUND"
    }
}

