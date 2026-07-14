package com.example.melodist.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.melodist.data.repository.SeekBarStyle
import ir.mahozad.multiplatform.wavyslider.WaveDirection
import ir.mahozad.multiplatform.wavyslider.material3.WavySlider

/**
 * Punto de entrada único para renderizar la barra de progreso de reproducción en el [style] que
 * el usuario haya elegido. Tanto el mini-reproductor real como la vista previa de ajustes pasan
 * por aquí, para que un estilo siempre se vea igual en el selector que en el reproductor. Pasa
 * [enabled] = false para una vista previa estática sin búsqueda.
 */
@Composable
fun PlayerSeekBar(
    style: SeekBarStyle,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    enabled: Boolean = true,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
) {
    Box(modifier = modifier.height(24.dp), contentAlignment = Alignment.Center) {
        when (style) {
            SeekBarStyle.WAVY -> WavySlider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                enabled = enabled,
                waveLength = 102.dp,
                waveHeight = if (isPlaying) 10.dp else 0.dp,
                waveVelocity = if (isPlaying) 28.dp to WaveDirection.TAIL else 0.dp to WaveDirection.TAIL,
                waveThickness = 8.dp,
                trackThickness = 9.dp,
                incremental = false,
            )

            SeekBarStyle.LINEAR -> SlimSlider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                trackHeight = 4.dp,
                thumbSize = 12.dp,
                draggedThumbSize = 16.dp,
            )

            SeekBarStyle.MATERIAL -> Slider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = activeColor,
                    activeTrackColor = activeColor,
                    inactiveTrackColor = inactiveColor,
                ),
            )

            SeekBarStyle.MINIMAL -> SlimSlider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                trackHeight = 2.dp,
                thumbSize = 0.dp,
                draggedThumbSize = 10.dp,
            )
        }
    }
}
