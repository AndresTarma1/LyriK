package com.example.melodist.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Sentinel for an overlay position that has never been set by the user. */
const val OVERLAY_POS_UNSET = Int.MIN_VALUE

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
        val TRIM_MEMORY_ON_TRAY = booleanPreferencesKey("trim_memory_on_tray")
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
        val YTM_SYNC = booleanPreferencesKey("ytm_sync_enabled")
        val OVERLAY_HOTKEY_ENABLED = booleanPreferencesKey("overlay_hotkey_enabled")
        val OVERLAY_HOTKEY_CODE = intPreferencesKey("overlay_hotkey_code")
        val OVERLAY_HOTKEY_MODS = intPreferencesKey("overlay_hotkey_mods")
        val OVERLAY_HOTKEY_LABEL = stringPreferencesKey("overlay_hotkey_label")
        val OVERLAY_POS_X = intPreferencesKey("overlay_pos_x")
        val OVERLAY_POS_Y = intPreferencesKey("overlay_pos_y")
        val LT_USERNAME = stringPreferencesKey("listen_together_username")
    }

    /** Last overlay window position in dp, or [OVERLAY_POS_UNSET] when never moved (→ default corner). */
    val overlayPosX: Flow<Int> = dataStore.data.map { it[PreferencesKeys.OVERLAY_POS_X] ?: OVERLAY_POS_UNSET }
    val overlayPosY: Flow<Int> = dataStore.data.map { it[PreferencesKeys.OVERLAY_POS_Y] ?: OVERLAY_POS_UNSET }

    suspend fun setOverlayPosition(x: Int, y: Int) {
        dataStore.edit {
            it[PreferencesKeys.OVERLAY_POS_X] = x
            it[PreferencesKeys.OVERLAY_POS_Y] = y
        }
    }

    /** Remembered display name for joining/creating Listen Together rooms. */
    val listenTogetherUsername: Flow<String> = dataStore.data.map { it[PreferencesKeys.LT_USERNAME] ?: "" }

    suspend fun setListenTogetherUsername(name: String) {
        dataStore.edit { it[PreferencesKeys.LT_USERNAME] = name }
    }

    // ── Game overlay hotkey ─────────────────────────────────────────────────
    // A global shortcut that toggles a Steam-style always-on-top overlay for controlling music
    // without leaving the current game/app. The combo is stored as a jnativehook key code plus a
    // packed modifier mask (bit0 ctrl, bit1 alt, bit2 shift, bit3 meta); code 0 means "unset, use
    // the manager's built-in default". [label] is the pre-formatted human-readable combo.

    val overlayHotkeyEnabled: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.OVERLAY_HOTKEY_ENABLED] ?: true }
    val overlayHotkeyCode: Flow<Int> = dataStore.data.map { it[PreferencesKeys.OVERLAY_HOTKEY_CODE] ?: 0 }
    val overlayHotkeyMods: Flow<Int> = dataStore.data.map { it[PreferencesKeys.OVERLAY_HOTKEY_MODS] ?: 0 }
    val overlayHotkeyLabel: Flow<String> = dataStore.data.map { it[PreferencesKeys.OVERLAY_HOTKEY_LABEL] ?: "" }

    suspend fun setOverlayHotkeyEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.OVERLAY_HOTKEY_ENABLED] = enabled }
    }

    suspend fun setOverlayHotkey(code: Int, mods: Int, label: String) {
        dataStore.edit {
            it[PreferencesKeys.OVERLAY_HOTKEY_CODE] = code
            it[PreferencesKeys.OVERLAY_HOTKEY_MODS] = mods
            it[PreferencesKeys.OVERLAY_HOTKEY_LABEL] = label
        }
    }

    // ── YouTube Music sync (experimental) ───────────────────────────────────
    // When enabled, adding/removing a song in a local playlist is mirrored to a YouTube Music
    // playlist on the signed-in account. Local→remote playlist ids are stored here (not in the
    // SQLite schema) so the feature needs no destructive DB migration.

    val ytmSyncEnabled: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.YTM_SYNC] ?: false }

    suspend fun setYtmSyncEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.YTM_SYNC] = enabled }
    }

    private fun remotePlaylistKey(localId: String) = stringPreferencesKey("ytm_remote_$localId")

    /** Remote YTM playlist id mirroring a given local playlist, or null if none yet. */
    suspend fun getRemotePlaylistId(localId: String): String? =
        dataStore.data.first()[remotePlaylistKey(localId)]

    suspend fun setRemotePlaylistId(localId: String, remoteId: String) {
        dataStore.edit { it[remotePlaylistKey(localId)] = remoteId }
    }

    suspend fun clearRemotePlaylistId(localId: String) {
        dataStore.edit { it.remove(remotePlaylistKey(localId)) }
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
    val trimMemoryOnTray: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.TRIM_MEMORY_ON_TRAY] ?: true }

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

    suspend fun setTrimMemoryOnTray(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.TRIM_MEMORY_ON_TRAY] = enabled }
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