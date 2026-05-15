package com.example.melodist.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.pages.BrowseResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class YouTubeBrowseState {
    object Loading : YouTubeBrowseState()
    data class Success(val result: BrowseResult) : YouTubeBrowseState()
    data class Error(val message: String) : YouTubeBrowseState()
}

class YouTubeBrowseViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<YouTubeBrowseState>(YouTubeBrowseState.Loading)
    val uiState: StateFlow<YouTubeBrowseState> = _uiState.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    fun load(browseId: String, params: String?) {
        if (_uiState.value is YouTubeBrowseState.Loading) {
            // already loading
        } else {
            _uiState.value = YouTubeBrowseState.Loading
        }

        viewModelScope.launch {
            YouTube.browse(browseId, params)
                .onSuccess { result ->
                    _title.value = result.title ?: ""
                    _uiState.value = YouTubeBrowseState.Success(result)
                }
                .onFailure { error ->
                    _uiState.value = YouTubeBrowseState.Error(
                        error.message ?: "Error al cargar contenido"
                    )
                }
        }
    }
}
