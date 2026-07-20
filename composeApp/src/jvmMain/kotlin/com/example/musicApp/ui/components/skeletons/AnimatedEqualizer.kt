package com.example.musicApp.ui.components.skeletons

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * ✅ Altamente Optimizado:
 * - Solo ejecuta animación cuando [isPlaying] es true (ahorra ciclos de CPU/GPU en inactividad).
 * - Utiliza `graphicsLayer { scaleY = anim.value }` para realizar la animación de escala
 *   completamente en la fase de dibujo (draw phase), evitando recomposiciones y re-layouts a 60 FPS.
 */
@Composable
fun AnimatedEqualizer(
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier,
    barCount: Int = 5,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    if (isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "equalizer_animation")

        val bars = List(barCount) { index ->
            infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 400 + (index * 120),
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )
        }

        Row(
            modifier = modifier.height(24.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            bars.forEach { anim ->
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .graphicsLayer {
                            scaleY = anim.value
                            transformOrigin = TransformOrigin(0.5f, 1f) // Escalado desde el fondo
                        }
                        .background(color, RoundedCornerShape(2.dp))
                )
            }
        }
    } else {
        // ✅ Icono estático cuando no reproduce — sin animación, sin costo de CPU/GPU
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            modifier = modifier
                .height(24.dp)
                .size(20.dp),
            tint = color
        )
    }
}