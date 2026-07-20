package com.example.musicApp.ui.themes

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.musicApp.data.repository.IslandStyle
import com.example.musicApp.data.repository.LayoutMode

/**
 * Spacing / sizing design tokens. A single consistent scale (replaces ad-hoc hardcoded dp) plus
 * a few values derived from the [LayoutMode] so "islands" (rounded, spaced cards) and "attached"
 * (edge-to-edge, compact) share one source of truth.
 */
data class Dimens(
    // Base spacing scale (4dp rhythm)
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,

    // Layout-mode derived
    /** Corner radius of top-level "surfaces" (cards, miniplayer, panels). 0 in attached mode. */
    val surfaceCorner: Dp,
    /** Gap between top-level surfaces. 0 in attached mode. */
    val surfaceGap: Dp,
    /** Outer padding of the window content around surfaces. */
    val windowPadding: Dp,
    /** Inner padding inside a surface/card. */
    val surfacePadding: Dp,
    /** Corner radius for inner items (list rows, thumbnails, chips). */
    val itemCorner: Dp,
    /** Drop-shadow elevation that lifts islands off the background. 0 in attached mode (flat). */
    val surfaceElevation: Dp,
)

fun dimensFor(
    layoutMode: LayoutMode,
    islandStyle: IslandStyle = IslandStyle.COMFORTABLE,
): Dimens = when (layoutMode) {
    LayoutMode.ISLANDS -> Dimens(
        surfaceCorner = islandStyle.cornerDp.dp,
        surfaceGap = islandStyle.gapDp.dp,
        windowPadding = islandStyle.gapDp.dp,
        surfacePadding = 14.dp,
        itemCorner = 10.dp,
        surfaceElevation = 10.dp,
    )
    LayoutMode.ATTACHED -> Dimens(
        surfaceCorner = 0.dp,
        surfaceGap = 0.dp,
        windowPadding = 0.dp,
        surfacePadding = 12.dp,
        itemCorner = 8.dp,
        surfaceElevation = 0.dp,
    )
}

val LocalDimens = staticCompositionLocalOf { dimensFor(LayoutMode.ISLANDS) }
val LocalLayoutMode = staticCompositionLocalOf { LayoutMode.ISLANDS }
