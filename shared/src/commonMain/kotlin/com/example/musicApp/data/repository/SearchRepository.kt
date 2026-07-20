package com.example.musicApp.data.repository

import com.example.musicApp.db.DatabaseDao
import com.example.musicApp.db.entities.SearchHistoryEntry
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio para gestionar el historial de búsqueda usando SQLDelight.
 */
class SearchRepository(
    private val dao: DatabaseDao
) {

    /**
     * Obtiene el historial de búsqueda como un Flow reactivo.
     */
    fun getSearchHistory(): Flow<List<SearchHistoryEntry>> {
        return dao.searchHistory()
    }

    /**
     * Agrega una consulta de búsqueda al historial.
     * Usa INSERT OR REPLACE para que las consultas duplicadas solo actualicen su posición.
     */
    suspend fun addSearchQuery(query: String) {
        if (query.isBlank()) return
        dao.insertSearchHistory(query.trim())
    }

    /**
     * Elimina una sola consulta del historial.
     */
    suspend fun deleteSearchQuery(query: String) {
        dao.deleteSearchHistory(query)
    }

    /**
     * Borra todo el historial de búsqueda.
     */
    suspend fun clearSearchHistory() {
        dao.clearSearchHistory()
    }
}