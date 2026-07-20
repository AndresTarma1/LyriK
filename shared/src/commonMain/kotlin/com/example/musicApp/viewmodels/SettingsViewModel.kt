package com.example.musicApp.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicApp.data.repository.AppLocale
import com.example.musicApp.data.repository.AudioQuality
import com.example.musicApp.data.repository.DarkLevel
import com.example.musicApp.data.repository.IslandStyle
import com.example.musicApp.data.repository.LayoutMode
import com.example.musicApp.data.repository.NavigationRailStyle
import com.example.musicApp.data.repository.NowPlayingBackground
import com.example.musicApp.data.repository.SeekBarStyle
import com.example.musicApp.data.repository.ThemeMode
import com.example.musicApp.data.repository.ThemePalette
import com.example.musicApp.data.repository.UserPreferencesRepository
import com.example.musicApp.data.repository.YouTubeRegion
import com.example.musicApp.utils.SyncUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val syncUtils: SyncUtils,
) : ViewModel() {

    val audioQuality: StateFlow<AudioQuality> = preferencesRepository.audioQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AudioQuality.NORMAL)

    val themeMode: StateFlow<ThemeMode> = preferencesRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.DARK)

    val darkLevel: StateFlow<DarkLevel> = preferencesRepository.darkLevel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DarkLevel.DIM)

    val layoutMode: StateFlow<LayoutMode> = preferencesRepository.layoutMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LayoutMode.ISLANDS)

    val islandStyle: StateFlow<IslandStyle> = preferencesRepository.islandStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IslandStyle.COMFORTABLE)

    val dynamicColorFromArtwork: StateFlow<Boolean> = preferencesRepository.dynamicColorFromArtwork
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val highResCoverArt: StateFlow<Boolean> = preferencesRepository.highResCoverArt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val crossfadeEnabled: StateFlow<Boolean> = preferencesRepository.crossfadeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val seekBarStyle: StateFlow<SeekBarStyle> = preferencesRepository.seekBarStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SeekBarStyle.WAVY)

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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), List(5) { 0f })

    val themePalette: StateFlow<ThemePalette> = preferencesRepository.themePalette
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePalette.DEFAULT)

    val locale: StateFlow<AppLocale> = preferencesRepository.locale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppLocale.SYSTEM)

    val youtubeRegion: StateFlow<YouTubeRegion> = preferencesRepository.youtubeRegion
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), YouTubeRegion.SYSTEM)

    val ytmSyncEnabled: StateFlow<Boolean> = preferencesRepository.ytmSyncEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val offlineModeEnabled: StateFlow<Boolean> = preferencesRepository.offlineModeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Progreso de la sincronización para el botón manual "Sincronizar ahora" — ignora el enfriamiento de inicio de sesión. */
    val syncState = syncUtils.syncState

    val overlayHotkeyEnabled: StateFlow<Boolean> = preferencesRepository.overlayHotkeyEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val overlayHotkeyLabel: StateFlow<String> = preferencesRepository.overlayHotkeyLabel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val nowPlayingBackground: StateFlow<NowPlayingBackground> = preferencesRepository.nowPlayingBackground
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NowPlayingBackground.GRADIENT)

    val navigationRailStyle: StateFlow<NavigationRailStyle> = preferencesRepository.navigationRailStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NavigationRailStyle.DEFAULT)


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

    fun setIslandStyle(style: IslandStyle) {
        viewModelScope.launch { preferencesRepository.setIslandStyle(style) }
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

    fun setSeekBarStyle(style: SeekBarStyle) {
        viewModelScope.launch { preferencesRepository.setSeekBarStyle(style) }
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
        viewModelScope.launch {
            preferencesRepository.setYtmSyncEnabled(enabled)
            // Dar retroalimentación inmediata de que activar esto hace algo: reconciliar las playlists
            // vinculadas a YouTube de inmediato en lugar de esperar al siguiente inicio de sesión/restauración de sesión.
            if (enabled) syncUtils.syncAutoSyncPlaylists()
        }
    }

    fun setNowPlayingBackground(style: NowPlayingBackground) {
        viewModelScope.launch { preferencesRepository.setNowPlayingBackground(style) }
    }

    fun setNavigationRailStyle(style: NavigationRailStyle){
        viewModelScope.launch { preferencesRepository.setNavigationRailStyle(style) }
    }

    fun setOfflineModeEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setOfflineModeEnabled(enabled) }
    }

    /**
     * "Sincronizar ahora" manual — omite el enfriamiento de 30 minutos de AccountViewModel, para
     * pruebas o cuando el usuario quiere una sincronización completa ahora mismo (canciones/álbumes/artistas/playlists favoritas).
     */
    fun syncNow() {
        viewModelScope.launch {
            preferencesRepository.setLastFullSyncAt(System.currentTimeMillis())
            syncUtils.performFullSync()
            if (preferencesRepository.ytmSyncEnabled.first()) {
                syncUtils.syncAutoSyncPlaylists()
            }
        }
    }

    fun setOverlayHotkeyEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setOverlayHotkeyEnabled(enabled) }
    }

    fun setOverlayHotkey(code: Int, mods: Int, label: String) {
        viewModelScope.launch { preferencesRepository.setOverlayHotkey(code, mods, label) }
    }
}