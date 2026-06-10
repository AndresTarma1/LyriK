package com.example.melodist.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import lyrik.composeapp.generated.resources.Res
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.milliseconds

data class EqPreset(val name: String, val bands: List<Float>)

private val eqPresets = listOf(
    EqPreset("Flat", List(10) { 0f }),
    EqPreset("Rock", listOf(5f, 3f, 2f, 1f, 0f, 0f, 1f, 2f, 3f, 4f)),
    EqPreset("Pop", listOf(2f, 2f, 3f, 4f, 3f, 2f, 2f, 2f, 3f, 3f)),
    EqPreset("Jazz", listOf(4f, 3f, 2f, 1f, 2f, 3f, 2f, 2f, 3f, 4f)),
    EqPreset("Classical", listOf(5f, 4f, 3f, 2f, 1f, 0f, 0f, 1f, 3f, 4f)),
    EqPreset("Bass Boost", listOf(6f, 5f, 4f, 3f, 1f, 0f, 0f, 0f, 0f, 0f)),
    EqPreset("Dance", listOf(5f, 4f, 3f, 2f, 0f, 0f, 1f, 2f, 3f, 4f)),
    EqPreset("Acoustic", listOf(4f, 3f, 2f, 1f, 0f, 0f, 1f, 3f, 4f, 5f)),
)

@Composable
fun EqualizerPanel(
    bands: List<Float>,
    onBandsChange: (List<Float>) -> Unit,
    modifier: Modifier = Modifier
) {
    val freqs = listOf("32", "64", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")
    var localBands by remember(bands) { mutableStateOf(bands) }
    var showPresetMenu by remember { mutableStateOf(false) }

    LaunchedEffect(localBands) {
        if (localBands != bands) {
            delay(400.milliseconds)
            onBandsChange(localBands)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box {
                    TextButton(onClick = { showPresetMenu = true }) {
                        Text("Presets")
                    }
                    DropdownMenu(
                        expanded = showPresetMenu,
                        onDismissRequest = { showPresetMenu = false },
                        offset = DpOffset(x = 16.dp, y = 0.dp),
                    ) {
                        eqPresets.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.name) },
                                onClick = {
                                    localBands = preset.bands
                                    showPresetMenu = false
                                }
                            )
                        }
                    }
                }
                TextButton(onClick = { localBands = List(10) { 0f } }) {
                    Text(stringResource(Res.string.eq_reset))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            localBands.forEachIndexed { i, gain ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (gain > 0) "+${gain.toInt()}" else "${gain.toInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )

                    Spacer(Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .height(120.dp)
                            .width(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Slider(
                            value = gain,
                            onValueChange = { newVal ->
                                val newBands = localBands.toMutableList()
                                newBands[i] = newVal
                                localBands = newBands
                            },
                            valueRange = -15f..15f,
                            modifier = Modifier
                                .requiredWidth(120.dp)
                                .graphicsLayer {
                                    rotationZ = 270f
                                }
                                .pointerHoverIcon(PointerIcon.Hand)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = freqs.getOrElse(i) { "" },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }
        }
    }
}