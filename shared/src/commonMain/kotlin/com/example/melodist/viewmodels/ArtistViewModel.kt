package com.example.melodist.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.melodist.data.remote.ApiService
import com.example.melodist.data.repository.ArtistRepository
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.pages.ArtistPage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.logging.Logger

sealed class ArtistState {
    object Loading : ArtistState()
    data class Success(val artistPage: ArtistPage) : ArtistState()
    data class Error(val message: String) : ArtistState()
}
@OptIn(ExperimentalCoroutinesApi::class)
class ArtistViewModel(
    private val apiService: ApiService,
    private val repository: ArtistRepository
) : ViewModel() {

    private val log = Logger.getLogger("ArtistViewModel")

    private val _uiState = MutableStateFlow<ArtistState>(ArtistState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _currentBrowseId = MutableStateFlow<String?>(null)
    val isSaved: StateFlow<Boolean> = _currentBrowseId
        .flatMapLatest{ id ->
            if (id == null) flowOf(false)
            else repository.isArtistSaved(id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)


    fun loadArtist(browseId: String) {
        _currentBrowseId.value = browseId

        viewModelScope.launch {
            _uiState.value = ArtistState.Loading
            YouTube.artist(browseId)
                .onSuccess {
                    _uiState.value = ArtistState.Success(it)
                }
                .onFailure {
                    _uiState.value = ArtistState.Error(it.message ?: "Error desconocido")
                }
        }
    }

    fun toggleSave() {
        val state = _uiState.value
        if (state !is ArtistState.Success) return

        viewModelScope.launch {
            if (isSaved.value) {
                repository.removeArtist(state.artistPage.artist.id)
            } else {
                repository.saveArtist(
                    artist = state.artistPage.artist,
                    subscriberCount = state.artistPage.subscriberCountText
                )
            }
        }
    }
}
