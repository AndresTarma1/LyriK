package com.example.musicApp.ui.screens.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.expressive.SettingsMenuLink
import com.alorma.compose.settings.ui.expressive.SettingsSwitch
import com.example.musicApp.viewmodels.SettingsViewModel
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SyncSettingsGroup(
    viewModel: SettingsViewModel,
    colors: ListItemColors,
    onShowYtmSyncWarning: () -> Unit
) {
    val offlineModeEnabled by viewModel.offlineModeEnabled.collectAsState()
    val ytmSyncEnabled by viewModel.ytmSyncEnabled.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val isSyncing = syncState.overallStatus is com.example.musicApp.utils.SyncStatus.Syncing

    SettingsGroup(
        title = { Text(stringResource(Res.string.section_sync)) },
        colors = colors,
    ) {
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.WifiOff, null) },
            title = { Text(stringResource(Res.string.offline_mode)) },
            subtitle = { Text(stringResource(Res.string.offline_mode_subtitle)) },
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 3),
            colors = colors,
            state = offlineModeEnabled,
            onCheckedChange = { viewModel.setOfflineModeEnabled(it) }
        )
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.CloudSync, null) },
            title = { Text(stringResource(Res.string.ytm_sync)) },
            subtitle = { Text(stringResource(Res.string.ytm_sync_subtitle)) },
            shapes = ListItemDefaults.segmentedShapes(index = 1, count = 3),
            colors = colors,
            state = ytmSyncEnabled,
            onCheckedChange = { checked ->
                if (checked) onShowYtmSyncWarning()
                else viewModel.setYtmSyncEnabled(false)
            }
        )
        SettingsMenuLink(
            icon = { Icon(Icons.Rounded.Sync, null) },
            shapes = ListItemDefaults.segmentedShapes(index = 2, count = 3),
            title = { Text(stringResource(Res.string.sync_now)) },
            subtitle = {
                Text(
                    if (isSyncing) syncState.currentOperation.ifBlank { stringResource(Res.string.sync_now_syncing) }
                    else stringResource(Res.string.sync_now_subtitle)
                )
            },
            colors = colors,
            action = {
                if (isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            },
            onClick = { if (!isSyncing) viewModel.syncNow() }
        )
    }
}
