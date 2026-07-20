package com.example.musicApp.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.alorma.compose.settings.ui.expressive.SettingsMenuLink
import com.example.musicApp.data.repository.SeekBarStyle
import com.example.musicApp.data.repository.ThemePalette
import com.example.musicApp.ui.components.PlayerSeekBar
import com.example.musicApp.ui.screens.shared.displayName
import com.example.musicApp.viewmodels.AppViewModel
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop
import java.net.URI
import java.net.URLEncoder

internal fun openReportBugPage() {
    val os = "${System.getProperty("os.name")} ${System.getProperty("os.version")}"
    val body = "**Versión:** ${AppViewModel.CURRENT_VERSION}\n**Sistema operativo:** $os\n\nDescribe el problema:\n"
    val url = "https://github.com/AndresTarma1/LyriK/issues/new" +
        "?title=${URLEncoder.encode("[Bug] ", "UTF-8")}" +
        "&body=${URLEncoder.encode(body, "UTF-8")}"
    runCatching { Desktop.getDesktop().browse(URI(url)) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun <T> DropdownSelector(
    label: String,
    icon: ImageVector,
    currentValue: String,
    colors: ListItemColors,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<Pair<T, String>>,
    segmentedShape: ListItemShapes = ListItemDefaults.shapes(),
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    paletteItem: Boolean = false,
) {
    Box {
        SettingsMenuLink(
            icon = { Icon(icon, null) },
            title = { Text(label) },
            subtitle = { Text(currentValue) },
            shapes = segmentedShape,
            colors = colors,
            action = {
                IconButton(onClick = { onExpandedChange(!expanded) }) {
                    Icon(Icons.Rounded.ChevronRight, null)
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { onExpandedChange(false) },
                        offset = DpOffset(x = 16.dp, y = 0.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        shadowElevation = 8.dp,
                    ) {
                        options.forEach { (value, displayName) ->
                            DropdownMenuItem(
                                text = { Text(displayName) },
                                onClick = { onSelect(value) },
                                leadingIcon = {
                                    if (paletteItem && value is ThemePalette) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(value.primary))
                                        )
                                    } else if (isSelected(value)) {
                                        Icon(
                                            Icons.Rounded.Check, null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            },
            onClick = { onExpandedChange(!expanded) }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ActionRow(
    label: String,
    icon: ImageVector,
    btnLabel: String,
    subtitle: String? = null,
    segmentedShape: ListItemShapes = ListItemDefaults.shapes(),
    isDestructive: Boolean = false,
    onClick: () -> Unit,
    colors: ListItemColors,
) {
    SettingsMenuLink(
        icon = { Icon(icon, null, tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
        title = { Text(label) },
        subtitle = subtitle?.let { { Text(it) } },
        shapes = segmentedShape,
        colors = colors,
        action = {
            TextButton(onClick = onClick) {
                Text(btnLabel)
            }
        },
        onClick = onClick
    )
}

@Composable
internal fun AboutCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.MusicNote, null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.about_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(Res.string.about_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = com.example.musicApp.ui.utils.circleAwareShape(),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = stringResource(Res.string.version_prefix) + AppViewModel.CURRENT_VERSION,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
internal fun SeekBarStylePickerContent(
    current: SeekBarStyle,
    onSelect: (SeekBarStyle) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SeekBarStyle.entries.forEach { style ->
            val selected = style == current
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { onSelect(style) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        style.displayName(),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        null,
                        tint = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                PlayerSeekBar(
                    style = style,
                    value = 0.42f,
                    onValueChange = {},
                    onValueChangeFinished = {},
                    modifier = Modifier.fillMaxWidth(),
                    isPlaying = true,
                    enabled = false,
                )
            }
        }
    }
}

@Composable
internal fun ResponsiveSettingsDialog(
    onDismiss: () -> Unit,
    icon: ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        BoxWithConstraints {
            val maxWidth = maxWidth
            val maxHeight = maxHeight
            val dialogWidth = (maxWidth * 0.9f).coerceAtMost(480.dp)
            val maxDialogHeight = maxHeight * 0.85f

            Surface(
                modifier = Modifier
                    .width(dialogWidth)
                    .heightIn(max = maxDialogHeight),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp,
            ) {
                Column(modifier = Modifier.height(IntrinsicSize.Min)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, stringResource(Res.string.close_label))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        content = content,
                    )
                }
            }
        }
    }
}
