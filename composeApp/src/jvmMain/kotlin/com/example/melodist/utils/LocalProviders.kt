package com.example.melodist.utils

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.SnackbarHostState
import com.example.melodist.data.repository.UserPreferencesRepository
import com.example.melodist.viewmodels.DownloadViewModel
import com.example.melodist.viewmodels.PlayerViewModel

val LocalPlayerViewModel = staticCompositionLocalOf<PlayerViewModel> { error("No PlayerViewModel provided") }

val LocalDownloadViewModel = staticCompositionLocalOf<DownloadViewModel> {
    error("No se ha proporcionado un DownloadViewModel")
}

val LocalUserPreferences = staticCompositionLocalOf<UserPreferencesRepository> {
    error("No se ha proporcionado un UserPreferencesRepository")
}

val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("No se ha proporcionado un SnackbarHostState")
}
