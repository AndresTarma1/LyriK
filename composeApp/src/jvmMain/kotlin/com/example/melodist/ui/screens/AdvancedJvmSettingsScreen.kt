package com.example.melodist.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.melodist.data.repository.JvmConfig
import com.example.melodist.data.repository.JvmRuntimeInfo
import com.example.melodist.data.repository.RenderApi
import com.example.melodist.data.repository.formatBytes
import com.example.melodist.ui.components.layout.AppVerticalScrollbar
import com.example.melodist.utils.AppRestarter
import com.example.melodist.viewmodels.JvmSettingsUiState
import com.example.melodist.viewmodels.JvmSettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedJvmSettingsScreen(
    viewModel: JvmSettingsViewModel,
    onDismiss: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val runtimeInfo by viewModel.runtimeInfo.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var showRestartDialog by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        BoxWithConstraints {
            val dialogWidth = (maxWidth * 0.9f).coerceIn(480.dp, maxWidth)
            val dialogHeight = (maxHeight * 0.9f).coerceIn(600.dp, maxHeight)

            Surface(
                modifier = Modifier.width(dialogWidth).height(dialogHeight),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                Box {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                    ) {
                        Header(onDismiss = onDismiss)

                        Spacer(Modifier.height(20.dp))
                        JvmInfoCard(runtimeInfo)

                        Spacer(Modifier.height(16.dp))
                        SectionTitle("Memoria JVM")
                        Spacer(Modifier.height(8.dp))

                        MemorySliderRow(
                            label = "Memoria maxima (-Xmx)",
                            value = uiState.xmx,
                            onValueChange = viewModel::setXmx,
                            min = 128,
                            max = 4096,
                            default = 512,
                        )

                        Spacer(Modifier.height(8.dp))

                        MemorySliderRow(
                            label = "Memoria inicial (-Xms)",
                            value = uiState.xms,
                            onValueChange = viewModel::setXms,
                            min = 64,
                            max = 2048,
                            default = 64,
                        )

                        Spacer(Modifier.height(16.dp))
                        SectionTitle("Renderizado")
                        Spacer(Modifier.height(8.dp))
                        RenderApiSelector(
                            selected = uiState.renderApi,
                            onSelected = viewModel::setRenderApi,
                        )

                        Spacer(Modifier.height(16.dp))
                        SectionTitle("Recolector de basura (GC)")
                        Spacer(Modifier.height(8.dp))

                        GcSwitchRow(
                            label = "G1 Garbage Collector",
                            description = "Optimizado para baja latencia (recomendado)",
                            checked = uiState.useG1GC,
                            onCheckedChange = viewModel::setG1GC,
                        )

                        Spacer(Modifier.height(6.dp))

                        GcSwitchRow(
                            label = "Z Garbage Collector",
                            description = "Pausas ultra-cortas (< 1ms)",
                            checked = uiState.useZGC,
                            onCheckedChange = viewModel::setZGC,
                        )

                        Spacer(Modifier.height(6.dp))

                        GcSwitchRow(
                            label = "Logging de GC",
                            description = "Registra actividad del recolector",
                            checked = uiState.gcLogging,
                            onCheckedChange = viewModel::setGcLogging,
                        )

                        ValidationErrorCard(
                            error = uiState.validationError,
                            onClear = viewModel::clearError,
                        )

                        Spacer(Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                onClick = viewModel::resetToDefaults,
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Icon(Icons.Default.Restore, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Restaurar valores")
                            }

                            Button(
                                onClick = { showRestartDialog = true },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                enabled = uiState.validationError == null,
                            ) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Aplicar y reiniciar")
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }

                    AppVerticalScrollbar(
                        state = scrollState,
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 2.dp),
                    )
                }
            }
        }
    }

    if (showRestartDialog) {
        RestartDialog(
            uiState = uiState,
            onDismiss = { showRestartDialog = false },
            onRestart = {
                scope.launch {
                    if (viewModel.saveCurrentConfig()) {
                        AppRestarter.restartWithJvmArgs(uiState.toConfig())
                    }
                }
            },
        )
    }
}

@Composable
private fun Header(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                "Configuracion avanzada de JVM",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                "Requiere reiniciar la aplicacion para aplicar cambios",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, "Cerrar")
        }
    }
}

@Composable
private fun RestartDialog(
    uiState: JvmSettingsUiState,
    onDismiss: () -> Unit,
    onRestart: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Reiniciar aplicacion") },
        text = {
            Column {
                Text("La aplicacion se reiniciara con la nueva configuracion de JVM.")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Renderer seleccionado: ${uiState.renderApi.label}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    uiState.renderApi.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Para que estos cambios surtan efecto, Melodist debe reiniciarse.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Argumentos que se aplicaran:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                AppRestarter.previewJvmArgs(uiState.toConfig()).forEach { arg ->
                    Text("  $arg", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        },
        confirmButton = {
            Button(onClick = onRestart, shape = RoundedCornerShape(8.dp)) {
                Text("Reiniciar ahora")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

private fun JvmSettingsUiState.toConfig() = JvmConfig(
    xmx = xmx,
    xms = xms,
    useG1GC = useG1GC,
    useZGC = useZGC,
    gcLogging = gcLogging,
    renderApi = renderApi,
)

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
private fun JvmInfoCard(info: JvmRuntimeInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Memory, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(
                    "Informacion de JVM",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                )
            }
            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoRow("Memoria usada", formatBytes(info.usedMemory))
                InfoRow("Memoria libre", formatBytes(info.freeMemory))
                InfoRow("Memoria maxima", formatBytes(info.maxMemory))
                InfoRow("Procesadores", "${info.processorCount}")
                InfoRow("JVM", info.jvmName)
                InfoRow("Java", info.javaVersion)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MemorySliderRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    min: Int,
    max: Int,
    default: Int,
) {
    val numericValue = remember(value) {
        value.dropLast(1).toIntOrNull() ?: default
    }
    val unit = remember(value) {
        value.lastOrNull()?.lowercaseChar() ?: 'm'
    }
    val sliderValue = remember(numericValue, unit) {
        if (unit == 'g') numericValue * 1024 else numericValue
    }.coerceIn(min, max)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.width(100.dp),
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End,
                    ),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    shape = RoundedCornerShape(8.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Slider(
                value = sliderValue.toFloat(),
                onValueChange = { v ->
                    val mb = v.toInt()
                    onValueChange(if (mb >= 1024 && mb % 1024 == 0) "${mb / 1024}g" else "${mb}m")
                },
                valueRange = min.toFloat()..max.toFloat(),
                steps = (max - min) / 64 - 1,
                modifier = Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${min}MB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${max}MB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RenderApiSelector(
    selected: RenderApi,
    onSelected: (RenderApi) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RenderApi.entries.forEach { renderApi ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelected(renderApi) }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .background(
                            if (selected == renderApi) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else Color.Transparent
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selected == renderApi,
                        onClick = { onSelected(renderApi) },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (renderApi == RenderApi.DIRECTX) "${renderApi.label} (por defecto)" else renderApi.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            renderApi.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ValidationErrorCard(
    error: String?,
    onClear: () -> Unit,
) {
    if (error == null) return

    Spacer(Modifier.height(12.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.Warning,
                null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun GcSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) }
            .pointerHoverIcon(PointerIcon.Hand)
            .background(
                if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                else Color.Transparent
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.height(32.dp),
        )
    }
}
