package com.example.melodist.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
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
import com.example.melodist.overlay.GlobalHotkeyManager
import com.example.melodist.overlay.HotkeyCombo.Companion.DEFAULT
import com.example.melodist.ui.components.EqualizerDialog
import com.example.melodist.ui.components.PlayerSeekBar
import com.example.melodist.ui.components.layout.AppVerticalScrollbar
import com.example.melodist.ui.screens.shared.displayName
import com.example.melodist.ui.screens.shared.openFolder
import com.example.melodist.ui.utils.circleAwareShape
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.viewmodels.AppViewModel
import com.example.melodist.viewmodels.JvmSettingsViewModel
import com.example.melodist.viewmodels.SettingsViewModel
import com.example.melodist.viewmodels.UpdateCheckState
import com.example.melodist.viewmodels.UpdateStatus
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import java.awt.Desktop
import java.net.URI
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    val hotkeyManager: GlobalHotkeyManager = koinInject()
    val appViewModel: AppViewModel = koinInject()

    var showThemeDropdown by remember { mutableStateOf(false) }
    var showDarkLevelDropdown by remember { mutableStateOf(false) }
    var showLayoutDropdown by remember { mutableStateOf(false) }
    var showIslandStyleDropdown by remember { mutableStateOf(false) }
    var showPaletteDropdown by remember { mutableStateOf(false) }
    var showAudioDropdown by remember { mutableStateOf(false) }
    var showLanguageDropdown by remember { mutableStateOf(false) }
    var showRegionDropdown by remember { mutableStateOf(false) }
    var showSeekBarStyleDialog by remember { mutableStateOf(false) }
    var showNowPBdropdown by remember { mutableStateOf(false) }

    val colors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surface,
    )


    val audioQuality by viewModel.audioQuality.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val darkLevel by viewModel.darkLevel.collectAsState()
    val layoutMode by viewModel.layoutMode.collectAsState()
    val islandStyle by viewModel.islandStyle.collectAsState()
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
    val seekBarStyle by viewModel.seekBarStyle.collectAsState()
    val ytmSyncEnabled by viewModel.ytmSyncEnabled.collectAsState()
    val offlineModeEnabled by viewModel.offlineModeEnabled.collectAsState()
    val overlayHotkeyEnabled by viewModel.overlayHotkeyEnabled.collectAsState()
    val overlayHotkeyLabel by viewModel.overlayHotkeyLabel.collectAsState()
    val nowPlayingBackground by viewModel.nowPlayingBackground.collectAsState()
    val defaultHotkeyLabel = remember { DEFAULT.label() }
    val updateCheckState by appViewModel.checkState.collectAsState()
    val updateStatus by appViewModel.updateStatus.collectAsState()

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
                    // Solo aplica al diseño de islas (el modo adjunto no tiene separaciones/esquinas).
                    if (layoutMode == LayoutMode.ISLANDS) {
                        DropdownSelector(
                            label = stringResource(Res.string.settings_island_style),
                            icon = Icons.Rounded.ViewAgenda,
                            currentValue = islandStyle.displayName(),
                            expanded = showIslandStyleDropdown,
                            onExpandedChange = { showIslandStyleDropdown = it },
                            options = IslandStyle.entries.map { it to it.displayName() },
                            isSelected = { it == islandStyle },
                            onSelect = { viewModel.setIslandStyle(it); showIslandStyleDropdown = false },
                            colors = colors,
                        )
                    }
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

                    DropdownSelector(
                        label = stringResource(Res.string.now_playing_background),
                        icon = Icons.Rounded.Style,
                        expanded = showNowPBdropdown,
                        colors = colors,
                        currentValue = nowPlayingBackground.displayName(),
                        onExpandedChange = { showNowPBdropdown = it },
                        options = NowPlayingBackground.entries.map { it to it.displayName() },
                        isSelected = { it == nowPlayingBackground},
                        onSelect = { viewModel.setNowPlayingBackground(it) },
                        paletteItem = true
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
                    SettingsMenuLink(
                        icon = { Icon(Icons.AutoMirrored.Rounded.ShowChart, null) },
                        title = { Text(stringResource(Res.string.seek_bar_style)) },
                        subtitle = { Text(seekBarStyle.displayName()) },
                        colors = colors,
                        shape = RoundedCornerShape(16.dp),
                        action = { IconButton(onClick = { showSeekBarStyleDialog = true }) {
                            Icon(Icons.Rounded.ChevronRight, null)
                        } },
                        onClick = { showSeekBarStyleDialog = true }
                    )
                }

                Spacer(Modifier.height(8.dp))
                SettingsGroup(
                    title = { Text(stringResource(Res.string.section_sync)) },
                    colors = colors,
                ) {
                    SettingsSwitch(
                        icon = { Icon(Icons.Rounded.WifiOff, null) },
                        title = { Text(stringResource(Res.string.offline_mode)) },
                        subtitle = { Text(stringResource(Res.string.offline_mode_subtitle)) },
                        colors = colors,
                        shape = RoundedCornerShape(16.dp),
                        state = offlineModeEnabled,
                        onCheckedChange = { viewModel.setOfflineModeEnabled(it) }
                    )
                    SettingsSwitch(
                        icon = { Icon(Icons.Rounded.CloudSync, null) },
                        title = { Text(stringResource(Res.string.ytm_sync)) },
                        subtitle = { Text(stringResource(Res.string.ytm_sync_subtitle)) },
                        colors = colors,
                        shape = RoundedCornerShape(16.dp),
                        state = ytmSyncEnabled,
                        onCheckedChange = { checked ->
                            // Requiere una confirmación explícita de la función experimental antes de activarla.
                            if (checked) showYtmSyncWarning = true
                            else viewModel.setYtmSyncEnabled(false)
                        }
                    )
                    val syncState by viewModel.syncState.collectAsState()
                    val isSyncing = syncState.overallStatus is com.example.melodist.utils.SyncStatus.Syncing
                    SettingsMenuLink(
                        icon = { Icon(Icons.Rounded.Sync, null) },
                        title = { Text(stringResource(Res.string.sync_now)) },
                        subtitle = {
                            Text(
                                if (isSyncing) syncState.currentOperation.ifBlank { stringResource(Res.string.sync_now_syncing) }
                                else stringResource(Res.string.sync_now_subtitle)
                            )
                        },
                        colors = colors,
                        shape = RoundedCornerShape(16.dp),
                        action = {
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        },
                        onClick = { if (!isSyncing) viewModel.syncNow() }
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
                SettingsGroup(
                    title = { Text(stringResource(Res.string.section_support)) },
                    colors = colors,
                ) {
                    val downloading = updateStatus as? UpdateStatus.Downloading
                    val ready = updateStatus is UpdateStatus.Ready
                    SettingsMenuLink(
                        icon = { Icon(Icons.Rounded.SystemUpdate, null) },
                        title = { Text(stringResource(Res.string.check_updates)) },
                        subtitle = {
                            Text(
                                when {
                                    ready -> stringResource(Res.string.check_updates_ready)
                                    downloading != null -> {
                                        val pct = downloading.progress
                                        if (pct >= 0f) "${stringResource(Res.string.check_updates_downloading)} ${(pct * 100).toInt()}%"
                                        else stringResource(Res.string.check_updates_downloading)
                                    }
                                    updateCheckState is UpdateCheckState.Checking -> stringResource(Res.string.check_updates_checking)
                                    updateCheckState is UpdateCheckState.UpToDate -> stringResource(Res.string.check_updates_up_to_date)
                                    updateCheckState is UpdateCheckState.Failed -> stringResource(Res.string.check_updates_failed)
                                    else -> stringResource(Res.string.check_updates_subtitle)
                                }
                            )
                        },
                        colors = colors,
                        shape = RoundedCornerShape(16.dp),
                        action = {
                            when {
                                ready -> {
                                    // Reabrir el diálogo de instalación a nivel de aplicación (el que controla el cierre).
                                    FilledTonalButton(onClick = { appViewModel.checkForUpdates(manual = true) }) {
                                        Text(stringResource(Res.string.btn_install_update))
                                    }
                                }
                                downloading != null || updateCheckState is UpdateCheckState.Checking ->
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                else -> TextButton(onClick = { appViewModel.checkForUpdates(manual = true) }) {
                                    Text(stringResource(Res.string.btn_check))
                                }
                            }
                        },
                        onClick = {
                            if (ready) appViewModel.checkForUpdates(manual = true)
                            else if (downloading == null && updateCheckState !is UpdateCheckState.Checking)
                                appViewModel.checkForUpdates(manual = true)
                        }
                    )
                    ActionRow(
                        label = stringResource(Res.string.report_bug),
                        subtitle = stringResource(Res.string.report_bug_subtitle),
                        icon = Icons.Rounded.BugReport,
                        btnLabel = stringResource(Res.string.btn_report),
                        onClick = { openReportBugPage() },
                        colors = colors
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
        // Activar el modo de captura de una sola tecla en el gestor de atajos mientras el diálogo
        // esté abierto; la siguiente pulsación de una tecla sin modificador se registrará como la nueva combinación.
        DisposableEffect(Unit) {
            hotkeyManager.beginCapture { combo ->
                viewModel.setOverlayHotkey(combo.keyCode, combo.modsMask, combo.label())
                showOverlayCapture = false
            }
            onDispose { hotkeyManager.cancelCapture() }
        }
        AlertDialog(
            onDismissRequest = { showOverlayCapture = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
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
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
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
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
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
        EqualizerDialog(
            onDismiss = { showEqualizerDialog = false },
            bands = equalizerBands,
            onBandsChange = { viewModel.setEqualizerBands(it) }
        )
    }

    if (showSeekBarStyleDialog) {
        ResponsiveSettingsDialog(
            onDismiss = { showSeekBarStyleDialog = false },
            icon = Icons.AutoMirrored.Rounded.ShowChart,
            title = stringResource(Res.string.seek_bar_style_title),
        ) {
            SeekBarStylePickerContent(
                current = seekBarStyle,
                onSelect = { viewModel.setSeekBarStyle(it) },
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

/**
 * Abre una página prellenada de "nuevo issue" en GitHub en lugar de construir un sistema de
 * reporte de errores dentro de la aplicación — el proyecto no tiene backend, y esto mantiene
 * los reportes revisados manualmente como cualquier otro issue.
 */
private fun openReportBugPage() {
    val os = "${System.getProperty("os.name")} ${System.getProperty("os.version")}"
    val body = "**Versión:** ${AppViewModel.CURRENT_VERSION}\n**Sistema operativo:** $os\n\nDescribe el problema:\n"
    val url = "https://github.com/AndresTarma1/LyriK/issues/new" +
        "?title=${URLEncoder.encode("[Bug] ", "UTF-8")}" +
        "&body=${URLEncoder.encode(body, "UTF-8")}"
    runCatching { Desktop.getDesktop().browse(URI(url)) }
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

/**
 * Selector visual estilo Metrolist: cada opción es una tarjeta que muestra una vista previa
 * en vivo de la barra de búsqueda renderizada en ese estilo, para que la elección se haga
 * mirando en lugar de leer una etiqueta. Al tocar se aplica de inmediato (el mini-reproductor
 * se actualiza en vivo detrás del diálogo).
 */
@Composable
private fun SeekBarStylePickerContent(
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
private fun ActionRow(
    label: String,
    icon: ImageVector,
    btnLabel: String,
    subtitle: String? = null,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
    colors: ListItemColors,
) {

    SettingsMenuLink(
        icon = { Icon(icon, null, tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
        title = { Text(label) },
        subtitle = subtitle?.let { { Text(it) } },
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
                // surfaceContainer sin overlay tonal para que AMOLED se mantenga neutro-oscuro
                // (la superficie anterior + 6dp de elevación teñía el modal con el acento, "no era negro puro").
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