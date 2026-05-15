package com.example.melodist.db.entities

/**
 * Format information for a song.
 */
data class FormatEntity(
    val id: String,
    val itag: Int,
    val mimeType: String,
    val codecs: String,
    val bitrate: Int,
    val sampleRate: Int?,
    val contentLength: Long,
    val loudnessDb: Double?,
    val perceptualLoudnessDb: Double? = null,
    val playbackUrl: String?
)

