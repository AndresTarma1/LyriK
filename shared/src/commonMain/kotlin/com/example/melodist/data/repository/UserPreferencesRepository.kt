package com.example.melodist.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class AudioQuality {
    LOW, NORMAL, HIGH
}

enum class ThemeMode {
    SYSTEM, DARK, LIGHT
}

/** How dark the dark theme's surfaces are. DIM = tinted dark gray, BLACK = pure-black (AMOLED). */
enum class DarkLevel {
    DIM, BLACK
}

/** Overall layout style. ISLANDS = rounded, spaced cards (current). ATTACHED = edge-to-edge, compact. */
enum class LayoutMode {
    ISLANDS, ATTACHED
}

enum class AppLocale(val tag: String?) {
    SYSTEM(null), ES("es"), EN("en")
}

enum class YouTubeRegion(val gl: String, val hl: String) {
    SYSTEM("", ""),
    US("US", "en"),
    CO("CO", "es"),
}

enum class ThemePalette(val primary: Long, val secondary: Long) {
    DEFAULT(0xFF687988, 0xFF72787E),
    OCEANO(0xFF0288D1, 0xFF0277BD),
    BOSQUE(0xFF2E7D32, 0xFF388E3C),
    ATARDECER(0xFFC62828, 0xFFEF6C00),
    PURPURA(0xFF6A1B9A, 0xFF8E24AA),
    TEAL(0xFF00695C, 0xFF00897B),
    AMBAR(0xFFFF8F00, 0xFFFFA000),
    INDIGO(0xFF283593, 0xFF3949AB),
    YTMUSIC(0xFFFF0033, 0xFFB00020)
}

class UserPreferencesRepository(private val dataStore: DataStore<Preferences>) {

    private object PreferencesKeys {
        val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val HIGH_RES_COVER = booleanPreferencesKey("high_res_cover")
        val CROSSFADE = booleanPreferencesKey("crossfade")
        val CACHE_IMAGES = booleanPreferencesKey("cache_images")
        val IMAGES_ENABLED = booleanPreferencesKey("images_enabled")
        val MINIMIZE_TO_TRAY = booleanPreferencesKey("minimize_to_tray")
        val WINDOW_WIDTH = intPreferencesKey("window_width")
        val WINDOW_HEIGHT = intPreferencesKey("window_height")
        val WINDOW_MAXIMIZED = booleanPreferencesKey("window_maximized")
        val QUEUE_LOCKED = booleanPreferencesKey("queue_locked")
        val EQUALIZER_BANDS = stringPreferencesKey("equalizer_bands")
        val THEME_PALETTE = stringPreferencesKey("theme_palette")
        val LOCALE = stringPreferencesKey("locale")
        val YOUTUBE_REGION = stringPreferencesKey("youtube_region")
        val DARK_LEVEL = stringPreferencesKey("dark_level")
        val LAYOUT_MODE = stringPreferencesKey("layout_mode")
    }

    val darkLevel: Flow<DarkLevel> = dataStore.data.map { pref ->
        try { DarkLevel.valueOf(pref[PreferencesKeys.DARK_LEVEL] ?: DarkLevel.DIM.name) }
        catch (_: Exception) { DarkLevel.DIM }
    }

    suspend fun setDarkLevel(level: DarkLevel) {
        dataStore.edit { it[PreferencesKeys.DARK_LEVEL] = level.name }
    }

    val layoutMode: Flow<LayoutMode> = dataStore.data.map { pref ->
        try { LayoutMode.valueOf(pref[PreferencesKeys.LAYOUT_MODE] ?: LayoutMode.ISLANDS.name) }
        catch (_: Exception) { LayoutMode.ISLANDS }
    }

    suspend fun setLayoutMode(mode: LayoutMode) {
        dataStore.edit { it[PreferencesKeys.LAYOUT_MODE] = mode.name }
    }

    val audioQuality: Flow<AudioQuality> = dataStore.data.map { preferences ->
        try {
            AudioQuality.valueOf(preferences[PreferencesKeys.AUDIO_QUALITY] ?: AudioQuality.NORMAL.name)
        } catch (e: Exception) {
            AudioQuality.NORMAL
        }
    }

    val themeMode: Flow<ThemeMode> = dataStore.data.map { preferences ->
        try {
            ThemeMode.valueOf(preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.DARK.name)
        } catch (e: Exception) {
            ThemeMode.DARK
        }
    }

    val dynamicColorFromArtwork: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.DYNAMIC_COLOR] ?: false }
    val highResCoverArt: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.HIGH_RES_COVER] ?: true }
    val crossfadeEnabled: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.CROSSFADE] ?: false }
    val cacheImages: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.CACHE_IMAGES] ?: true }
    val imagesEnabled: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.IMAGES_ENABLED] ?: true }
    val minimizeToTray: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.MINIMIZE_TO_TRAY] ?: true }

    val windowWidth: Flow<Int> = dataStore.data.map { it[PreferencesKeys.WINDOW_WIDTH] ?: 1200 }
    val windowHeight: Flow<Int> = dataStore.data.map { it[PreferencesKeys.WINDOW_HEIGHT] ?: 800 }
    val windowMaximized: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.WINDOW_MAXIMIZED] ?: false }
    val queueLocked: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.QUEUE_LOCKED] ?: false }

    // Almacenamos 10 bandas separadas por coma, default a 0.0
    val equalizerBands: Flow<List<Float>> = dataStore.data.map { pref ->
        val str = pref[PreferencesKeys.EQUALIZER_BANDS] ?: "0,0,0,0,0,0,0,0,0,0"
        try {
            str.split(",").map { it.toFloat() }
        } catch(e: Exception) {
            List(10) { 0f }
        }
    }

    suspend fun setAudioQuality(quality: AudioQuality) {
        dataStore.edit { it[PreferencesKeys.AUDIO_QUALITY] = quality.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[PreferencesKeys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColorFromArtwork(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setHighResCoverArt(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.HIGH_RES_COVER] = enabled }
    }

    suspend fun setCrossfadeEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.CROSSFADE] = enabled }
    }

    suspend fun setCacheImages(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.CACHE_IMAGES] = enabled }
    }

    suspend fun setImagesEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.IMAGES_ENABLED] = enabled }
    }

    suspend fun setMinimizeToTray(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.MINIMIZE_TO_TRAY] = enabled }
    }

    suspend fun setWindowSize(width: Int, height: Int) {
        dataStore.edit {
            it[PreferencesKeys.WINDOW_WIDTH] = width
            it[PreferencesKeys.WINDOW_HEIGHT] = height
        }
    }

    suspend fun setWindowMaximized(maximized: Boolean) {
        dataStore.edit { it[PreferencesKeys.WINDOW_MAXIMIZED] = maximized }
    }

    suspend fun setQueueLocked(locked: Boolean) {
        dataStore.edit { it[PreferencesKeys.QUEUE_LOCKED] = locked }
    }

    suspend fun setEqualizerBands(bands: List<Float>) {
        dataStore.edit { it[PreferencesKeys.EQUALIZER_BANDS] = bands.joinToString(",") }
    }

    val themePalette: Flow<ThemePalette> = dataStore.data.map { pref ->
        val name = pref[PreferencesKeys.THEME_PALETTE] ?: ThemePalette.DEFAULT.name
        try { ThemePalette.valueOf(name) } catch (_: Exception) { ThemePalette.DEFAULT }
    }

    suspend fun setThemePalette(palette: ThemePalette) {
        dataStore.edit { it[PreferencesKeys.THEME_PALETTE] = palette.name }
    }

    val locale: Flow<AppLocale> = dataStore.data.map { pref ->
        val name = pref[PreferencesKeys.LOCALE] ?: AppLocale.SYSTEM.name
        try { AppLocale.valueOf(name) } catch (_: Exception) { AppLocale.SYSTEM }
    }

    suspend fun setLocale(locale: AppLocale) {
        dataStore.edit { it[PreferencesKeys.LOCALE] = locale.name }
    }

    val youtubeRegion: Flow<YouTubeRegion> = dataStore.data.map { pref ->
        val name = pref[PreferencesKeys.YOUTUBE_REGION] ?: YouTubeRegion.SYSTEM.name
        try { YouTubeRegion.valueOf(name) } catch (_: Exception) { YouTubeRegion.SYSTEM }
    }

    suspend fun setYoutubeRegion(region: YouTubeRegion) {
        dataStore.edit { it[PreferencesKeys.YOUTUBE_REGION] = region.name }
    }

}