package com.example.melodist.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.melodist.data.AppDirs
import com.example.melodist.data.repository.AppLocale
import com.example.melodist.data.repository.AudioQuality
import com.example.melodist.data.repository.ThemeMode
import com.example.melodist.data.repository.ThemePalette
import com.example.melodist.ui.components.EqualizerPanel
import com.example.melodist.ui.components.layout.AppVerticalScrollbar
import com.example.melodist.ui.screens.shared.displayName
import com.example.melodist.ui.screens.shared.openFolder
import com.example.melodist.ui.utils.circleAwareShape
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.viewmodels.JvmSettingsViewModel
import com.example.melodist.viewmodels.SettingsViewModel
import lyrik.composeapp.generated.resources.Res
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val scrollState = rememberScrollState()
    val downloadViewModel = LocalDownloadViewModel.current
    val scope = rememberCoroutineScope()

    var showClearDownloadsDialog by remember { mutableStateOf(false) }
    var showAudioQualityDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showPaletteDialog by remember { mutableStateOf(false) }
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showJvmSettingsDialog by remember { mutableStateOf(false) }
    val jvmSettingsViewModel: JvmSettingsViewModel = koinInject()

    val audioQuality   by viewModel.audioQuality.collectAsState()
    val themeMode      by viewModel.themeMode.collectAsState()
    val themePalette   by viewModel.themePalette.collectAsState()
    val dynamicColor   by viewModel.dynamicColorFromArtwork.collectAsState()
    val highResCover   by viewModel.highResCoverArt.collectAsState()
    val cacheImages    by viewModel.cacheImages.collectAsState()
    val imagesEnabled  by viewModel.imagesEnabled.collectAsState()
    val minimizeToTray by viewModel.minimizeToTray.collectAsState()
    val equalizerBands by viewModel.equalizerBands.collectAsState()
    val currentLocale  by viewModel.locale.collectAsState()
    val cacheSizeText  by downloadViewModel.cacheSizeText.collectAsState()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    Text(
                        text = stringResource(Res.string.settings_title),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(Res.string.settings_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SectionLabel(stringResource(Res.string.section_audio), Icons.Rounded.GraphicEq)
                SettingsCard {
                    ModalRow(
                        label = stringResource(Res.string.streaming_quality),
                        icon = Icons.Rounded.Tune,
                        value = audioQuality.displayName(),
                        onClick = { showAudioQualityDialog = true }
                    )
                    RowDivider()
                    ModalRow(
                        label = stringResource(Res.string.equalizer),
                        icon = Icons.Rounded.GraphicEq,
                        value = stringResource(Res.string.ten_bands),
                        onClick = { showEqualizerDialog = true }
                    )
                }

                Spacer(Modifier.height(8.dp))
                SectionLabel(stringResource(Res.string.section_appearance), Icons.Rounded.Palette)
                SettingsCard {
                    ModalRow(
                        label = stringResource(Res.string.theme),
                        icon = Icons.Rounded.DarkMode,
                        value = themeMode.displayName(),
                        onClick = { showThemeDialog = true }
                    )
                    RowDivider()
                    ModalRow(
                        label = stringResource(Res.string.color_palette),
                        icon = Icons.Rounded.Palette,
                        value = themePalette.displayName(),
                        onClick = { showPaletteDialog = true }
                    )
                    RowDivider()
                    ToggleRow(
                        label = stringResource(Res.string.dynamic_colors),
                        icon = Icons.Rounded.ColorLens,
                        checked = dynamicColor,
                        onCheckedChange = { viewModel.setDynamicColorFromArtwork(it) }
                    )
                }

                Spacer(Modifier.height(8.dp))
                SectionLabel(stringResource(Res.string.section_player), Icons.Rounded.PlayCircle)
                SettingsCard {
                    ToggleRow(
                        label = stringResource(Res.string.high_res_artwork),
                        icon = Icons.Rounded.HighQuality,
                        checked = highResCover,
                        onCheckedChange = { viewModel.setHighResCoverArt(it) }
                    )
                    RowDivider()
                    ToggleRow(
                        label = stringResource(Res.string.show_images),
                        icon = Icons.Rounded.Image,
                        checked = imagesEnabled,
                        onCheckedChange = { viewModel.setImagesEnabled(it) }
                    )
                }

                Spacer(Modifier.height(8.dp))
                SectionLabel(stringResource(Res.string.section_system), Icons.Rounded.DesktopWindows)
                SettingsCard {
                    ModalRow(
                        label = stringResource(Res.string.language),
                        icon = Icons.Rounded.Language,
                        value = currentLocale.displayName(),
                        onClick = { showLanguageDialog = true }
                    )
                    RowDivider()
                    ToggleRow(
                        label = stringResource(Res.string.minimize_to_tray),
                        icon = Icons.Rounded.NotificationsActive,
                        checked = minimizeToTray,
                        onCheckedChange = { viewModel.setMinimizeToTray(it) }
                    )
                    RowDivider()
                    ToggleRow(
                        label = stringResource(Res.string.cache_images),
                        icon = Icons.Rounded.Image,
                        checked = cacheImages,
                        onCheckedChange = { viewModel.setCacheImages(it) }
                    )
                    RowDivider()
                    InfoRow(
                        label = stringResource(Res.string.download_cache),
                        icon = Icons.Rounded.FolderOpen,
                        value = cacheSizeText
                    )
                    RowDivider()
                    ActionRow(
                        label = stringResource(Res.string.open_data_folder),
                        icon = Icons.Rounded.FolderOpen,
                        btnLabel = stringResource(Res.string.btn_open),
                        onClick = { openFolder(AppDirs.dataRoot) }
                    )
                    RowDivider()
                    ActionRow(
                        label = stringResource(Res.string.clear_download_cache),
                        icon = Icons.Rounded.DeleteSweep,
                        btnLabel = stringResource(Res.string.btn_clear),
                        isDestructive = true,
                        onClick = { showClearDownloadsDialog = true }
                    )
                    RowDivider()
                    ModalRow(
                        label = stringResource(Res.string.skiko_rendering),
                        icon = Icons.Rounded.AutoFixHigh,
                        value = stringResource(Res.string.render_api_restart),
                        onClick = { showJvmSettingsDialog = true }
                    )
                }

                Spacer(Modifier.height(8.dp))
                AboutCard()
                Spacer(Modifier.height(16.dp))
            }

            AppVerticalScrollbar(
                state = scrollState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(end = 2.dp, top = 4.dp, bottom = 4.dp)
            )
        }
    }

    if (showClearDownloadsDialog) {
        AlertDialog(
            onDismissRequest = { showClearDownloadsDialog = false },
            title = { Text(stringResource(Res.string.clear_downloads_title)) },
            text = { Text(stringResource(Res.string.clear_downloads_message)) },
            confirmButton = {
                TextButton(onClick = { downloadViewModel.clearCache(); showClearDownloadsDialog = false }) {
                    Text(stringResource(Res.string.btn_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDownloadsDialog = false }) { Text(stringResource(Res.string.cancel)) }
            }
        )
    }

    if (showAudioQualityDialog) {
        ResponsiveSettingsDialog(
            onDismiss = { showAudioQualityDialog = false },
            icon = Icons.Rounded.Tune,
            title = stringResource(Res.string.audio_quality_title),
        ) {
            AudioQuality.entries.forEach { quality ->
                val isSelected = quality == audioQuality
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { viewModel.setAudioQuality(quality); showAudioQualityDialog = false }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { viewModel.setAudioQuality(quality); showAudioQualityDialog = false }
                    )
                    Column {
                        Text(quality.displayName(), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }

    if (showThemeDialog) {
        ResponsiveSettingsDialog(
            onDismiss = { showThemeDialog = false },
            icon = Icons.Rounded.DarkMode,
            title = stringResource(Res.string.theme_title),
        ) {
            ThemeMode.entries.forEach { mode ->
                val isSelected = mode == themeMode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { viewModel.setThemeMode(mode); showThemeDialog = false }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { viewModel.setThemeMode(mode); showThemeDialog = false }
                    )
                    Column {
                        Text(mode.displayName(), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }

    if (showPaletteDialog) {
        ResponsiveSettingsDialog(
            onDismiss = { showPaletteDialog = false },
            icon = Icons.Rounded.Palette,
            title = stringResource(Res.string.palette_title),
        ) {
            ThemePalette.entries.forEach { palette ->
                val isSelected = palette == themePalette
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { viewModel.setThemePalette(palette); showPaletteDialog = false }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { viewModel.setThemePalette(palette); showPaletteDialog = false }
                    )
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(palette.primary))
                    )
                    Column {
                        Text(palette.displayName(), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }

    if (showLanguageDialog) {
        ResponsiveSettingsDialog(
            onDismiss = { showLanguageDialog = false },
            icon = Icons.Rounded.Language,
            title = stringResource(Res.string.language),
        ) {
            AppLocale.entries.forEach { locale ->
                val isSelected = locale == currentLocale
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { viewModel.setLocale(locale); showLanguageDialog = false }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { viewModel.setLocale(locale); showLanguageDialog = false }
                    )
                    Column {
                        Text(locale.displayName(), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }

    if (showEqualizerDialog) {
        ResponsiveSettingsDialog(
            onDismiss = { showEqualizerDialog = false },
            icon = Icons.Rounded.GraphicEq,
            title = stringResource(Res.string.equalizer_title),
        ) {
            EqualizerPanel(
                bands = equalizerBands,
                onBandsChange = { viewModel.setEqualizerBands(it) }
            )
        }
    }

    if (showJvmSettingsDialog) {
        AdvancedJvmSettingsScreen(
            viewModel = jvmSettingsViewModel,
            onDismiss = { showJvmSettingsDialog = false },
        )
    }
}

@Composable
private fun SectionLabel(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier          = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight    = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), content = content)
    }
}

@Composable
private fun ModalRow(
    label: String,
    icon: ImageVector,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ToggleRow(
    label:           String,
    icon:            ImageVector,
    checked:         Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked         = checked,
            onCheckedChange = onCheckedChange,
            modifier        = Modifier.height(32.dp)
        )
    }
}

@Composable
private fun SegmentedRow(
    label:    String,
    icon:     ImageVector,
    options:  List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        Surface(
            shape  = RoundedCornerShape(8.dp),
            color  = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Row(modifier = Modifier.padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                options.forEachIndexed { idx, opt ->
                    val isSelected = idx == selected
                    val bg by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        animationSpec = tween(150), label = "bg$idx"
                    )
                    val fg by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(150), label = "fg$idx"
                    )
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onSelect(idx) }
                            .pointerHoverIcon(PointerIcon.Hand),
                        shape = RoundedCornerShape(6.dp),
                        color = bg
                    ) {
                        Text(
                            text = opt,
                            style    = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium),
                            color    = fg,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, icon: ImageVector, value: String) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = value,
                style    = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun ActionRow(
    label:         String,
    icon:          ImageVector,
    btnLabel:      String,
    isDestructive: Boolean = false,
    onClick:       () -> Unit
) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            icon, null,
            tint     = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

            FilledTonalButton(
                onClick        = onClick,
                shape          = RoundedCornerShape(8.dp),
                modifier       = Modifier.height(32.dp).pointerHoverIcon(PointerIcon.Hand),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
            ) {
                Text(btnLabel, style = MaterialTheme.typography.labelMedium)
            }

    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun AboutCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier              = Modifier.padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier         = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
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

            Surface(shape = circleAwareShape(), color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    text = stringResource(Res.string.version_prefix) + "0.1.3",
                    style    = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color    = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ResponsiveSettingsDialog(
    onDismiss: () -> Unit,
    icon: ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, ) {
        BoxWithConstraints {
            val maxWidth = maxWidth
            val maxHeight = maxHeight
            val dialogWidth = (maxWidth * 0.9f).coerceIn(400.dp, maxWidth)
            val dialogHeight = (maxHeight * 0.85f).coerceIn(300.dp, maxHeight)

            Surface(
                modifier = Modifier
                    .width(dialogWidth)
                    .height(dialogHeight),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            .weight(1f)
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

@Composable
private fun rememberScrollState(): androidx.compose.foundation.ScrollState {
    return androidx.compose.foundation.rememberScrollState()
}

