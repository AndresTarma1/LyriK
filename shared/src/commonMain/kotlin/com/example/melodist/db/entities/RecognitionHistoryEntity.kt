package com.example.melodist.db.entities

import java.time.LocalDateTime

/**
 * Recognition history entry from Shazam-like services.
 */
data class RecognitionHistoryEntity(
    val id: Long = 0,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val coverArtUrl: String? = null,
    val coverArtHqUrl: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val label: String? = null,
    val shazamUrl: String? = null,
    val appleMusicUrl: String? = null,
    val spotifyUrl: String? = null,
    val isrc: String? = null,
    val youtubeVideoId: String? = null,
    val recognizedAt: LocalDateTime = LocalDateTime.now(),
    val liked: Boolean = false
)

