package com.example.melodist.ui.themes

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.example.melodist.data.repository.DarkLevel
import com.example.melodist.data.repository.IslandStyle
import com.example.melodist.data.repository.LayoutMode
import com.example.melodist.data.repository.ThemeMode
import com.example.melodist.data.repository.ThemePalette
import com.example.melodist.data.repository.UserPreferencesRepository
import com.example.melodist.ui.components.artwork.ArtworkColors
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicMaterialThemeState


@Composable
fun AppTheme(
    artworkColors: ArtworkColors? = null,
    userPreferences: UserPreferencesRepository,
    content: @Composable () -> Unit,
) {
    val themeMode by userPreferences.themeMode.collectAsState(ThemeMode.SYSTEM)
    val dynamicEnabled by userPreferences.dynamicColorFromArtwork.collectAsState(false)
    val palette by userPreferences.themePalette.collectAsState(ThemePalette.DEFAULT)
    val darkLevel by userPreferences.darkLevel.collectAsState(DarkLevel.DIM)
    val layoutMode by userPreferences.layoutMode.collectAsState(LayoutMode.ISLANDS)
    val islandStyle by userPreferences.islandStyle.collectAsState(IslandStyle.COMFORTABLE)

    val isDarkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        else -> isSystemInDarkTheme()
    }

    val seeds = remember(artworkColors, dynamicEnabled, palette) {
        val basePrimary = if (dynamicEnabled && artworkColors != null && artworkColors != ArtworkColors.Default) {
            artworkColors.vibrant
        } else {
            Color(palette.primary)
        }
        val baseSecondary = if (dynamicEnabled && artworkColors != null && artworkColors != ArtworkColors.Default) {
            artworkColors.muted
        } else {
            Color(palette.secondary)
        }
        basePrimary to baseSecondary
    }

    val dynamicThemeState = rememberDynamicMaterialThemeState(
        isDark = isDarkTheme,
        style = PaletteStyle.Content,
        primary = seeds.first,
        secondary = seeds.second,
    )

    val baseScheme = dynamicThemeState.colorScheme
    val colorScheme = remember(baseScheme, isDarkTheme, darkLevel, seeds.first) {
        if (isDarkTheme) baseScheme.darkened(darkLevel, seeds.first) else baseScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = melodistTypography(),
    ) {
        CompositionLocalProvider(
            LocalDimens provides dimensFor(layoutMode, islandStyle),
            LocalLayoutMode provides layoutMode,
            content = content,
        )
    }
}

/** Re-derives the dark scheme's surfaces for the chosen [level], tinting them with [accent]. */
private fun ColorScheme.darkened(level: DarkLevel, accent: Color): ColorScheme = when (level) {
    DarkLevel.BLACK -> copy(
        background = Color(0xFF000000),
        surface = Color(0xFF000000),
        surfaceDim = Color(0xFF000000),
        surfaceBright = Color(0xFF1C1C1C),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF0A0A0A),
        surfaceContainer = Color(0xFF101010),
        surfaceContainerHigh = Color(0xFF181818),
        surfaceContainerHighest = Color(0xFF202020),
        surfaceVariant = Color(0xFF1A1A1A),
    )
    DarkLevel.DIM -> {
        fun t(baseHex: Long, amount: Float) = lerp(Color(baseHex), accent, amount)
        copy(
            background = t(0xFF0A0A0C, 0.04f),
            surface = t(0xFF121214, 0.05f),
            surfaceDim = t(0xFF0A0A0C, 0.04f),
            surfaceBright = t(0xFF242428, 0.06f),
            surfaceContainerLowest = t(0xFF0D0D0F, 0.04f),
            surfaceContainerLow = t(0xFF161618, 0.05f),
            surfaceContainer = t(0xFF1A1A1D, 0.06f),
            surfaceContainerHigh = t(0xFF222226, 0.06f),
            surfaceContainerHighest = t(0xFF2A2A2E, 0.06f),
            surfaceVariant = t(0xFF26262A, 0.05f),
        )
    }
}
