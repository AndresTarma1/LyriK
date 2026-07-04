package com.example.melodist

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.example.melodist.data.repository.AppLocale
import com.example.melodist.data.repository.ThemeMode
import com.example.melodist.data.repository.UserPreferencesRepository
import com.example.melodist.data.repository.YouTubeRegion
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeLocale
import java.util.Locale
import com.example.melodist.navigation.NavigationDesktop
import com.example.melodist.navigation.RootComponent
import com.example.melodist.player.PlaybackState
import com.example.melodist.ui.components.artwork.LocalArtworkColors
import com.example.melodist.ui.components.artwork.rememberArtworkColors
import com.example.melodist.ui.components.skeletons.AnimatedEqualizer
import com.example.melodist.ui.themes.MelodistTheme
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.utils.LocalPlaylistsViewModel
import com.example.melodist.utils.LocalSnackbarHostState
import com.example.melodist.utils.LocalSnackbarScope
import com.example.melodist.utils.LocalUserPreferences
import com.example.melodist.viewmodels.AppViewModel
import com.example.melodist.viewmodels.UpdateDownloadState
import com.example.melodist.viewmodels.DownloadViewModel
import com.example.melodist.viewmodels.LibraryPlaylistsViewModel
import com.example.melodist.viewmodels.PlayerUiState
import com.example.melodist.viewmodels.PlayerViewModel
import org.koin.compose.koinInject
import com.example.melodist.overlay.GlobalHotkeyManager
import com.example.melodist.overlay.HotkeyCombo
import com.example.melodist.overlay.MusicOverlayWindow
import com.example.melodist.overlay.OverlayController
import com.kdroid.composetray.tray.api.Tray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import lyrik.composeapp.generated.resources.Music_note_circle
import lyrik.composeapp.generated.resources.Res
import lyrik.composeapp.generated.resources.app_name
import lyrik.composeapp.generated.resources.cancel
import lyrik.composeapp.generated.resources.sync_now
import lyrik.composeapp.generated.resources.sync_now_syncing
import lyrik.composeapp.generated.resources.tray_exit
import lyrik.composeapp.generated.resources.tray_next
import lyrik.composeapp.generated.resources.tray_open
import lyrik.composeapp.generated.resources.tray_pause
import lyrik.composeapp.generated.resources.tray_play
import lyrik.composeapp.generated.resources.tray_previous
import lyrik.composeapp.generated.resources.update_download
import lyrik.composeapp.generated.resources.update_install
import lyrik.composeapp.generated.resources.update_downloading
import lyrik.composeapp.generated.resources.update_installing
import lyrik.composeapp.generated.resources.update_failed
import lyrik.composeapp.generated.resources.update_later
import lyrik.composeapp.generated.resources.update_message
import lyrik.composeapp.generated.resources.update_title
import lyrik.composeapp.generated.resources.ytm_sync
import lyrik.composeapp.generated.resources.ytm_sync_warning_confirm
import lyrik.composeapp.generated.resources.ytm_sync_warning_message
import lyrik.composeapp.generated.resources.ytm_sync_warning_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.intui.window.styling.dark
import org.jetbrains.jewel.intui.window.styling.light
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.TitleBarScope
import org.jetbrains.jewel.window.styling.TitleBarColors
import org.jetbrains.jewel.window.styling.TitleBarStyle
import java.awt.Desktop
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Frame
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent


@Composable
fun ApplicationScope.App(
    rootComponent: RootComponent,
    appViewModel: AppViewModel,
    playerViewModel: PlayerViewModel,
    downloadViewModel: DownloadViewModel,
    userPreferences: UserPreferencesRepository,
    onExit: () -> Unit,
    windowState: WindowState,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val updateInfo by appViewModel.updateInfo.collectAsState()

    // Window size/placement is now set at construction in main() (read from prefs before the window
    // is built), so it opens in the right placement on the first frame — no async placement race.

    LaunchedEffect(Unit) {
        appViewModel.checkForUpdates()
    }

    var isVisible by remember { mutableStateOf(false) }
    val minimizeToTray by remember { userPreferences.minimizeToTray }.collectAsState(false)

    // When the window is hidden to the tray, return idle RAM to the OS (Skia surfaces, buffers,
    // thread stacks sitting resident). Pages fault back on restore; doing it only while hidden
    // avoids any visible stutter. Re-keys to true before the delay fires at startup, so it never
    // trims during normal use.
    val trimMemoryOnTray by remember(userPreferences) { userPreferences.trimMemoryOnTray }.collectAsState(true)
    LaunchedEffect(isVisible, trimMemoryOnTray) {
        if (!isVisible && trimMemoryOnTray) {
            kotlinx.coroutines.delay(2000)
            com.example.melodist.utils.WorkingSetTrimmer.trim()
        }
    }

    // Launch-at-Windows-startup: keep the registry Run key in sync with the preference. Doing the
    // registry write here (JVM/desktop layer) keeps SettingsViewModel pure. AutoLaunch resolves the
    // real LyriK.exe in the packaged app; in a dev run it points at the dev launcher.
    LaunchedEffect(Unit) {
        val autoLaunch = io.github.vinceglb.autolaunch.AutoLaunch(appPackageName = "LyriK")
        userPreferences.launchAtStartup.distinctUntilChanged().collect { enabled ->
            runCatching { if (enabled) autoLaunch.enable() else autoLaunch.disable() }
                .onFailure { io.github.aakira.napier.Napier.w("AutoLaunch sync failed: ${it.message}") }
        }
    }

    // Game overlay: keep the global-hotkey manager in sync with the user's configured combo.
    val hotkeyManager: GlobalHotkeyManager = koinInject()
    val overlayEnabled by remember(userPreferences) { userPreferences.overlayHotkeyEnabled }.collectAsState(true)
    val overlayCode by remember(userPreferences) { userPreferences.overlayHotkeyCode }.collectAsState(0)
    val overlayMods by remember(userPreferences) { userPreferences.overlayHotkeyMods }.collectAsState(0)
    LaunchedEffect(overlayEnabled, overlayCode, overlayMods) {
        hotkeyManager.setEnabled(overlayEnabled)
        hotkeyManager.updateCombo(HotkeyCombo.fromPrefs(overlayCode, overlayMods))
    }
    val overlayVisible by OverlayController.visible.collectAsState()
    val overlayPosX by remember(userPreferences) { userPreferences.overlayPosX }.collectAsState(com.example.melodist.data.repository.OVERLAY_POS_UNSET)
    val overlayPosY by remember(userPreferences) { userPreferences.overlayPosY }.collectAsState(com.example.melodist.data.repository.OVERLAY_POS_UNSET)

    // Quick sync menu in the title bar — same toggle/action as Settings, just closer at hand.
    val syncUtils: com.example.melodist.utils.SyncUtils = koinInject()
    val ytmSyncEnabled by remember(userPreferences) { userPreferences.ytmSyncEnabled }.collectAsState(false)
    val syncState by syncUtils.syncState.collectAsState()
    var showYtmSyncWarningFromMenu by remember { mutableStateOf(false) }

    // The menu's entry point shows the signed-in account's YouTube avatar (and name/email as a
    // header inside the menu) instead of a generic icon. Fetched once per login-state transition —
    // this is a single lightweight call, not a sync operation, so it doesn't need the cooldown.
    val isLoggedIn by remember { com.example.melodist.data.account.AccountManager.loginState }.collectAsState(false)
    var accountInfo by remember { mutableStateOf<com.metrolist.innertube.models.AccountInfo?>(null) }
    LaunchedEffect(isLoggedIn) {
        accountInfo = if (isLoggedIn) com.metrolist.innertube.YouTube.accountInfo().getOrNull() else null
    }

    fun handleExit() {
        scope.launch {
            // Single atomic write (was two sequential DataStore edits) — halves the disk
            // round-trip that used to run synchronously right before exit.
            userPreferences.setWindowState(
                maximized = windowState.placement == WindowPlacement.Maximized,
                width = windowState.size.width.value.toInt(),
                height = windowState.size.height.value.toInt(),
            )
            onExit()
        }
    }

    if (!isVisible || minimizeToTray) {
        val trayState by playerViewModel.uiState.collectAsState()
        val isPlaying = trayState.playbackState == PlaybackState.PLAYING
        TrayCustom(
            trayState = trayState,
            isPlaying = isPlaying,
            playerViewModel = playerViewModel,
            onToggleVisibility = { isVisible = !isVisible },
            onShow = { isVisible = true },
            handleExit = ::handleExit
        )
    }

    val currentSongFlow = remember(playerViewModel) {
        playerViewModel.uiState.map { it.currentSong }.distinctUntilChanged()
    }
    val playbackStateFlow = remember(playerViewModel) {
        playerViewModel.uiState.map { it.playbackState }.distinctUntilChanged()
    }

    val appLocale by remember(userPreferences) { userPreferences.locale }.collectAsState(AppLocale.SYSTEM)
    LaunchedEffect(appLocale) {
        val newLocale = appLocale.tag?.let { Locale.forLanguageTag(it) }
        if (newLocale != null) Locale.setDefault(newLocale)
    }

    val currentSong by currentSongFlow.collectAsState(initial = playerViewModel.uiState.value.currentSong)
    val playbackState by playbackStateFlow.collectAsState(initial = playerViewModel.uiState.value.playbackState)
    val artworkColors = rememberArtworkColors(currentSong?.thumbnailUrl)
    val themeMode by remember(userPreferences) { userPreferences.themeMode }.collectAsState(ThemeMode.SYSTEM)
    val youtubeRegion by remember(userPreferences) { userPreferences.youtubeRegion }.collectAsState(YouTubeRegion.SYSTEM)

    LaunchedEffect(youtubeRegion) {
        if (youtubeRegion == YouTubeRegion.SYSTEM) {
            val sysLocale = Locale.getDefault()
            val rawCountry = sysLocale.country
            val rawLang = sysLocale.toLanguageTag()
            val safeGl = if (rawCountry.matches(Regex("^[a-zA-Z]{2}$"))) rawCountry.uppercase() else "US"
            val safeHl = if (rawLang.matches(Regex("^[a-zA-Z]{2}(-[a-zA-Z]{2})?$"))) rawLang else "en-US"
            YouTube.locale = YouTubeLocale(safeGl, safeHl)
        } else {
            YouTube.locale = YouTubeLocale(youtubeRegion.gl, youtubeRegion.hl)
        }
    }

    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    MelodistTheme(artworkColors = artworkColors, userPreferences = userPreferences) {
        val surfaceColor = MaterialTheme.colorScheme.background

        val titleBarStyle = if (isDark) {
            TitleBarStyle.dark(
                colors = TitleBarColors.dark(
                    backgroundColor = surfaceColor,
                    inactiveBackground = surfaceColor,
                    borderColor = surfaceColor,
                )
            )
        } else {
            TitleBarStyle.light(
                colors = TitleBarColors.light(
                    backgroundColor = surfaceColor,
                    inactiveBackground = surfaceColor,
                    borderColor = surfaceColor,
                )
            )
        }

        IntUiTheme(
            theme = if (isDark) JewelTheme.darkThemeDefinition() else JewelTheme.lightThemeDefinition(),
            styling = ComponentStyling.decoratedWindow(titleBarStyle = titleBarStyle),
        ) {
            val playlistsViewModel: LibraryPlaylistsViewModel = koinInject()
            CompositionLocalProvider(
                LocalArtworkColors provides artworkColors,
                LocalSnackbarHostState provides snackbarHostState,
                LocalSnackbarScope provides scope,
                LocalPlayerViewModel provides playerViewModel,
                LocalDownloadViewModel provides downloadViewModel,
                LocalPlaylistsViewModel provides playlistsViewModel,
                LocalUserPreferences provides userPreferences,
            ) {
                DecoratedWindow(
                    onCloseRequest = { if (minimizeToTray) isVisible = false else handleExit() },
                    state = windowState,
                    visible = isVisible,
                    title = stringResource(Res.string.app_name),
                    icon = painterResource(Res.drawable.Music_note_circle),
                ) {
                    val updateDownload by appViewModel.downloadState.collectAsState()
                    updateInfo?.let { info ->
                        val busy = updateDownload is UpdateDownloadState.Downloading ||
                            updateDownload is UpdateDownloadState.Launching
                        AlertDialog(
                            onDismissRequest = { appViewModel.dismissUpdate() },
                            title = { Text(stringResource(Res.string.update_title)) },
                            text = {
                                Column {
                                    Text(stringResource(Res.string.update_message, info.currentVersion, info.latestVersion))
                                    when (val ds = updateDownload) {
                                        is UpdateDownloadState.Downloading -> {
                                            Spacer(Modifier.height(12.dp))
                                            if (ds.progress >= 0f) {
                                                LinearProgressIndicator(progress = { ds.progress }, modifier = Modifier.fillMaxWidth())
                                                Text("${stringResource(Res.string.update_downloading)} ${(ds.progress * 100).toInt()}%")
                                            } else {
                                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                                Text(stringResource(Res.string.update_downloading))
                                            }
                                        }
                                        UpdateDownloadState.Launching -> {
                                            Spacer(Modifier.height(12.dp))
                                            Text(stringResource(Res.string.update_installing))
                                        }
                                        is UpdateDownloadState.Failed -> {
                                            Spacer(Modifier.height(12.dp))
                                            Text(stringResource(Res.string.update_failed))
                                        }
                                        else -> {}
                                    }
                                }
                            },
                            confirmButton = {
                                if (info.installerUrl != null) {
                                    // In-app: download the installer and launch it, then quit so it can update.
                                    TextButton(enabled = !busy, onClick = { appViewModel.downloadAndInstall { handleExit() } }) {
                                        Text(stringResource(Res.string.update_install))
                                    }
                                } else {
                                    // No installer asset published — fall back to opening the release page.
                                    TextButton(onClick = {
                                        appViewModel.dismissUpdate()
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                Desktop.getDesktop().browse(
                                                    java.net.URI(info.releaseUrl ?: "https://github.com/AndresTarma1/LyriK/releases/latest")
                                                )
                                            } catch (_: Exception) {}
                                        }
                                    }) {
                                        Text(stringResource(Res.string.update_download))
                                    }
                                }
                            },
                            dismissButton = {
                                // Dismissing is always safe: the download (if any) keeps running in
                                // the background and the installer is ready next time, no progress lost.
                                TextButton(onClick = { appViewModel.dismissUpdate() }) {
                                    Text(stringResource(Res.string.update_later))
                                }
                            }
                        )
                    }

                    if (showYtmSyncWarningFromMenu) {
                        AlertDialog(
                            onDismissRequest = { showYtmSyncWarningFromMenu = false },
                            title = { Text(stringResource(Res.string.ytm_sync_warning_title)) },
                            text = { Text(stringResource(Res.string.ytm_sync_warning_message)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    scope.launch { userPreferences.setYtmSyncEnabled(true) }
                                    showYtmSyncWarningFromMenu = false
                                }) { Text(stringResource(Res.string.ytm_sync_warning_confirm)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showYtmSyncWarningFromMenu = false }) {
                                    Text(stringResource(Res.string.cancel))
                                }
                            }
                        )
                    }

                    window.minimumSize = Dimension(1024, 600)

                    // Spotify-style taskbar thumbnail buttons (Prev / Play-Pause / Next) on Windows.
                    val isWindows = remember { System.getProperty("os.name").orEmpty().lowercase().contains("win") }
                    val thumbBar = remember {
                        com.example.melodist.windows.WindowsThumbBar(
                            onPrevious = { playerViewModel.previous() },
                            onPlayPause = { playerViewModel.togglePlayPause() },
                            onNext = { playerViewModel.next() },
                        )
                    }

                    DisposableEffect(Unit) {
                        val startMaximized = windowState.placement == WindowPlacement.Maximized
                        val listener = object : ComponentAdapter() {
                            override fun componentResized(e: ComponentEvent) {
                                window.removeComponentListener(this)
                                if (startMaximized) {
                                    window.extendedState = Frame.MAXIMIZED_BOTH
                                }
                                EventQueue.invokeLater {
                                    isVisible = true
                                    if (isWindows) runCatching { thumbBar.init(window) }
                                }
                            }
                        }
                        window.addComponentListener(listener)
                        onDispose { window.removeComponentListener(listener) }
                    }

                    // Keep the middle button's glyph in sync with playback state.
                    if (isWindows) {
                        LaunchedEffect(playbackState) {
                            thumbBar.setPlaying(playbackState == PlaybackState.PLAYING)
                        }
                    }

                    TitleBar{
                        MelodistTitleBar(
                            currentSong = currentSong?.title,
                            isPlaying = playbackState == PlaybackState.PLAYING,
                            accountInfo = accountInfo,
                            ytmSyncEnabled = ytmSyncEnabled,
                            isSyncing = syncState.overallStatus is com.example.melodist.utils.SyncStatus.Syncing,
                            onToggleSync = { enable ->
                                if (enable) showYtmSyncWarningFromMenu = true
                                else scope.launch { userPreferences.setYtmSyncEnabled(false) }
                            },
                            onSyncNow = {
                                scope.launch { userPreferences.setLastFullSyncAt(System.currentTimeMillis()) }
                                syncUtils.performFullSync()
                                if (ytmSyncEnabled) syncUtils.syncAutoSyncPlaylists()
                            },
                        )
                    }

                    key(appLocale) {
                        NavigationDesktop(rootComponent)
                    }
                }
            }
        }
    }

    // Independent always-on-top overlay window, toggled by the global hotkey. Lives outside the
    // main DecoratedWindow so it works even while the app is minimized to the tray.
    MusicOverlayWindow(
        visible = overlayVisible,
        onDismiss = { OverlayController.hide() },
        playerViewModel = playerViewModel,
        userPreferences = userPreferences,
        savedPosX = overlayPosX,
        savedPosY = overlayPosY,
    )
}
@Composable
private fun ApplicationScope.TrayCustom(
    trayState: PlayerUiState,
    isPlaying: Boolean,
    playerViewModel: PlayerViewModel,
    onToggleVisibility: () -> Unit,
    onShow: () -> Unit,
    handleExit: () -> Unit
) {
    val tooltipText = trayState.currentSong?.title ?: stringResource(Res.string.app_name)
    val pauseLabel = stringResource(Res.string.tray_pause)
    val playLabel = stringResource(Res.string.tray_play)
    val nextLabel = stringResource(Res.string.tray_next)
    val previousLabel = stringResource(Res.string.tray_previous)
    val openLabel = stringResource(Res.string.tray_open)
    val exitLabel = stringResource(Res.string.tray_exit)

    Tray(
        icon = Res.drawable.Music_note_circle,
        tooltip = tooltipText,
        primaryAction = { onToggleVisibility() },
    ) {
        Item(
            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            label = if (isPlaying) pauseLabel else playLabel,
            onClick = { playerViewModel.togglePlayPause() }
        )
        Item(
            icon = Icons.Filled.SkipNext,
            label = nextLabel,
            onClick = { playerViewModel.next() }
        )
        Item(
            icon = Icons.Filled.SkipPrevious,
            label = previousLabel,
            onClick = { playerViewModel.previous() }
        )
        Divider()
        Item(
            icon = Icons.Filled.OpenInFull,
            label = openLabel,
            onClick = { onShow() }
        )
        Divider()
        Item(
            icon = Icons.Filled.ClosedCaption,
            label = exitLabel,
            onClick = { handleExit() }
        )
    }
}

@Composable
private fun TitleBarScope.MelodistTitleBar(
    currentSong: String?,
    isPlaying: Boolean,
    accountInfo: com.metrolist.innertube.models.AccountInfo?,
    ytmSyncEnabled: Boolean,
    isSyncing: Boolean,
    onToggleSync: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
) {
    Row(
        modifier = Modifier.align(Alignment.Start).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(Res.string.app_name),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }

    // Quick access to YTM sync — the same toggle/action that lives in Settings, closer at hand.
    // Entry point is the signed-in account's YouTube avatar when available (falls back to a
    // generic cloud icon while logged out or before the avatar loads).
    Box(modifier = Modifier.align(Alignment.End).padding(end = 4.dp)) {
        var showMenu by remember { mutableStateOf(false) }
        IconButton(onClick = { showMenu = true }) {
            if (isSyncing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else if (accountInfo?.thumbnailUrl != null) {
                com.example.melodist.ui.components.MelodistImage(
                    url = accountInfo.thumbnailUrl,
                    contentDescription = accountInfo.name,
                    modifier = Modifier.size(24.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    placeholderType = com.example.melodist.ui.components.PlaceholderType.ARTIST,
                )
            } else {
                Icon(
                    if (ytmSyncEnabled) Icons.Rounded.CloudSync else Icons.Rounded.CloudOff,
                    contentDescription = stringResource(Res.string.ytm_sync),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
        ) {
            if (accountInfo != null) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    com.example.melodist.ui.components.MelodistImage(
                        url = accountInfo.thumbnailUrl,
                        contentDescription = accountInfo.name,
                        modifier = Modifier.size(36.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        placeholderType = com.example.melodist.ui.components.PlaceholderType.ARTIST,
                    )
                    Column {
                        Text(accountInfo.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        accountInfo.email?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.ytm_sync)) },
                trailingIcon = { Switch(checked = ytmSyncEnabled, onCheckedChange = null) },
                onClick = { onToggleSync(!ytmSyncEnabled) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            DropdownMenuItem(
                text = { Text(stringResource(if (isSyncing) Res.string.sync_now_syncing else Res.string.sync_now)) },
                leadingIcon = {
                    if (isSyncing) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Sync, contentDescription = null)
                },
                enabled = !isSyncing,
                onClick = { onSyncNow(); showMenu = false },
            )
        }
    }

    AnimatedContent(
        targetState = currentSong,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    ) { song ->
        if (song != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (isPlaying) {
                    AnimatedEqualizer(
                        isPlaying = true,
                        modifier = Modifier.size(width = 20.dp, height = 14.dp)
                    )
                }
                    Text(
                        text = song,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .widthIn(max = 450.dp)
                            .basicMarquee(
                                velocity = if(isPlaying) 25.dp else 0.dp,
                            ),
                    )
            }
        }
    }
}
