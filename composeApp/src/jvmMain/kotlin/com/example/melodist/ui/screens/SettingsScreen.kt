package com.example.melodist.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.window.Dialog
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.SettingsMenuLink
import com.alorma.compose.settings.ui.SettingsSwitch
import com.example.melodist.data.AppDirs
import com.example.melodist.data.repository.*
import com.example.melodist.ui.components.EqualizerPanel
import com.example.melodist.ui.components.layout.AppVerticalScrollbar
import com.example.melodist.ui.screens.shared.displayName
import com.example.melodist.ui.screens.shared.openFolder
import com.example.melodist.ui.utils.circleAwareShape
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.viewmodels.JvmSettingsViewModel
import com.example.melodist.viewmodels.SettingsViewModel
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val scrollState = rememberScrollState()
    val downloadViewModel = LocalDownloadViewModel.current

    var showClearDownloadsDialog by remember { mutableStateOf(false) }
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var showJvmSettingsDialog by remember { mutableStateOf(false) }
    var showYtmSyncWarning by remember { mutableStateOf(false) }
    var showOverlayCapture by remember { mutableStateOf(false) }
    val jvmSettingsViewModel: JvmSettingsViewModel = koinInject()
    val hotkeyManager: com.example.melodist.overlay.GlobalHotkeyManager = koinInject()

    var showThemeDropdown by remember { mutableStateOf(false) }
    var showDarkLevelDropdown by remember { mutableStateOf(false) }
    var showLayoutDropdown by remember { mutableStateOf(false) }
    var showPaletteDropdown by remember { mutableStateOf(false) }
    var showAudioDropdown by remember { mutableStateOf(false) }
    var showLanguageDropdown by remember { mutableStateOf(false) }
    var showRegionDropdown by remember { mutableStateOf(false) }

    val colors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surface,
    )


    val audioQuality by viewModel.audioQuality.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val darkLevel by viewModel.darkLevel.collectAsState()
    val layoutMode by viewModel.layoutMode.collectAsState()
    val themePalette by viewModel.themePalette.collectAsState()
    val dynamicColor by viewModel.dynamicColorFromArtwork.collectAsState()
    val highResCover by viewModel.highResCoverArt.collectAsState()
    val cacheImages by viewModel.cacheImages.collectAsState()
    val imagesEnabled by viewModel.imagesEnabled.collectAsState()
    val minimizeToTray by viewModel.minimizeToTray.collectAsState()
    val trimMemoryOnTray by viewModel.trimMemoryOnTray.collectAsState()
    val launchAtStartup by viewModel.launchAtStartup.collectAsState()
    val equalizerBands by viewModel.equalizerBands.collectAsState()
    val currentLocale by viewModel.locale.collectAsState()
    val youtubeRegion by viewModel.youtubeRegion.collectAsState()
    val crossfadeEnabled by viewModel.crossfadeEnabled.collectAsState()
    val ytmSyncEnabled by viewModel.ytmSyncEnabled.collectAsState()
    val overlayHotkeyEnabled by viewModel.overlayHotkeyEnabled.collectAsState()
    val overlayHotkeyLabel by viewModel.overlayHotkeyLabel.collectAsState()
    val defaultHotkeyLabel = remember { com.example.melodist.overlay.HotkeyCombo.DEFAULT.label() }

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

                SettingsGroup(
                    title = { Text(stringResource(Res.string.section_audio)) },
                    colors = colors,
                ) {
                    DropdownSelector(
                        label = stringResource(Res.string.streaming_quality),
                        icon = Icons.Rounded.Tune,
                        colors = colors,
                        currentValue = audioQuality.displayName(),
                        expanded = showAudioDropdown,
                        onExpandedChange = { showAudioDropdown = it },
                        options = AudioQuality.entries.map { it to it.displayName() },
                        isSelected = { it == audioQuality },
                        onSelect = { viewModel.setAudioQuality(it); showAudioDropdown = false }
                    )
                    SettingsMenuLink(
                        icon = { Icon(Icons.Rounded.GraphicEq, null) },
                        colors = colors,
                        shape = RoundedCornerShape(16.dp),
                        title = { Text(stringResource(Res.string.equalizer)) },
                        action = { IconButton(onClick ={showEqualizerDialog = true}){
                            Icon(Icons.Rounded.ChevronRight, null)
                        } },
                        subtitle = { Text(stringResource(Res.string.ten_bands)) },
                        onClick = { showEqualizerDialog = true }
                    )
                }
                Spacer(Modifier.height(8.dp))

                SettingsGroup(
                    title = { Text(stringResource(Res.string.section_appearance)) },
                    colors = colors,
                ) {
                    DropdownSelector(
                        label = stringResource(Res.string.theme),
                        icon = Icons.Rounded.DarkMode,
                        currentValue = themeMode.displayName(),
                        expanded = showThemeDropdown,
                        onExpandedChange = { showThemeDropdown = it },
                        options = ThemeMode.entries.map { it to it.displayName() },
                        isSelected = { it == themeMode },
                        onSelect = { viewModel.setThemeMode(it); showThemeDropdown = false },
                        colors = colors
                    )
                    DropdownSelector(
                        label = stringResource(Res.string.settings_dark_level),
                        icon = Icons.Rounded.Contrast,
                        currentValue = darkLevel.displayName(),
                        expanded = showDarkLevelDropdown,
                        onExpandedChange = { showDarkLevelDropdown = it },
                        options = DarkLevel.entries.map { it to it.displayName() },
                        isSelected = { it == darkLevel },
                        onSelect = { viewModel.setDarkLevel(it); showDarkLevelDropdown = false },
                        colors = colors,
                    )
                    DropdownSelector(
                        label = stringResource(Res.string.settings_layout),
                        icon = Icons.Rounded.Dashboard,
                        currentValue = layoutMode.displayName(),
                        expanded = showLayoutDropdown,
                        onExpandedChange = { showLayoutDropdown = it },
                        options = LayoutMode.entries.map { it to it.displayName() },
                        isSelected = { it == layoutMode },
                        onSelect = { viewModel.setLayoutMode(it); showLayoutDropdown = false },
                        colors = colors,
                    )
                    DropdownSelector(
                        label = stringResource(Res.string.color_palette),
                        icon = Icons.Rounded.Palette,
                        currentValue = themePalette.displayName(),
                        expanded = showPaletteDropdown,
                        onExpandedChange = { showPaletteDropdown = it },
                        options = ThemePalette.entries.map { it to it.displayName() },
                        isSelected = { it == themePalette },
                        onSelect = { viewModel.setThemePalette(it); showPaletteDropdown = false },
                        colors = colors,
                        paletteItem = true,
                    )
                    SettingsSwitch(
                        icon = { Icon(Icons.Rounded.ColorLens, null) },
                        title = { Text(stringResource(Res.string.dynamic_colors)) },
                        colors = colors,
                        shape = RoundedCornerShape(16.dp),
                        state = dynamicColor,
                        onCheckedChange = { viewModel.setDynamicColorFromArtwork(it) }
                    )
                }

                Spacer(Modifier.height(8.dp))
                SettingsGroup(
                    title = { Text(stringResource(Res.string.section_player)) },
                    colors = colors,
                ) {
                    SettingsSwitch(
                        icon = { Icon(Icons.Rounded.HighQuality, null) },
                        title = { Text(stringResource(Res.string.high_res_artwork)) },
                        subtitle = { Text(stringResource(Res.string.high_res_artwork_subtitle)) },
                        colors = colors,
                        shape = RoundedCornerShape(16.dp),
                        state = highResCover,
                        onCheckedChange = { viewModel.setHighResCoverArt(it) }
                    )
                    SettingsSwitch(
                        icon = { Icon(Icons.Rounded.Image, null) },
                        title = { Text(stringResource(Res.string.show_images)) },
                        subtitle = { Text(stringResource(Res.string.show_images_subtitle)) },
                        colors = colors,
                        shape = RoundedCornerShape(16.dp),
                        state = imagesEnabled,
                        onCheckedChange = { viewModel.setImagesEnabled(it) }
                    )
                    SettingsSwitch(
                        icon = { Icon(Icons.Rounded.Shuffle, null) },
                        subtitle = { Text(stringResource(Res.string.crossfade_subtitle)) },
                        title = { Text(stringResource(Res.string.crossfade)) },
                        colors = colors,
                        shape = RoundedCornerShape(16.dp),
                        state = crossfadeEnabled,
                        onCheckedChange = { viewModel.setCrossfadeEnabled(it) }
                    )
                }

                Spacer(Modifier.height(8.dp))
                SettingsGroup(
                    title = { Text(stringResource(Res.string.section_sync)) },
                    colors = colors,
                ) {
                    SettingsSwitch(
                        icon = { Icon(Icons.Rounded.CloudSync, null) },
                        title = { Text(stringResource(Res.string.ytm_sync)) },
                        subtitle = { Text(stringResource(Res.string.ytm_sync_subtitle)) },
                        colors = colors,
                        shape = RoundedCornerShape(16.dp),
                        state = ytmSyncEnabled,
                        onCheckedChange = { checked ->
                            // Require an explicit experimental-feature confirmation before enabling.
                            if (checked) showYtmSyncWarning = true
                            else viewModel.setYtmSyncEnabled(false)
                        }
                    )
                }

                Spacer(Modifier.height(8.dp))
                SettingsGroup(
                    title = { Text(stringResource(Res.string.section_overlay)) },
                    colors = colors,
                ) {
                    SettingsSwitch(
                        icon = { Icon(Icons.Rounded.VideogameAsset, null) },
                        title = { Text(stringResource(Res.string.overlay_enable)) },
                        subtitle = { Text(stringResource(Res.string.overlay_enable_subtitle)) },
                        colors = colors,
                        shape = RoundedCornerShape(16.dp),
                        state = overlayHotkeyEnabled,
                        onCheckedChange = { viewModel.setOverlayHotkeyEnabled(it) }
                    )
                    SettingsMenuLink(
                        icon = { Icon(Icons.Rounded.Keyboard, null) },
                        title = { Text(stringResource(Res.string.overlay_shortcut)) },
                        subtitle = { Text(overlayHotkeyLabel.ifBlank { defaultHotkeyLabel }) },
                        colors = colors,
                        shape = RoundedCornerShape(16.dp),
                        action = {
                            FilledTonalButton(onClick = { showOverlayCapture = true }) {
                                Text(stringResource(Res.string.overlay_shortcut_set))
                            }
                        },
                        onClick = { showOverlayCapture = true }
                    )
                }

                Spacer(Modifier.height(8.dp))
                SettingsGroup(
                    title = { Text(stringResource(Res.string.section_system)) },
                    colors = colors)
                {
                    DropdownSelector(
                        label = stringResource(Res.string.language),
                        icon = Icons.Rounded.Language,
                        currentValue = currentLocale.displayName(),
                        expanded = showLanguageDropdown,
                        onExpandedChange = { showLanguageDropdown = it },
                        options = AppLocale.entries.map { it to it.displayName() },
                        isSelected = { it == currentLocale },
                        onSelect = { viewModel.setLocale(it); showLanguageDropdown = false },
                        colors = colors
                    )
                    DropdownSelector(
                        label = stringResource(Res.string.youtube_region),
                        icon = Icons.Rounded.Public,
                        currentValue = youtubeRegion.displayName(),
                        expanded = showRegionDropdown,
                        onExpandedChange = { showRegionDropdown = it },
                        options = YouTubeRegion.entries.map { it to it.displayName() },
                        isSelected = { it == youtubeRegion },
                        onSelect = { viewModel.setYoutubeRegion(it); showRegionDropdown = false },
                        colors = colors
                    )
                    SettingsSwitch(
                        icon = { Icon(Icons.Rounded.NotificationsActive, null) },
                        title = { Text(stringResource(Res.string.minimize_to_tray)) },
                        colors = colors,
                        shape = RoundedCornerShape(16.dp),
                        state = minimizeToTray,
                        onCheckedChange = { viewModel.setMinimizeToTray(it) }
                    )
                    SettingsSwitch(
                        icon = { Icon(Icons.Rounded.CleaningServices, null) },
                        title = { Text(stringResource(Res.string.trim_memory_on_tray)) },
                        subtitle = { Text(stringResource(Res.string.trim_memory_on_tray_subtitle)) },
                        colors = colors,
                        shape = RoundedCornerShape(16.dp),
                        state = trimMemoryOnTray,
                        onCheckedChange = { viewModel.setTrimMemoryOnTray(it) }
                    )
                    SettingsSwitch(
                        icon = { Icon(Icons.Rounded.RocketLaunch, null) },
                        title = { Text(stringResource(Res.string.launch_at_startup)) },
                        subtitle = { Text(stringResource(Res.string.launch_at_startup_subtitle)) },
                        colors = colors,
                        shape = RoundedCornerShape(16.dp),
                        state = launchAtStartup,
                        onCheckedChange = { viewModel.setLaunchAtStartup(it) }
                    )
                    SettingsSwitch(
                        icon = { Icon(Icons.Rounded.Image, null) },
                        title = { Text(stringResource(Res.string.cache_images)) },
                        colors = colors,
                        shape = RoundedCornerShape(16.dp),
                        state = cacheImages,
                        onCheckedChange = { viewModel.setCacheImages(it) }
                    )
                    ActionRow(
                        label = stringResource(Res.string.open_data_folder),
                        icon = Icons.Rounded.FolderOpen,
                        btnLabel = stringResource(Res.string.btn_open),
                        onClick = { openFolder(AppDirs.dataRoot) },
                        colors = colors
                    )
                    ActionRow(
                        label = stringResource(Res.string.clear_download_cache),
                        icon = Icons.Rounded.DeleteSweep,
                        btnLabel = stringResource(Res.string.btn_clear),
                        isDestructive = true,
                        onClick = { showClearDownloadsDialog = true },
                        colors = colors
                    )
                    SettingsMenuLink(
                        icon = { Icon(Icons.Rounded.AutoFixHigh, null) },
                        title = { Text(stringResource(Res.string.skiko_rendering)) },
                        colors = colors,
                        shape = RoundedCornerShape(16.dp),
                        subtitle = { Text(stringResource(Res.string.render_api_restart)) },
                        action = { IconButton(onClick ={showJvmSettingsDialog = true}){
                            Icon(Icons.Rounded.ChevronRight, null)
                        } },
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

    if (showOverlayCapture) {
        // Put the hotkey manager in one-shot capture mode while the dialog is open; the next
        // non-modifier key press is recorded as the new combo.
        DisposableEffect(Unit) {
            hotkeyManager.beginCapture { combo ->
                viewModel.setOverlayHotkey(combo.keyCode, combo.modsMask, combo.label())
                showOverlayCapture = false
            }
            onDispose { hotkeyManager.cancelCapture() }
        }
        AlertDialog(
            onDismissRequest = { showOverlayCapture = false },
            icon = { Icon(Icons.Rounded.Keyboard, null) },
            title = { Text(stringResource(Res.string.overlay_capture_title)) },
            text = { Text(stringResource(Res.string.overlay_capture_message)) },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showOverlayCapture = false }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }

    if (showYtmSyncWarning) {
        AlertDialog(
            onDismissRequest = { showYtmSyncWarning = false },
            icon = { Icon(Icons.Rounded.WarningAmber, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(Res.string.ytm_sync_warning_title)) },
            text = { Text(stringResource(Res.string.ytm_sync_warning_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setYtmSyncEnabled(true)
                    showYtmSyncWarning = false
                }) { Text(stringResource(Res.string.ytm_sync_warning_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showYtmSyncWarning = false }) { Text(stringResource(Res.string.cancel)) }
            },
        )
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
private fun <T> DropdownSelector(
    label: String,
    icon: ImageVector,
    currentValue: String,
    colors: ListItemColors,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<Pair<T, String>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    paletteItem: Boolean = false,
) {
    Box {
        SettingsMenuLink(
            icon = { Icon(icon, null) },
            title = { Text(label) },
            subtitle = { Text(currentValue) },
            shape = RoundedCornerShape(16.dp),
            colors = colors,
            action = {
                IconButton(onClick = { onExpandedChange(!expanded) }) {
                    Icon(
                        Icons.Rounded.ChevronRight,
                        null,
                    )
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

@Composable
private fun ActionRow(
    label: String,
    icon: ImageVector,
    btnLabel: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
    colors: ListItemColors,
) {

    SettingsMenuLink(
        icon = { Icon(icon, null, tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
        title = { Text(label) },
        shape = RoundedCornerShape(16.dp),
        colors = colors,
        action = {
            TextButton(onClick = onClick) {
                Text(btnLabel)
            }
        },
        onClick ={}
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
                    Icons.Rounded.MusicNote,
                    null,
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

            Surface(shape = circleAwareShape(), color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    text = stringResource(Res.string.version_prefix) + com.example.melodist.viewmodels.AppViewModel.CURRENT_VERSION,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
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
    Dialog(onDismissRequest = onDismiss) {
        BoxWithConstraints {
            val maxWidth = maxWidth
            val maxHeight = maxHeight

            // Calculamos un ancho adaptativo con un máximo fijo
            val dialogWidth = (maxWidth * 0.9f).coerceAtMost(480.dp)
            // Definimos el tope máximo de alto (ej. 85% de la pantalla)
            val maxDialogHeight = maxHeight * 0.85f

            Surface(
                modifier = Modifier
                    .width(dialogWidth)
                    .heightIn(max = maxDialogHeight),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
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
                            // Quitamos el weight(1f) que causaba que se estirara al máximo disponible
                            .fillMaxWidth()
                            // El scroll se activará SOLO si el contenido supera el heightIn de la Surface
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        content = content,
                    )
                }
            }
        }
    }
}