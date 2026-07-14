package com.example.melodist.ui.components.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import com.example.melodist.lyrics.LyricLine

/**
 * Letras sincronizadas al estilo karaoke: la línea activa se resalta (con relleno por palabra
 * cuando existen tiempos por palabra), la vista se desplaza automáticamente para mantenerla
 * centrada y al tocar una línea se busca esa posición.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncedLyricsView(
    lines: List<LyricLine>,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    textAlign: Boolean = false, // false = centrado, true = alineado al inicio
) {
    val listState = rememberLazyListState()

    // Última línea cuyo tiempo de inicio es <= posición actual. Se recalcula en cada tick (barato
    // para ~decenas de líneas); El LaunchedEffect a continuación solo vuelve a desplazarse
    // cuando el Int realmente cambia.
    val activeIndex = run {
        var idx = -1
        for (i in lines.indices) {
            if (lines[i].timeMs <= positionMs) idx = i else break
        }
        idx
    }

    Box(modifier = modifier) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val viewportPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }
            // Anclar la línea activa cerca de la PARTE SUPERIOR (para que las letras próximas
            // sean visibles debajo), no centrada. El offset negativo mueve el elemento hacia
            // abajo desde la parte más alta.
            val topAnchorPx = (viewportPx * 0.16f).toInt()

            LaunchedEffect(activeIndex) {
                listState.animateScrollToItem(activeIndex.coerceAtLeast(0), -topAnchorPx)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                // Pequeño padding superior (para que la primera línea no esté pegada al borde pero
                // no desperdicie espacio); gran padding inferior para que las últimas líneas aún
                // puedan desplazarse hacia arriba hasta el ancla.
                contentPadding = PaddingValues(top = 16.dp, bottom = maxHeight * 0.8f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(lines, key = { i, _ -> i }) { i, line ->
                    LyricLineRow(
                        line = line,
                        positionMs = positionMs,
                        isActive = i == activeIndex,
                        isPast = i < activeIndex,
                        startAligned = textAlign,
                        onClick = { onSeek(line.timeMs) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricLineRow(
    line: LyricLine,
    positionMs: Long,
    isActive: Boolean,
    isPast: Boolean,
    startAligned: Boolean,
    onClick: () -> Unit,
) {
    val activeColor = MaterialTheme.colorScheme.onSurface
    val sungColor = MaterialTheme.colorScheme.primary
    val idleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isPast) 0.32f else 0.45f)

    // Escalamiento sutil en la línea activa para énfasis.
    val scale by animateFloatAsState(if (isActive) 1f else 0.96f, label = "lyricScale")

    val rowModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
        }
        .padding(horizontal = 8.dp, vertical = 6.dp)

    val style = MaterialTheme.typography.headlineSmall.copy(
        fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
    )
    val arrangement = if (startAligned) Arrangement.Start else Arrangement.Center

    if (line.text.isBlank()) {
        // Línea vacía tipo espaciador (por ejemplo, espacio instrumental)
        Box(rowModifier.padding(vertical = 2.dp)) {
            Text("♪", style = style, color = if (isActive) sungColor else idleColor)
        }
        return
    }

    if (isActive && line.words.isNotEmpty()) {
        // Relleno de karaoke por palabra.
        FlowRow(
            modifier = rowModifier,
            horizontalArrangement = arrangement,
        ) {
            line.words.forEach { w ->
                val color = when {
                    positionMs >= w.endMs -> sungColor
                    positionMs >= w.startMs -> {
                        val frac = ((positionMs - w.startMs).toFloat() /
                            (w.endMs - w.startMs).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                        lerp(activeColor, sungColor, frac)
                    }
                    else -> activeColor
                }
                Text(
                    text = w.text + " ",
                    style = style,
                    color = color,
                )
            }
        }
    } else {
        Text(
            text = line.text,
            style = style,
            color = if (isActive) activeColor else idleColor,
            modifier = rowModifier,
            textAlign = if (startAligned) androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
