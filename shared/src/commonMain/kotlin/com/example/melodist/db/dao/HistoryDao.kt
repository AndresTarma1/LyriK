package com.example.melodist.db.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.example.melodist.db.MelodistDatabase
import com.example.melodist.db.entities.SearchHistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class HistoryDao(private val database: MelodistDatabase) {

    fun searchHistory(): Flow<List<SearchHistoryEntry>> =
        database.searchHistoryQueries.selectAll { id, query, timestamp ->
            SearchHistoryEntry(id = id, query = query)
        }.asFlow().mapToList(Dispatchers.IO)

    suspend fun insertSearchHistory(query: String) = withContext(Dispatchers.IO) {
        database.searchHistoryQueries.insertQuery(query, System.currentTimeMillis())
    }

    suspend fun deleteSearchHistory(query: String) = withContext(Dispatchers.IO) {
        database.searchHistoryQueries.deleteQuery(query)
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        database.searchHistoryQueries.deleteAll()
    }

    suspend fun insertEvent(songId: String, timestamp: java.time.LocalDateTime, playTime: Long) = withContext(Dispatchers.IO) {
        database.eventQueries.insertEvent(
            songId = songId,
            timestamp = timestamp.atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
            playTime = playTime
        )
    }
}
