package com.example.melodist.ui.screens.shared

import androidx.compose.runtime.Composable
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import com.example.melodist.data.repository.AppLocale
import com.example.melodist.data.repository.AudioQuality
import com.example.melodist.data.repository.DarkLevel
import com.example.melodist.data.repository.IslandStyle
import com.example.melodist.data.repository.LayoutMode
import com.example.melodist.data.repository.ThemeMode
import com.example.melodist.data.repository.ThemePalette
import com.example.melodist.data.repository.RenderApi
import com.example.melodist.data.repository.SeekBarStyle
import com.example.melodist.data.repository.YouTubeRegion
import com.example.melodist.viewmodels.LibrarySortOrder
import com.example.melodist.viewmodels.YtmLibraryFilter

@Composable
fun AudioQuality.displayName(): String = when (this) {
    AudioQuality.LOW -> stringResource(Res.string.audio_quality_low)
    AudioQuality.NORMAL -> stringResource(Res.string.audio_quality_normal)
    AudioQuality.HIGH -> stringResource(Res.string.audio_quality_high)
}

@Composable
fun ThemeMode.displayName(): String = when (this) {
    ThemeMode.SYSTEM -> stringResource(Res.string.theme_system)
    ThemeMode.DARK -> stringResource(Res.string.theme_dark)
    ThemeMode.LIGHT -> stringResource(Res.string.theme_light)
}

@Composable
fun DarkLevel.displayName(): String = when (this) {
    DarkLevel.DIM -> stringResource(Res.string.dark_level_dim)
    DarkLevel.BLACK -> stringResource(Res.string.dark_level_black)
}

@Composable
fun LayoutMode.displayName(): String = when (this) {
    LayoutMode.ISLANDS -> stringResource(Res.string.layout_islands)
    LayoutMode.ATTACHED -> stringResource(Res.string.layout_attached)
}

@Composable
fun IslandStyle.displayName(): String = when (this) {
    IslandStyle.COMPACT -> stringResource(Res.string.island_style_compact)
    IslandStyle.COMFORTABLE -> stringResource(Res.string.island_style_comfortable)
    IslandStyle.SPACIOUS -> stringResource(Res.string.island_style_spacious)
}

@Composable
fun SeekBarStyle.displayName(): String = when (this) {
    SeekBarStyle.WAVY -> stringResource(Res.string.seek_bar_style_wavy)
    SeekBarStyle.LINEAR -> stringResource(Res.string.seek_bar_style_linear)
    SeekBarStyle.MATERIAL -> stringResource(Res.string.seek_bar_style_material)
    SeekBarStyle.MINIMAL -> stringResource(Res.string.seek_bar_style_minimal)
}

@Composable
fun ThemePalette.displayName(): String = when (this) {
    ThemePalette.DEFAULT -> stringResource(Res.string.palette_default)
    ThemePalette.OCEANO -> stringResource(Res.string.palette_ocean)
    ThemePalette.BOSQUE -> stringResource(Res.string.palette_forest)
    ThemePalette.ATARDECER -> stringResource(Res.string.palette_sunset)
    ThemePalette.PURPURA -> stringResource(Res.string.palette_purple)
    ThemePalette.TEAL -> stringResource(Res.string.palette_teal)
    ThemePalette.AMBAR -> stringResource(Res.string.palette_amber)
    ThemePalette.INDIGO -> stringResource(Res.string.palette_indigo)
    ThemePalette.YTMUSIC -> stringResource(Res.string.palette_ytmusic)
}

@Composable
fun RenderApi.displayName(): String = when (this) {
    RenderApi.DIRECTX -> stringResource(Res.string.render_directx)
    RenderApi.OPENGL -> stringResource(Res.string.render_opengl)
    RenderApi.SOFTWARE -> stringResource(Res.string.render_software)
    RenderApi.ANGLE -> stringResource(Res.string.render_angle)
}

@Composable
fun RenderApi.displayDescription(): String = when (this) {
    RenderApi.DIRECTX -> stringResource(Res.string.render_directx_desc)
    RenderApi.OPENGL -> stringResource(Res.string.render_opengl_desc)
    RenderApi.SOFTWARE -> stringResource(Res.string.render_software_desc)
    RenderApi.ANGLE -> stringResource(Res.string.render_angle_desc)
}

@Composable
fun LibrarySortOrder.displayName(): String = when (this) {
    LibrarySortOrder.NAME_ASC -> stringResource(Res.string.sort_name_asc_label)
    LibrarySortOrder.NAME_DESC -> stringResource(Res.string.sort_name_desc_label)
    LibrarySortOrder.DATE_ADDED -> stringResource(Res.string.sort_date_added)
}

@Composable
fun YtmLibraryFilter.displayName(): String = when (this) {
    YtmLibraryFilter.RECENT_ACTIVITY -> stringResource(Res.string.ytm_filter_recent_activity)
    YtmLibraryFilter.RECENTLY_PLAYED -> stringResource(Res.string.ytm_filter_recently_played)
    YtmLibraryFilter.PLAYLISTS_AZ -> stringResource(Res.string.ytm_filter_playlists_az)
    YtmLibraryFilter.PLAYLISTS_RECENT -> stringResource(Res.string.ytm_filter_playlists_recent)
}

@Composable
fun AppLocale.displayName(): String = when (this) {
    AppLocale.SYSTEM -> stringResource(Res.string.language_system)
    AppLocale.ES -> stringResource(Res.string.language_spanish)
    AppLocale.EN -> stringResource(Res.string.language_english)
}

@Composable
fun YouTubeRegion.displayName(): String = when (this) {
    YouTubeRegion.SYSTEM -> stringResource(Res.string.region_system)
    YouTubeRegion.US -> stringResource(Res.string.region_us)
    YouTubeRegion.CO -> stringResource(Res.string.region_co)
    YouTubeRegion.MX -> stringResource(Res.string.region_mx)
    YouTubeRegion.AR -> stringResource(Res.string.region_ar)
    YouTubeRegion.BR -> stringResource(Res.string.region_br)
    YouTubeRegion.CL -> stringResource(Res.string.region_cl)
    YouTubeRegion.PE -> stringResource(Res.string.region_pe)
    YouTubeRegion.EC -> stringResource(Res.string.region_ec)
    YouTubeRegion.VE -> stringResource(Res.string.region_ve)
    YouTubeRegion.CA -> stringResource(Res.string.region_ca)
}
