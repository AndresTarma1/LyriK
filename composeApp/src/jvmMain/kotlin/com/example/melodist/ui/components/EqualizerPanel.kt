package com.example.melodist.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import lyrik.composeapp.generated.resources.Res
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun EqualizerPanel(
    bands: List<Float>,
    onBandsChange: (List<Float>) -> Unit,
    modifier: Modifier = Modifier
) {
    val freqs = listOf("32", "64", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")
    var localBands by remember(bands) { mutableStateOf(bands) }

    LaunchedEffect(localBands, bands) {
        if (localBands != bands) {
            delay(400)
            onBandsChange(localBands)
        }
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Rounded.GraphicEq,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(Res.string.eq_gain),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { localBands = List(10) { 0f } }) {
                Text(stringResource(Res.string.eq_reset))
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            localBands.forEachIndexed { i, gain ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (gain > 0) "+${gain.toInt()}" else "${gain.toInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Slider(
                        value = gain,
                        onValueChange = { newVal ->
                            val newBands = localBands.toMutableList()
                            newBands[i] = newVal
                            localBands = newBands
                        },
                        valueRange = -15f..15f,
                        modifier = Modifier
                            .width(240.dp)
                            .graphicsLayer {
                                rotationZ = 270f
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                            }
                            .pointerHoverIcon(PointerIcon.Hand)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = freqs[i],
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
