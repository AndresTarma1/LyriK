package com.example.musicApp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.musicApp.overlay.GlobalHotkeyManager
import com.example.musicApp.ui.components.EqualizerDialog
import com.example.musicApp.ui.components.layout.AppVerticalScrollbar
import com.example.musicApp.ui.screens.settings.*
import com.example.musicApp.utils.LocalDownloadViewModel
import com.example.musicApp.viewmodels.AppViewModel
import com.example.musicApp.viewmodels.JvmSettingsViewModel
import com.example.musicApp.viewmodels.SettingsViewModel
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

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
    var showSeekBarStyleDialog by remember { mutableStateOf(false) }
    val jvmSettingsViewModel: JvmSettingsViewModel = koinInject()
    val hotkeyManager: GlobalHotkeyManager = koinInject()
    val appViewModel: AppViewModel = koinInject()

    val colors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    )

    val equalizerBands by viewModel.equalizerBands.collectAsState()
    val seekBarStyle by viewModel.seekBarStyle.collectAsState()

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

                AudioSettingsGroup(
                    viewModel = viewModel,
                    colors = colors,
                    onOpenEqualizer = { showEqualizerDialog = true }
                )
                Spacer(Modifier.height(8.dp))

                AppearanceSettingsGroup(viewModel = viewModel, colors = colors)
                Spacer(Modifier.height(8.dp))

                PlayerSettingsGroup(
                    viewModel = viewModel,
                    colors = colors,
                    onOpenSeekBarStyleDialog = { showSeekBarStyleDialog = true }
                )
                Spacer(Modifier.height(8.dp))

                SyncSettingsGroup(
                    viewModel = viewModel,
                    colors = colors,
                    onShowYtmSyncWarning = { showYtmSyncWarning = true }
                )
                Spacer(Modifier.height(8.dp))

                OverlaySettingsGroup(
                    viewModel = viewModel,
                    colors = colors,
                    onOpenCapture = { showOverlayCapture = true }
                )
                Spacer(Modifier.height(8.dp))

                SystemSettingsGroup(
                    viewModel = viewModel,
                    colors = colors,
                    onShowClearDownloadsDialog = { showClearDownloadsDialog = true },
                    onOpenJvmSettings = { showJvmSettingsDialog = true }
                )
                Spacer(Modifier.height(8.dp))

                SupportSettingsGroup(appViewModel = appViewModel, colors = colors)

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
