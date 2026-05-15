package com.example.melodist.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.example.melodist.ui.components.layout.HorizontalScrollableRow

/**
 * CompositionLocal que provee el valor de traducción del shimmer.
 * Una sola infiniteTransition se crea en el nivel más alto (ej. la pantalla)
 * y todos los skeleton components la comparten, evitando N animaciones paralelas.
 *
 * Uso:
 *   ProvideShimmerTransition {
 *       ChipRowSkeleton()
 *       SectionSkeleton()
 *       SongSkeleton()
 *   }
 */
val LocalShimmerTranslation = staticCompositionLocalOf { 0f }

/**
 * Envuelve contenido skeleton con UNA sola animación shimmer compartida.
 * Coloca este composable en el nivel de la pantalla (HomeScreen, SearchScreen, etc.)
 * para que todos los skeleton hijos compartan la misma animación.
 */
@Composable
fun ProvideShimmerTransition(content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnimation by transition.animateFloat(
        initialValue = -1000f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslation"
    )
    CompositionLocalProvider(LocalShimmerTranslation provides translateAnimation) {
        content()
    }
}

/**
 * Crea un brush shimmer usando la animación compartida del CompositionLocal.
 * Si no hay ProvideShimmerTransition en el árbol, crea su propia animación
 * como fallback (para previews, etc.)
 */
@Composable
fun shimmerBrush(): Brush {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    )

    // Reutilizar la animación compartida si está disponible
    val translateAnimation = LocalShimmerTranslation.current

    // Si el valor es 0f y no hay Provider activo, crear una animación local como fallback
    val translate = if (translateAnimation != 0f) {
        translateAnimation
    } else {
        val transition = rememberInfiniteTransition(label = "shimmerFallback")
        val anim by transition.animateFloat(
            initialValue = -1000f,
            targetValue = 1000f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmerFallbackTranslation"
        )
        anim
    }

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translate, translate),
        end = Offset(translate + 500f, translate + 500f)
    )
}

@Composable
fun ChipRowSkeleton() {
    val brush = shimmerBrush()

    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(3) {
            Box(
                modifier = Modifier
                    .width(90.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(brush)
            )
        }
    }
}

@Composable
fun SectionSkeleton() {
    val brush = shimmerBrush()
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
    ) {
        // Title row — matches headlineMedium + padding(horizontal=24, vertical=8)
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .width(180.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )

        val scrollState = rememberLazyListState()
        HorizontalScrollableRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            state = scrollState,
        ) {
            items(8) {
                // Matches MusicItem: width 200dp, padding 8dp, aspectRatio 1f
                Column(
                    modifier = Modifier
                        .width(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(brush)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    // Title — bodyLarge Bold
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Artist — bodySmall
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(13.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush)
                    )
                }
            }
        }
    }
}

@Composable
fun SongSkeleton() {
    val brush = shimmerBrush()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(52.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier.width(180.dp).height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier.width(100.dp).height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
        }

        Box(
            modifier = Modifier.size(24.dp)
                .clip(CircleShape)
                .background(brush)
        )
    }
}

