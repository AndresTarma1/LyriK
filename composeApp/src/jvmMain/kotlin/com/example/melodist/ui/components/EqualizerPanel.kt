package com.example.melodist.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
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
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun EqualizerPanel(
    bands: List<Float>,
    onBandsChange: (List<Float>) -> Unit,
    modifier: Modifier = Modifier
) {
    val freqs = listOf("32", "64", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")
    var localBands by remember(bands) { mutableStateOf(bands) }

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
            TextButton(onClick = { localBands = List(10) { 0f } }) {
                Text(stringResource(Res.string.eq_reset))
            }
        }

        Spacer(Modifier.height(16.dp))

        // Fila contenedora de las 10 bandas
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            localBands.forEachIndexed { i, gain ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f) // Distribuye las 10 columnas por igual
                ) {
                    // Valor numérico de los dB
                    Text(
                        text = if (gain > 0) "+${gain.toInt()}" else "${gain.toInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )

                    Spacer(Modifier.height(8.dp))

                    // EL TRUCO ESTÁ AQUÍ:
                    // El Box define el espacio FÍSICO real en la pantalla (Ancho angosto, Alto libre)
                    Box(
                        modifier = Modifier
                            .height(120.dp) // El alto que tendrá tu ecualizador visualmente
                            .width(32.dp),   // Evita que los sliders se expandan horizontalmente y se pisen
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
                                // Forzamos a que el ancho original sea exactamente el alto final deseado
                                .requiredWidth(120.dp)
                                .graphicsLayer {
                                    rotationZ = 270f // Lo giramos verticalmente
                                }
                                .pointerHoverIcon(PointerIcon.Hand)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Texto de la Frecuencia (32, 64, etc.)
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