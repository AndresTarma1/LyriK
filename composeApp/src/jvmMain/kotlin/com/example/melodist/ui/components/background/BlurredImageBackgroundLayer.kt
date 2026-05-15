package com.example.melodist.ui.components.background

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import com.example.melodist.ui.components.artwork.ArtworkColors
import com.example.melodist.ui.components.artwork.rememberArtworkColors

/**
 * Fondo de pantalla difuminado usando la imagen provista.
 */
@Composable
fun BlurredImageBackground(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    darkOverlayAlpha: Float = 0.50f,
    gradientFraction: Float = 0.55f,
    content: @Composable BoxScope.() -> Unit
) {
    val artworkColors = rememberArtworkColors(imageUrl)
    BlurredImageBackground(
        artworkColors = artworkColors,
        modifier = modifier,
        darkOverlayAlpha = darkOverlayAlpha,
        gradientFraction = gradientFraction,
        content = content
    )
}

@Composable
fun BlurredImageBackground(
    artworkColors: ArtworkColors,
    modifier: Modifier = Modifier,
    darkOverlayAlpha: Float = 0.50f,
    gradientFraction: Float = 0.55f,
    content: @Composable BoxScope.() -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val isLight = surfaceColor.luminance() > 0.5f

    val topColor = if (artworkColors == ArtworkColors.Default) {
        surfaceColor
    } else {
        artworkColors.vibrant
    }
    val bottomColor = if (artworkColors == ArtworkColors.Default) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        artworkColors.darkMuted
    }

    Box(modifier = modifier.background(surfaceColor)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to topColor,
                        gradientFraction to topColor.copy(alpha = 0.72f),
                        1.00f to bottomColor
                    )
                )
        )

        val overlayColor = if (isLight) Color.White else Color.Black
        val overlayAlpha = if (isLight) 0.58f else darkOverlayAlpha

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(overlayColor.copy(alpha = overlayAlpha))
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to overlayColor.copy(alpha = if (isLight) 0.03f else 0.08f),
                        gradientFraction to overlayColor.copy(alpha = if (isLight) 0.12f else 0.28f),
                        1.00f to overlayColor.copy(alpha = if (isLight) 0.34f else 0.72f)
                    )
                )
        )

        content()
    }
}
