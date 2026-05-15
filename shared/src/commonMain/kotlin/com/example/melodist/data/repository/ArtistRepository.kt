package com.example.melodist.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.example.melodist.db.MelodistDatabase
import com.example.melodist.db.SavedArtist
import com.metrolist.innertube.models.ArtistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ArtistRepository(private val database: MelodistDatabase) {
    fun getSavedArtists(): Flow<List<SavedArtist>> {
        return database.savedArtistQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    fun isArtistSaved(id: String): Flow<Boolean> {
        return database.savedArtistQueries.exists(id)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it ?: false }
    }

    suspend fun saveArtist(artist: ArtistItem, subscriberCount: String? = null) = withContext(Dispatchers.IO) {
        database.savedArtistQueries.insert(
            id = artist.id,
            title = artist.title,
            thumbnail = artist.thumbnail,
            subscriberCount = subscriberCount,
            savedAt = System.currentTimeMillis()
        )
    }

    suspend fun removeArtist(id: String) = withContext(Dispatchers.IO) {
        database.savedArtistQueries.delete(id)
    }
}
