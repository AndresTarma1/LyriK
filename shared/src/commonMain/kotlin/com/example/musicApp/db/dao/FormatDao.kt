package com.example.musicApp.db.dao

import com.example.musicApp.db.MelodistDatabase
import com.example.musicApp.db.entities.FormatEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FormatDao(private val database: MelodistDatabase) {

    suspend fun insertFormat(format: FormatEntity) = withContext(Dispatchers.IO) {
        database.formatQueries.insertFormat(
            id = format.id, itag = format.itag.toLong(), mimeType = format.mimeType,
            codecs = format.codecs, bitrate = format.bitrate.toLong(),
            sampleRate = format.sampleRate?.toLong(), contentLength = format.contentLength,
            loudnessDb = format.loudnessDb, perceptualLoudnessDb = format.perceptualLoudnessDb,
            playbackUrl = format.playbackUrl
        )
    }
}
