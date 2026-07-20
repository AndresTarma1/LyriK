package com.example.musicApp.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicApp.data.repository.SearchRepository
import com.example.musicApp.db.entities.SearchHistoryEntry
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.pages.ChartsPage
import com.metrolist.innertube.pages.ExplorePage
import com.metrolist.innertube.pages.MoodAndGenres
import com.metrolist.innertube.pages.SearchSummaryPage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.logging.Logger

sealed class SearchState {
    data class Success(val items: List<YTItem>, val continuation: String? = null, val isLoadingMore: Boolean = false) : SearchState()
    data class SummarySuccess(val summary: SearchSummaryPage) : SearchState()
    data class Error(val message: String) : SearchState()
    object Loading : SearchState()
    object Idle : SearchState()
}

class SearchViewModel(
    private val searchRepository: SearchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchState>(SearchState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _charts = MutableStateFlow<ChartsPage?>(null)
    val charts = _charts.asStateFlow()

    private val _explore = MutableStateFlow<ExplorePage?>(null)
    val explore: StateFlow<ExplorePage?> = _explore.asStateFlow()

    private val _moodAndGenres = MutableStateFlow<List<MoodAndGenres>>(emptyList())
    val moodAndGenres: StateFlow<List<MoodAndGenres>> = _moodAndGenres.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions = _suggestions.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    /**
     * Historial de búsqueda como un StateFlow reactivo.
     */
    val searchHistory: StateFlow<List<SearchHistoryEntry>> = searchRepository.getSearchHistory()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            YouTube.getChartsPage()
                .onSuccess { _charts.value = it }
        }
        viewModelScope.launch {
            YouTube.explore()
                .onSuccess { _explore.value = it }
        }
        viewModelScope.launch {
            YouTube.moodAndGenres()
                .onSuccess { _moodAndGenres.value = it }
        }
    }

    private var suggestionsJob: Job? = null
    private fun fetchSuggestions(query: String) {
        suggestionsJob?.cancel()

        suggestionsJob = viewModelScope.launch {
            delay(300)
            YouTube.searchSuggestions(query)
                .onSuccess {
                    _suggestions.value = it.queries
                }
                .onFailure {
                    _suggestions.value = emptyList()
                }
        }
    }

    private val _filter = MutableStateFlow<YouTube.SearchFilter?>(null)
    val filter = _filter.asStateFlow()

    fun onQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
        if (newQuery.isNotEmpty()) {
            fetchSuggestions(newQuery)
        } else {
            _suggestions.value = emptyList()
        }
    }

    fun onFilterChange(newFilter: YouTube.SearchFilter?) {
        _filter.value = newFilter
        search()
    }

    private var searchJob: Job? = null
    fun search() {
        searchJob?.cancel()

        if (_searchQuery.value.isBlank()) {
            _uiState.value = SearchState.Idle
            return
        }

        // Guardar la consulta en el historial de búsqueda
        viewModelScope.launch {
            searchRepository.addSearchQuery(_searchQuery.value)
        }

        searchJob = viewModelScope.launch {
            _uiState.value = SearchState.Loading
            if (_filter.value === null) {
                YouTube.searchSummary(_searchQuery.value)
                    .onSuccess { page ->
                        _uiState.value = SearchState.SummarySuccess(page)
                    }
                    .onFailure {
                        _uiState.value = SearchState.Error(it.message ?: "Unknown error")
                    }
            } else {
                YouTube.search(
                    query = _searchQuery.value,
                    filter = _filter.value!!
                ).onSuccess { listItems ->
                    _uiState.value = SearchState.Success(listItems.items, listItems.continuation)
                }.onFailure {
                    _uiState.value = SearchState.Error(it.message ?: "Unknown error")
                }
            }
        }
    }

    fun searchContinuation() {
        val current = _uiState.value

        if (current !is SearchState.Success) return
        if (current.isLoadingMore) return
        if (current.continuation == null) return

        viewModelScope.launch {
            _uiState.value = current.copy(isLoadingMore = true)

            YouTube.searchContinuation(current.continuation)
                .onSuccess { response ->
                    _uiState.value = current.copy(
                        items = (current.items + response.items).distinctBy { it.id },
                        continuation = response.continuation,
                        isLoadingMore = false
                    )
                }
                .onFailure {
                    Logger.getGlobal().warning("Failed to load continuation: ${it.message}")
                }
        }
    }

    /**
     * Elimina una sola entrada del historial de búsqueda.
     */
    fun deleteHistoryEntry(query: String) {
        viewModelScope.launch {
            searchRepository.deleteSearchQuery(query)
        }
    }

    /**
     * Borra todo el historial de búsqueda.
     */
    fun clearHistory() {
        viewModelScope.launch {
            searchRepository.clearSearchHistory()
        }
    }
}
