package com.example.musicApp.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.expressive.SettingsMenuLink
import com.alorma.compose.settings.ui.expressive.SettingsSwitch
import com.example.musicApp.overlay.HotkeyCombo.Companion.DEFAULT
import com.example.musicApp.viewmodels.SettingsViewModel
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OverlaySettingsGroup(
    viewModel: SettingsViewModel,
    colors: ListItemColors,
    onOpenCapture: () -> Unit
) {
    val overlayHotkeyEnabled by viewModel.overlayHotkeyEnabled.collectAsState()
    val overlayHotkeyLabel by viewModel.overlayHotkeyLabel.collectAsState()
    val defaultHotkeyLabel = remember { DEFAULT.label() }

    SettingsGroup(
        title = { Text(stringResource(Res.string.section_overlay)) },
        colors = colors,
    ) {
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.VideogameAsset, null) },
            title = { Text(stringResource(Res.string.overlay_enable)) },
            subtitle = { Text(stringResource(Res.string.overlay_enable_subtitle)) },
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
            colors = colors,
            state = overlayHotkeyEnabled,
            onCheckedChange = { viewModel.setOverlayHotkeyEnabled(it) }
        )
        SettingsMenuLink(
            icon = { Icon(Icons.Rounded.Keyboard, null) },
            shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
            title = { Text(stringResource(Res.string.overlay_shortcut)) },
            subtitle = { Text(overlayHotkeyLabel.ifBlank { defaultHotkeyLabel }) },
            colors = colors,
            action = {
                FilledTonalButton(onClick = onOpenCapture) {
                    Text(stringResource(Res.string.overlay_shortcut_set))
                }
            },
            onClick = onOpenCapture
        )
    }
}
