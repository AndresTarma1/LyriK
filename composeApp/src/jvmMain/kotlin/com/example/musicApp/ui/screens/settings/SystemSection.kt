package com.example.musicApp.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.expressive.SettingsMenuLink
import com.alorma.compose.settings.ui.expressive.SettingsSwitch
import com.example.musicApp.data.AppDirs
import com.example.musicApp.data.repository.AppLocale
import com.example.musicApp.data.repository.YouTubeRegion
import com.example.musicApp.ui.screens.shared.displayName
import com.example.musicApp.ui.screens.shared.openFolder
import com.example.musicApp.viewmodels.SettingsViewModel
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SystemSettingsGroup(
    viewModel: SettingsViewModel,
    colors: ListItemColors,
    onShowClearDownloadsDialog: () -> Unit,
    onOpenJvmSettings: () -> Unit
) {
    val currentLocale by viewModel.locale.collectAsState()
    val youtubeRegion by viewModel.youtubeRegion.collectAsState()
    val minimizeToTray by viewModel.minimizeToTray.collectAsState()
    val trimMemoryOnTray by viewModel.trimMemoryOnTray.collectAsState()
    val launchAtStartup by viewModel.launchAtStartup.collectAsState()
    val cacheImages by viewModel.cacheImages.collectAsState()

    var showLanguageDropdown by remember { mutableStateOf(false) }
    var showRegionDropdown by remember { mutableStateOf(false) }

    SettingsGroup(
        title = { Text(stringResource(Res.string.section_system)) },
        colors = colors
    ) {
        DropdownSelector(
            label = stringResource(Res.string.language),
            icon = Icons.Rounded.Language,
            currentValue = currentLocale.displayName(),
            segmentedShape = ListItemDefaults.segmentedShapes(index = 0, count = 9),
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
            segmentedShape = ListItemDefaults.segmentedShapes(index = 1, count = 9),
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
            shapes = ListItemDefaults.segmentedShapes(index = 2, count = 9),
            colors = colors,
            state = minimizeToTray,
            onCheckedChange = { viewModel.setMinimizeToTray(it) }
        )
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.CleaningServices, null) },
            title = { Text(stringResource(Res.string.trim_memory_on_tray)) },
            subtitle = { Text(stringResource(Res.string.trim_memory_on_tray_subtitle)) },
            shapes = ListItemDefaults.segmentedShapes(index = 3, count = 9),
            colors = colors,
            state = trimMemoryOnTray,
            onCheckedChange = { viewModel.setTrimMemoryOnTray(it) }
        )
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.RocketLaunch, null) },
            title = { Text(stringResource(Res.string.launch_at_startup)) },
            subtitle = { Text(stringResource(Res.string.launch_at_startup_subtitle)) },
            shapes = ListItemDefaults.segmentedShapes(index = 4, count = 9),
            colors = colors,
            state = launchAtStartup,
            onCheckedChange = { viewModel.setLaunchAtStartup(it) }
        )
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.Image, null) },
            title = { Text(stringResource(Res.string.cache_images)) },
            shapes = ListItemDefaults.segmentedShapes(index = 5, count = 9),
            colors = colors,
            state = cacheImages,
            onCheckedChange = { viewModel.setCacheImages(it) }
        )
        ActionRow(
            label = stringResource(Res.string.open_data_folder),
            icon = Icons.Rounded.FolderOpen,
            btnLabel = stringResource(Res.string.btn_open),
            segmentedShape = ListItemDefaults.segmentedShapes(index = 6, count = 9),
            onClick = { openFolder(AppDirs.dataRoot) },
            colors = colors
        )
        ActionRow(
            label = stringResource(Res.string.clear_download_cache),
            icon = Icons.Rounded.DeleteSweep,
            btnLabel = stringResource(Res.string.btn_clear),
            segmentedShape = ListItemDefaults.segmentedShapes(index = 7, count = 9),
            isDestructive = true,
            onClick = onShowClearDownloadsDialog,
            colors = colors
        )
        SettingsMenuLink(
            icon = { Icon(Icons.Rounded.AutoFixHigh, null) },
            shapes = ListItemDefaults.segmentedShapes(index = 8, count = 9),
            title = { Text(stringResource(Res.string.skiko_rendering)) },
            colors = colors,
            subtitle = { Text(stringResource(Res.string.render_api_restart)) },
            action = {
                IconButton(onClick = onOpenJvmSettings) {
                    Icon(Icons.Rounded.ChevronRight, null)
                }
            },
            onClick = onOpenJvmSettings
        )
    }
}
