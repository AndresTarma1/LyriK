package com.example.melodist.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun SlimSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
    activeColor: Color = MaterialTheme.colorScheme.onSurface,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
    trackHeight: Dp = 3.dp,
    thumbSize: Dp = 8.dp,
    draggedThumbSize: Dp = 12.dp,
) {
    val density = LocalDensity.current
    var isDragging by remember { mutableStateOf(false) }
    val thumbDiameter by animateDpAsState(if (isDragging) draggedThumbSize else thumbSize)
    val fraction = value.coerceIn(0f, 1f)

    // Mantener siempre la referencia más reciente de los callbacks
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    val interaction = if (!enabled) Modifier else Modifier
        .pointerHoverIcon(PointerIcon.Hand)
        .pointerInput(Unit) {
            detectTapGestures { offset ->
                val width = size.width.takeIf { it > 0 } ?: 1
                currentOnValueChange((offset.x / width).coerceIn(0f, 1f))
                currentOnValueChangeFinished?.invoke()
            }
        }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { isDragging = true },
                onDragEnd = { isDragging = false; currentOnValueChangeFinished?.invoke() },
                onDragCancel = { isDragging = false },
            ) { change, _ ->
                change.consume()
                val width = size.width.takeIf { it > 0 } ?: 1
                currentOnValueChange((change.position.x / width).coerceIn(0f, 1f))
            }
        }

    BoxWithConstraints(modifier = modifier.height(20.dp).then(interaction)) {
        val widthPx = with(density) { maxWidth.toPx() }
        val thumbRadiusPx = with(density) { (thumbDiameter / 2).toPx() }

        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(50))
                .background(inactiveColor)
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(fraction)
                .height(trackHeight)
                .clip(RoundedCornerShape(50))
                .background(activeColor)
        )
        if (thumbDiameter > 0.dp) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset((widthPx * fraction - thumbRadiusPx).roundToInt(), 0) }
                    .size(thumbDiameter)
                    .clip(CircleShape)
                    .background(activeColor)
            )
        }
    }
}

@Composable
fun SlimVerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
) {
    val density = LocalDensity.current
    var isDragging by remember { mutableStateOf(false) }
    val thumbDiameter by animateDpAsState(if (isDragging) 12.dp else 8.dp)

    // Mantener siempre actualizados el callback y el rango
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentRange by rememberUpdatedState(valueRange)

    val span = (valueRange.endInclusive - valueRange.start).takeIf { it != 0f } ?: 1f
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val zeroFraction = ((0f - valueRange.start) / span).coerceIn(0f, 1f)

    // La función interna ahora consulta las propiedades delegadas actualizadas
    fun updateFromOffsetY(offsetY: Float, heightPx: Float) {
        val currentSpan = (currentRange.endInclusive - currentRange.start).takeIf { it != 0f } ?: 1f
        val f = (1f - offsetY / heightPx).coerceIn(0f, 1f)
        currentOnValueChange(currentRange.start + f * currentSpan)
    }

    BoxWithConstraints(
        modifier = modifier
            .width(20.dp)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val height = size.height.takeIf { it > 0 }?.toFloat() ?: 1f
                    updateFromOffsetY(offset.y, height)
                }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                ) { change, _ ->
                    change.consume()
                    val height = size.height.takeIf { it > 0 }?.toFloat() ?: 1f
                    updateFromOffsetY(change.position.y, height)
                }
            },
    ) {
        val heightPx = with(density) { maxHeight.toPx() }
        val thumbRadiusPx = with(density) { (thumbDiameter / 2).toPx() }
        val thumbOffsetY = (heightPx * (1f - fraction) - thumbRadiusPx).roundToInt()
        val valueY = 1f - fraction
        val zeroY = 1f - zeroFraction

        val fillTop = minOf(valueY, zeroY)
        val fillHeightFraction =
            (maxOf(valueY, zeroY) - fillTop).coerceIn(0f, 1f)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxHeight()
                .width(3.dp)
                .clip(RoundedCornerShape(50))
                .background(inactiveColor)
        )
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, (heightPx * fillTop).roundToInt()) }
                .fillMaxHeight(fillHeightFraction)
                .width(3.dp)
                .clip(RoundedCornerShape(50))
                .background(activeColor)
        )
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, thumbOffsetY) }
                .size(thumbDiameter)
                .clip(CircleShape)
                .background(activeColor)
        )
    }
}