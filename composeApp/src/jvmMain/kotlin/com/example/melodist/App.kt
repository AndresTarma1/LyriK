package com.example.melodist

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.example.melodist.data.account.AccountManager
import com.example.melodist.data.repository.AppLocale
import com.example.melodist.data.repository.OVERLAY_POS_UNSET
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
import com.example.melodist.ui.themes.AppTheme
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.utils.LocalPlaylistsViewModel
import com.example.melodist.utils.LocalSnackbarHostState
import com.example.melodist.utils.LocalSnackbarScope
import com.example.melodist.utils.LocalUserPreferences
import com.example.melodist.viewmodels.AppViewModel
import com.example.melodist.viewmodels.UpdateStatus
import com.example.melodist.viewmodels.DownloadViewModel
import com.example.melodist.viewmodels.LibraryPlaylistsViewModel
import com.example.melodist.viewmodels.PlayerViewModel
import org.koin.compose.koinInject
import com.example.melodist.overlay.GlobalHotkeyManager
import com.example.melodist.overlay.HotkeyCombo
import com.example.melodist.overlay.MusicOverlayWindow
import com.example.melodist.overlay.OverlayController
import com.example.melodist.windows.WindowsThumbBar
import com.metrolist.innertube.models.AccountInfo
import io.github.aakira.napier.Napier
import io.github.vinceglb.autolaunch.AutoLaunch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import lyrik.composeapp.generated.resources.Music_note_circle
import lyrik.composeapp.generated.resources.Res
import lyrik.composeapp.generated.resources.app_name
import lyrik.composeapp.generated.resources.cancel
import lyrik.composeapp.generated.resources.update_ready_title
import lyrik.composeapp.generated.resources.update_ready_message
import lyrik.composeapp.generated.resources.update_install_now
import lyrik.composeapp.generated.resources.update_install_later
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
import org.jetbrains.jewel.window.styling.TitleBarColors
import org.jetbrains.jewel.window.styling.TitleBarStyle
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Frame
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import kotlin.time.Duration.Companion.milliseconds

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
    val updateStatus by appViewModel.updateStatus.collectAsState()
    val showInstallPrompt by appViewModel.showInstallPrompt.collectAsState()

    LaunchedEffect(Unit) {
        appViewModel.checkForUpdates()
    }

    var isVisible by remember { mutableStateOf(false) }
    val minimizeToTray by remember { userPreferences.minimizeToTray }.collectAsState(false)

    // Cuando la ventana se oculta, si el usuario tiene activada la opción de liberar memoria
    // se llama a la función trim() después de un retraso de 2 segundos.
    val trimMemoryOnTray by remember(userPreferences) { userPreferences.trimMemoryOnTray }.collectAsState(true)
    LaunchedEffect(isVisible, trimMemoryOnTray) {
        if (!isVisible && trimMemoryOnTray) {
            kotlinx.coroutines.delay(2000.milliseconds)
            com.example.melodist.utils.WorkingSetTrimmer.trim()
        }
    }

    // Lanzar la app al iniciar, solo si está activado
    LaunchedEffect(Unit) {
        val autoLaunch = AutoLaunch(appPackageName = "LyriK")
        userPreferences.launchAtStartup.distinctUntilChanged().collect { enabled ->
            runCatching { if (enabled) autoLaunch.enable() else autoLaunch.disable() }
                .onFailure { Napier.w("AutoLaunch sync failed: ${it.message}") }
        }
    }

    // Mantener el game overlay disponible y actualizado con la combinación de teclas y estado de activación del usuario.
    val hotkeyManager: GlobalHotkeyManager = koinInject()
    val overlayEnabled by remember(userPreferences) { userPreferences.overlayHotkeyEnabled }.collectAsState(true)
    val overlayCode by remember(userPreferences) { userPreferences.overlayHotkeyCode }.collectAsState(0)
    val overlayMods by remember(userPreferences) { userPreferences.overlayHotkeyMods }.collectAsState(0)

    LaunchedEffect(overlayEnabled, overlayCode, overlayMods) {
        hotkeyManager.setEnabled(overlayEnabled)
        hotkeyManager.updateCombo(HotkeyCombo.fromPrefs(overlayCode, overlayMods))
    }
    val overlayVisible by OverlayController.visible.collectAsState()
    val overlayPosX by remember(userPreferences) { userPreferences.overlayPosX }.collectAsState(OVERLAY_POS_UNSET)
    val overlayPosY by remember(userPreferences) { userPreferences.overlayPosY }.collectAsState(OVERLAY_POS_UNSET)

    // Mantener el estado de sincronización de YTM en la barra de título y en el menú de la bandeja.
    val syncUtils: com.example.melodist.utils.SyncUtils = koinInject()
    val ytmSyncEnabled by remember(userPreferences) { userPreferences.ytmSyncEnabled }.collectAsState(false)
    val offlineMode by remember(userPreferences) { userPreferences.offlineModeEnabled }.collectAsState(false)
    val syncState by syncUtils.syncState.collectAsState()
    var showYtmSyncWarningFromMenu by remember { mutableStateOf(false) }

    val isLoggedIn by remember { AccountManager.loginState }.collectAsState(false)
    val accountInfo by produceState<AccountInfo?>(initialValue = null, isLoggedIn) {
        value = if (isLoggedIn) YouTube.accountInfo().getOrNull() else null
    }

    fun handleExit() {
        scope.launch {
            // Guardamos lo esencial del estado de la ventana antes de salir, para restaurarlo en el próximo inicio.
            userPreferences.setWindowState(
                maximized = windowState.placement == WindowPlacement.Maximized,
                width = windowState.size.width.value.toInt(),
                height = windowState.size.height.value.toInt(),
            )
            onExit()
        }
    }

    val playerUiState by playerViewModel.uiState.collectAsState()
    val isPlaying = playerUiState.playbackState == PlaybackState.PLAYING
    val currentSong = playerUiState.currentSong

    val appLocale by remember(userPreferences) { userPreferences.locale }.collectAsState(AppLocale.SYSTEM)
    LaunchedEffect(appLocale) {
        val newLocale = appLocale.tag?.let { Locale.forLanguageTag(it) }
        if (newLocale != null) Locale.setDefault(newLocale)
    }

    val artworkColors = rememberArtworkColors(currentSong?.thumbnailUrl)
    val themeMode by remember(userPreferences) { userPreferences.themeMode }.collectAsState(ThemeMode.SYSTEM)
    val youtubeRegion by remember(userPreferences) { userPreferences.youtubeRegion }.collectAsState(YouTubeRegion.SYSTEM)

    if (!isVisible || minimizeToTray) {
        TrayCustom(
            playerViewModel = playerViewModel,
            onToggleVisibility = { isVisible = !isVisible },
            onShow = { isVisible = true },
            handleExit = ::handleExit
        )
    }
    // Validamos que la region o el código de region sea válido, si no lo es, se asigna un valor por defecto (US/en-US)
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

    AppTheme(artworkColors = artworkColors, userPreferences = userPreferences) {
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

                    windowBackgroundFlashingWorkaround(MaterialTheme.colorScheme.surface)
                    // La descarga corre silenciosamente, con la opción de instalar después o enseguida.
                    // Si el usuario elige instalar enseguida, se llama a handleExit() para cerrar la app y que el instalador pueda reemplazar los archivos.
                    val readyStatus = updateStatus as? UpdateStatus.Ready
                    if (showInstallPrompt && readyStatus != null) {
                        val info = readyStatus.info
                        AlertDialog(
                            onDismissRequest = { appViewModel.postponeInstall() },
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = 0.dp,
                            icon = { Icon(Icons.Rounded.SystemUpdate, null) },
                            title = { Text(stringResource(Res.string.update_ready_title)) },
                            text = { Text(stringResource(Res.string.update_ready_message, info.latestVersion)) },
                            confirmButton = {
                                TextButton(onClick = { appViewModel.installUpdate { handleExit() } }) {
                                    Text(stringResource(Res.string.update_install_now))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { appViewModel.postponeInstall() }) {
                                    Text(stringResource(Res.string.update_install_later))
                                }
                            }
                        )
                    }

                    if (showYtmSyncWarningFromMenu) {
                        AlertDialog(
                            onDismissRequest = { showYtmSyncWarningFromMenu = false },
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = 0.dp,
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

                    // Spotify-style barra de tareas botones (Prev / Play-Pause / Next) para Windows.
                    val isWindows = remember { System.getProperty("os.name").orEmpty().lowercase().contains("win") }
                    val thumbBar = remember {
                        WindowsThumbBar(
                            onPrevious = { playerViewModel.previous() },
                            onPlayPause = { playerViewModel.togglePlayPause() },
                            onNext = { playerViewModel.next() },
                        )
                    }

                    DisposableEffect(Unit) {
                        val startMaximized = windowState.placement == WindowPlacement.Maximized
                        if (isWindows) {
                            val listener = object : ComponentAdapter() {
                                override fun componentResized(e: ComponentEvent) {
                                    window.removeComponentListener(this)
                                    if (startMaximized) {
                                        window.extendedState = Frame.MAXIMIZED_BOTH
                                    }
                                    EventQueue.invokeLater {
                                        isVisible = true
                                        runCatching { thumbBar.init(window) }
                                    }
                                }
                            }
                            window.addComponentListener(listener)
                            onDispose { window.removeComponentListener(listener) }
                        } else {
                            if (startMaximized) {
                                window.extendedState = Frame.MAXIMIZED_BOTH
                            }
                            isVisible = true
                            onDispose {}
                        }
                    }

                    // sincronizar el estado de reproducción con la barra de tareas de Windows (Play/Pause)
                    if (isWindows) {
                        LaunchedEffect(isPlaying) {
                            thumbBar.setPlaying(isPlaying)
                        }
                    }

                    TitleBar{
                        DesktopTitleBar(
                            currentSong = currentSong?.title,
                            isPlaying = isPlaying,
                            accountInfo = accountInfo,
                            ytmSyncEnabled = ytmSyncEnabled,
                            isSyncing = syncState.overallStatus is com.example.melodist.utils.SyncStatus.Syncing,
                            isOfflineMode = offlineMode,
                            onToggleSync = { enable ->
                                if (enable) showYtmSyncWarningFromMenu = true
                                else scope.launch { userPreferences.setYtmSyncEnabled(false) }
                            },
                            onToggleOfflineMode = { enable ->
                                scope.launch { userPreferences.setOfflineModeEnabled(enable) }
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

    // Independiente de la ventana principal, el overlay de música puede mostrarse en cualquier momento si el usuario lo ha activado.
    MusicOverlayWindow(
        visible = overlayVisible,
        onDismiss = { OverlayController.hide() },
        playerViewModel = playerViewModel,
        userPreferences = userPreferences,
        savedPosX = overlayPosX,
        savedPosY = overlayPosY,
    )
}