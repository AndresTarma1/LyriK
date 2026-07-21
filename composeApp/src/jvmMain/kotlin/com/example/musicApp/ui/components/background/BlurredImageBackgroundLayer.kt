package com.example.musicApp.ui.components.background

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.musicApp.data.repository.NowPlayingBackground
import com.example.musicApp.ui.components.images.MelodistImage
import com.example.musicApp.ui.components.artwork.ArtworkColors
import com.example.musicApp.ui.components.artwork.rememberArtworkColors
import com.example.musicApp.utils.LocalUserPreferences


@Composable
fun NowBackground(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {

    val userPreferences = LocalUserPreferences.current
    val backgroundMode by userPreferences.nowPlayingBackground.collectAsState(initial = NowPlayingBackground.GRADIENT)

    val artworkColors = rememberArtworkColors(imageUrl)

    when(backgroundMode){
        NowPlayingBackground.GRADIENT -> NowPlayingBackgroundWithGradient(
            artworkColors = artworkColors,
            modifier = modifier,
            content = content
        )
        NowPlayingBackground.BLURRED_COVER -> NowPlayingBackgroundWithBlur(
            imageUrl = imageUrl,
            modifier = modifier,
            content = content
        )
        NowPlayingBackground.SOLID_COLOR -> NowPlayingBackgroundWithSolidColor(
            modifier = modifier,
            content = content
        )
    }
}

@Composable
fun NowPlayingBackgroundWithSolidColor(
    modifier: Modifier,
    content: @Composable (BoxScope.() -> Unit)
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val rememberedContent = remember(content) { content }
        rememberedContent()
    }
}


@JvmName("NowPlayingBackgroundWithGradient")
@Composable
fun NowPlayingBackgroundWithGradient(
    artworkColors: ArtworkColors,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface

    // Creamos un degradado diagonal usando los colores extraídos.
    // Esto simula las esquinas iluminadas de una portada real sin cargar archivos.
    val ambientGradient = remember(artworkColors, surfaceColor) {
        val topColor = if (artworkColors == ArtworkColors.Default) surfaceColor else artworkColors.vibrant
        val bottomColor = if (artworkColors == ArtworkColors.Default) surfaceColor else artworkColors.darkMuted

        Brush.linearGradient(
            colors = listOf(
                topColor.copy(alpha = 0.35f),
                bottomColor.copy(alpha = 0.15f),
                surfaceColor
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surfaceColor)
            .background(ambientGradient) // Una sola pasada de dibujo nativo
    ) {
        val rememberedContent = remember(content) { content }
        rememberedContent()
    }
}


@JvmName("NowPlayingBackgroundFromImageUrlWithBlur")
@Composable
fun NowPlayingBackgroundWithBlur(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surfaceColor)
    ) {
        // Capa de la imagen de fondo borrosa y semitransparente
        if (!imageUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = 15.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .alpha(0.25f)
            ) {
                MelodistImage(
                    url = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Slot de contenido estable
        val rememberedContent = remember(content) { content }
        rememberedContent()
    }
}