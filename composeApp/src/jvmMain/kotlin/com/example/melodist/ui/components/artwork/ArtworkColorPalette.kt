package com.example.melodist.ui.components.artwork

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.kmpalette.color
import com.kmpalette.loader.rememberNetworkLoader
import com.kmpalette.rememberPaletteState
import io.ktor.http.Url
import kotlinx.coroutines.delay


/**
 * Extracted color palette from a song's artwork.
 */
data class ArtworkColors(
    val dominant: Color,
    val vibrant: Color,
    val muted: Color,
    val darkMuted: Color,
    val isLight: Boolean
) {
    companion object {
        val Default = ArtworkColors(
            dominant = Color(0xFF1A1A2E),
            vibrant = Color(0xFF6C63FF),
            muted = Color(0xFF2D2D44),
            darkMuted = Color(0xFF121225),
            isLight = false
        )
    }
}

/**
 * CompositionLocal that provides artwork colors from the App level.
 */
val LocalArtworkColors = staticCompositionLocalOf { ArtworkColors.Default }

/**
 * ✅ LRU cache con tamaño máximo para evitar consumo excesivo de memoria.
 * Limita a 30 entradas (30 carátulas ≈ ~3MB en paletas vs crecimiento ilimitado anterior).
 */
private class ArtworkPaletteCache(private val maxSize: Int = 30) : LinkedHashMap<String, ArtworkColors>(maxSize, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArtworkColors>?): Boolean {

        return size > maxSize
    }
}

private val artworkPaletteCache = ArtworkPaletteCache()

@Composable
fun rememberArtworkColors(url: String?): ArtworkColors {
    val loader = rememberNetworkLoader()
    val paletteState = rememberPaletteState(loader = loader)

    var colors by remember { mutableStateOf(ArtworkColors.Default) }

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            colors = ArtworkColors.Default
            return@LaunchedEffect
        }

        synchronized(artworkPaletteCache) {
            artworkPaletteCache[url]?.let {
                colors = it
                return@LaunchedEffect
            }
        }

        try {
            // ✅ Delay de 200ms reducido a 120ms — respuesta más rápida sin parpadeo
            delay(120)
            paletteState.generate(Url(url))

            val dominant = paletteState.palette?.dominantSwatch?.color
            val vibrant = paletteState.palette?.vibrantSwatch?.color
            val muted = paletteState.palette?.mutedSwatch?.color
            val darkMuted = paletteState.palette?.darkMutedSwatch?.color

            val finalDominant = dominant ?: Color(0xFF1A1A2E)

            val resolved = ArtworkColors(
                dominant = finalDominant,
                vibrant = vibrant ?: finalDominant,
                muted = muted ?: finalDominant,
                darkMuted = darkMuted ?: finalDominant,
                isLight = finalDominant.luminance() > 0.5f
            )
            synchronized(artworkPaletteCache) {
                artworkPaletteCache[url] = resolved
            }
            colors = resolved

        } catch (_: Exception) {
            // Conserva el último color válido para evitar parpadeos al fallar una portada.
        }
    }

    return colors
}
