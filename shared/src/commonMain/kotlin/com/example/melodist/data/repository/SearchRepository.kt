package com.example.melodist.data.repository

import com.example.melodist.db.DatabaseDao
import com.example.melodist.db.entities.SearchHistoryEntry
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing search history using SQLDelight.
 */
class SearchRepository(
    private val dao: DatabaseDao
) {

    /**
     * Get search history as a reactive Flow.
     */
    fun getSearchHistory(): Flow<List<SearchHistoryEntry>> {
        return dao.searchHistory()
    }

    /**
     * Add a search query to history.
     * Uses INSERT OR REPLACE so duplicate queries just update their position.
     */
    suspend fun addSearchQuery(query: String) {
        if (query.isBlank()) return
        dao.insertSearchHistory(query.trim())
    }

    /**
     * Remove a single query from history.
     */
    suspend fun deleteSearchQuery(query: String) {
        dao.deleteSearchHistory(query)
    }

    /**
     * Clear all search history.
     */
    suspend fun clearSearchHistory() {
        dao.clearSearchHistory()
    }
}