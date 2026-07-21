package com.example.musicApp.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.expressive.SettingsMenuLink
import com.alorma.compose.settings.ui.expressive.SettingsSwitch
import com.example.musicApp.data.repository.NowPlayingBackground
import com.example.musicApp.ui.screens.shared.displayName
import com.example.musicApp.viewmodels.SettingsViewModel
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerSettingsGroup(
    viewModel: SettingsViewModel,
    colors: ListItemColors,
    onOpenSeekBarStyleDialog: () -> Unit
) {
    val highResCover by viewModel.highResCoverArt.collectAsState()
    val nowPlayingBackground by viewModel.nowPlayingBackground.collectAsState()
    val imagesEnabled by viewModel.imagesEnabled.collectAsState()
    val crossfadeEnabled by viewModel.crossfadeEnabled.collectAsState()
    val seekBarStyle by viewModel.seekBarStyle.collectAsState()
    val fullScreenPlayer by viewModel.fullScreenPlayer.collectAsState()
    var showNowPBdropdown by remember { mutableStateOf(false) }

    SettingsGroup(
        title = { Text(stringResource(Res.string.section_player)) },
        colors = colors,
    ) {
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.HighQuality, null) },
            title = { Text(stringResource(Res.string.high_res_artwork)) },
            subtitle = { Text(stringResource(Res.string.high_res_artwork_subtitle)) },
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 6),
            colors = colors,
            state = highResCover,
            onCheckedChange = { viewModel.setHighResCoverArt(it) }
        )
        DropdownSelector(
            label = stringResource(Res.string.now_playing_background),
            icon = Icons.Rounded.Style,
            expanded = showNowPBdropdown,
            colors = colors,
            currentValue = nowPlayingBackground.displayName(),
            segmentedShape = ListItemDefaults.segmentedShapes(index = 1, count = 6),
            onExpandedChange = { showNowPBdropdown = it },
            options = NowPlayingBackground.entries.map { it to it.displayName() },
            isSelected = { it == nowPlayingBackground },
            onSelect = { viewModel.setNowPlayingBackground(it) },
            paletteItem = true
        )
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.Image, null) },
            title = { Text(stringResource(Res.string.show_images)) },
            subtitle = { Text(stringResource(Res.string.show_images_subtitle)) },
            shapes = ListItemDefaults.segmentedShapes(index = 2, count = 6),
            colors = colors,
            state = imagesEnabled,
            onCheckedChange = { viewModel.setImagesEnabled(it) }
        )
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.Shuffle, null) },
            subtitle = { Text(stringResource(Res.string.crossfade_subtitle)) },
            shapes = ListItemDefaults.segmentedShapes(index = 3, count = 6),
            title = { Text(stringResource(Res.string.crossfade)) },
            colors = colors,
            state = crossfadeEnabled,
            onCheckedChange = { viewModel.setCrossfadeEnabled(it) }
        )
        SettingsMenuLink(
            icon = { Icon(Icons.AutoMirrored.Rounded.ShowChart, null) },
            shapes = ListItemDefaults.segmentedShapes(index = 4, count = 6),
            title = { Text(stringResource(Res.string.seek_bar_style)) },
            subtitle = { Text(seekBarStyle.displayName()) },
            colors = colors,
            action = {
                IconButton(onClick = onOpenSeekBarStyleDialog) {
                    Icon(Icons.Rounded.ChevronRight, null)
                }
            },
            onClick = onOpenSeekBarStyleDialog
        )
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.Fullscreen, null) },
            title = { Text(stringResource(Res.string.full_screen_player)) },
            subtitle = { Text(stringResource(Res.string.full_screen_player_subtitle)) },
            shapes = ListItemDefaults.segmentedShapes(index = 5, count = 6),
            colors = colors,
            state = fullScreenPlayer,
            onCheckedChange = { viewModel.setFullScreenPlayer(it) }
        )
    }
}
