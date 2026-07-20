package com.example.musicApp.ui.components.artwork

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.kmpalette.color
import com.kmpalette.loader.rememberNetworkLoader
import com.kmpalette.rememberPaletteState
import io.ktor.http.Url
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration.Companion.milliseconds

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

val LocalArtworkColors = staticCompositionLocalOf { ArtworkColors.Default }

// Opciones de caché optimizadas para lectura directa
private class ArtworkPaletteCache(private val maxSize: Int = 30) {
    private val map = LinkedHashMap<String, ArtworkColors>(maxSize, 0.75f, true)

    // Usamos funciones con Intrínsecos de sincronización simples o accesos directos
    fun get(key: String): ArtworkColors? = synchronized(map) { map[key] }

    fun put(key: String, value: ArtworkColors) = synchronized(map) {
        map[key] = value
        if (map.size > maxSize) {
            val eldest = map.keys.iterator().next()
            map.remove(eldest)
        }
    }
}

private val artworkPaletteCache = ArtworkPaletteCache()

@Composable
fun rememberArtworkColors(url: String?): ArtworkColors {
    val loader = rememberNetworkLoader()
    val paletteState = rememberPaletteState(loader = loader)

    // 1. SOLUCIÓN AL PARPADEO: Inicializamos el estado directamente buscando en la caché.
    // Si ya existe, se renderiza instantáneamente con los colores correctos sin esperar al LaunchedEffect.
    var colors by remember(url) {
        mutableStateOf(
            if (!url.isNullOrBlank()) artworkPaletteCache.get(url) ?: ArtworkColors.Default
            else ArtworkColors.Default
        )
    }

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            colors = ArtworkColors.Default
            return@LaunchedEffect
        }

        // Si la caché ya lo tenía (ya lo inicializamos arriba), no hacemos nada más
        if (artworkPaletteCache.get(url) != null) return@LaunchedEffect

        try {
            delay(120.milliseconds)
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

            artworkPaletteCache.put(url, resolved)
            colors = resolved

        } catch (e: Exception) {
            // 2. CORRECCIÓN DE CORRUTINAS: Si la excepción es por cancelación, la dejamos pasar.
            // Si no, destruimos el ciclo cooperativo de Compose.
            if (e is CancellationException) throw e
            // Conserva el último color válido en caso de error de red
        }
    }

    return colors
}