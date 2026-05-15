package com.example.melodist.ui.themes

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.example.melodist.data.repository.ThemeMode
import com.example.melodist.data.repository.ThemePalette
import com.example.melodist.ui.components.artwork.ArtworkColors
import com.materialkolor.DynamicMaterialTheme
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicMaterialThemeState
import org.koin.compose.koinInject
import com.example.melodist.data.repository.UserPreferencesRepository

val Primary = Color(0xFF687988)
val Secondary = Color(0xFF72787E)

/**
 * MelodistTheme — Refactorizado para usar MaterialKolor.
 */
@Composable
fun MelodistTheme(
    artworkColors: ArtworkColors? = null,
    userPreferences: UserPreferencesRepository,
    content: @Composable () -> Unit,
) {
    val themeMode by userPreferences.themeMode.collectAsState(ThemeMode.SYSTEM)
    val dynamicEnabled by userPreferences.dynamicColorFromArtwork.collectAsState(false)
    val palette by userPreferences.themePalette.collectAsState(ThemePalette.DEFAULT)

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

    DynamicMaterialTheme(
        state = dynamicThemeState,
        animate = true,
        content = content,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow // Un poco más rápido para reducir la sensación de lag
        )
    )
}