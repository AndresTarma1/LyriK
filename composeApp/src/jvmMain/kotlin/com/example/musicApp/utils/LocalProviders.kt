package com.example.musicApp.utils

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.SnackbarHostState
import com.example.musicApp.data.repository.UserPreferencesRepository
import com.example.musicApp.viewmodels.DownloadViewModel
import com.example.musicApp.viewmodels.LibraryPlaylistsViewModel
import com.example.musicApp.viewmodels.PlayerViewModel
import kotlinx.coroutines.CoroutineScope

val LocalPlayerViewModel = staticCompositionLocalOf<PlayerViewModel> { error("No PlayerViewModel provided") }

val LocalPlaylistsViewModel = staticCompositionLocalOf<LibraryPlaylistsViewModel> {
    error("No se ha proporcionado un LibraryPlaylistsViewModel")
}

val LocalDownloadViewModel = staticCompositionLocalOf<DownloadViewModel> {
    error("No se ha proporcionado un DownloadViewModel")
}

val LocalUserPreferences = staticCompositionLocalOf<UserPreferencesRepository> {
    error("No se ha proporcionado un UserPreferencesRepository")
}

val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("No se ha proporcionado un SnackbarHostState")
}

val LocalSnackbarScope = staticCompositionLocalOf<CoroutineScope> {
    error("No se ha proporcionado un SnackbarScope")
}
