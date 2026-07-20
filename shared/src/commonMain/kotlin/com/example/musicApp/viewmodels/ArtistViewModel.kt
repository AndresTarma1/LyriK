package com.example.musicApp.viewmodels

import androidx.lifecycle.viewModelScope
import com.example.musicApp.data.remote.ApiService
import com.example.musicApp.data.repository.ArtistRepository
import com.example.musicApp.data.repository.UserPreferencesRepository
import com.example.musicApp.utils.PendingAction
import com.example.musicApp.utils.PendingSyncQueue
import com.example.musicApp.utils.retryWithBackoff
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.pages.ArtistPage
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val repository: ArtistRepository,
    private val userPreferences: UserPreferencesRepository,
    private val pendingSyncQueue: PendingSyncQueue,
) : MelodistViewModel() {

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
            val artist = state.artistPage.artist
            val wasSaved = isSaved.value
            if (wasSaved) {
                repository.removeArtist(artist.id)
            } else {
                repository.saveArtist(
                    artist = artist,
                    subscriberCount = state.artistPage.subscriberCountText
                )
            }

            // Push the follow/unfollow to the real YouTube account — gated behind "Sincronizar
            // con YouTube Music" like the other pushes, since it writes to the user's account.
            // Some YTM-only artists don't expose a real channel id; skip the push for those.
            val channelId = artist.channelId
            if (channelId != null && userPreferences.ytmSyncEnabled.first()) {
                retryWithBackoff { YouTube.subscribeChannel(channelId, subscribe = !wasSaved) }
                    .onFailure {
                        Napier.w("Failed to push subscribe state for $channelId: ${it.message}")
                        pendingSyncQueue.enqueue(PendingAction.SubscribeArtist(channelId, subscribed = !wasSaved))
                    }
            }
        }
    }
}
