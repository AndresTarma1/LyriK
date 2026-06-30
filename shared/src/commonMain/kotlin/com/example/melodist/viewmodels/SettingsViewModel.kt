package com.example.melodist.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.melodist.data.repository.AppLocale
import com.example.melodist.data.repository.AudioQuality
import com.example.melodist.data.repository.DarkLevel
import com.example.melodist.data.repository.LayoutMode
import com.example.melodist.data.repository.ThemeMode
import com.example.melodist.data.repository.ThemePalette
import com.example.melodist.data.repository.UserPreferencesRepository
import com.example.melodist.data.repository.YouTubeRegion
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val audioQuality: StateFlow<AudioQuality> = preferencesRepository.audioQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AudioQuality.NORMAL)

    val themeMode: StateFlow<ThemeMode> = preferencesRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.DARK)

    val darkLevel: StateFlow<DarkLevel> = preferencesRepository.darkLevel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DarkLevel.DIM)

    val layoutMode: StateFlow<LayoutMode> = preferencesRepository.layoutMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LayoutMode.ISLANDS)

    val dynamicColorFromArtwork: StateFlow<Boolean> = preferencesRepository.dynamicColorFromArtwork
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val highResCoverArt: StateFlow<Boolean> = preferencesRepository.highResCoverArt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val crossfadeEnabled: StateFlow<Boolean> = preferencesRepository.crossfadeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val cacheImages: StateFlow<Boolean> = preferencesRepository.cacheImages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val imagesEnabled: StateFlow<Boolean> = preferencesRepository.imagesEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val minimizeToTray: StateFlow<Boolean> = preferencesRepository.minimizeToTray
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val trimMemoryOnTray: StateFlow<Boolean> = preferencesRepository.trimMemoryOnTray
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val launchAtStartup: StateFlow<Boolean> = preferencesRepository.launchAtStartup
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val queueLocked: StateFlow<Boolean> = preferencesRepository.queueLocked
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val equalizerBands: StateFlow<List<Float>> = preferencesRepository.equalizerBands
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), List(10) { 0f })

    val themePalette: StateFlow<ThemePalette> = preferencesRepository.themePalette
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePalette.DEFAULT)

    val locale: StateFlow<AppLocale> = preferencesRepository.locale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppLocale.SYSTEM)

    val youtubeRegion: StateFlow<YouTubeRegion> = preferencesRepository.youtubeRegion
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), YouTubeRegion.SYSTEM)

    val ytmSyncEnabled: StateFlow<Boolean> = preferencesRepository.ytmSyncEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val overlayHotkeyEnabled: StateFlow<Boolean> = preferencesRepository.overlayHotkeyEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val overlayHotkeyLabel: StateFlow<String> = preferencesRepository.overlayHotkeyLabel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setAudioQuality(quality: AudioQuality) {
        viewModelScope.launch { preferencesRepository.setAudioQuality(quality) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setDarkLevel(level: DarkLevel) {
        viewModelScope.launch { preferencesRepository.setDarkLevel(level) }
    }

    fun setLayoutMode(mode: LayoutMode) {
        viewModelScope.launch { preferencesRepository.setLayoutMode(mode) }
    }

    fun setDynamicColorFromArtwork(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setDynamicColorFromArtwork(enabled) }
    }

    fun setHighResCoverArt(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setHighResCoverArt(enabled) }
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setCrossfadeEnabled(enabled) }
    }

    fun setCacheImages(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setCacheImages(enabled) }
    }

    fun setImagesEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setImagesEnabled(enabled) }
    }

    fun setMinimizeToTray(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setMinimizeToTray(enabled) }
    }

    fun setTrimMemoryOnTray(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setTrimMemoryOnTray(enabled) }
    }

    fun setLaunchAtStartup(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setLaunchAtStartup(enabled) }
    }

    fun setQueueLocked(locked: Boolean) {
        viewModelScope.launch { preferencesRepository.setQueueLocked(locked) }
    }

    fun setEqualizerBands(bands: List<Float>) {
        viewModelScope.launch { preferencesRepository.setEqualizerBands(bands) }
    }

    fun setThemePalette(palette: ThemePalette) {
        viewModelScope.launch { preferencesRepository.setThemePalette(palette) }
    }

    fun setLocale(locale: AppLocale) {
        viewModelScope.launch { preferencesRepository.setLocale(locale) }
    }

    fun setYoutubeRegion(region: YouTubeRegion) {
        viewModelScope.launch { preferencesRepository.setYoutubeRegion(region) }
    }

    fun setYtmSyncEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setYtmSyncEnabled(enabled) }
    }

    fun setOverlayHotkeyEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setOverlayHotkeyEnabled(enabled) }
    }

    fun setOverlayHotkey(code: Int, mods: Int, label: String) {
        viewModelScope.launch { preferencesRepository.setOverlayHotkey(code, mods, label) }
    }
}