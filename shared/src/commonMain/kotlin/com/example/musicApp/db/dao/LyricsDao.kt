package com.example.musicApp.db.dao

import com.example.musicApp.db.MelodistDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LyricsDao(private val database: MelodistDatabase) {

    suspend fun insertLyrics(id: String, lyrics: String, provider: String = "Unknown") = withContext(Dispatchers.IO) {
        database.lyricsQueries.insertLyrics(id, lyrics, provider)
    }
}
