package com.example.melodist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.example.melodist.data.repository.ThemeMode
import com.example.melodist.data.repository.UserPreferencesRepository
import com.example.melodist.navigation.NavigationDesktop
import com.example.melodist.navigation.RootComponent
import com.example.melodist.player.PlaybackState
import com.example.melodist.ui.components.artwork.LocalArtworkColors
import com.example.melodist.ui.components.artwork.rememberArtworkColors
import com.example.melodist.ui.components.skeletons.AnimatedEqualizer
import com.example.melodist.ui.themes.MelodistTheme
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.utils.LocalSnackbarHostState
import com.example.melodist.utils.LocalUserPreferences
import com.example.melodist.viewmodels.AppViewModel
import com.example.melodist.viewmodels.DownloadViewModel
import com.example.melodist.viewmodels.LibraryViewModel
import com.example.melodist.viewmodels.PlayerUiState
import com.example.melodist.viewmodels.PlayerViewModel
import com.kdroid.composetray.tray.api.Tray
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import melodist.composeapp.generated.resources.Res
import melodist.composeapp.generated.resources.music
import org.jetbrains.compose.resources.painterResource
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

    LaunchedEffect(Unit) {
        appViewModel.checkForUpdates()
    }

    var isVisible by remember { mutableStateOf(false) }
    val minimizeToTray by remember { userPreferences.minimizeToTray }.collectAsState(false)

    fun handleExit() {
        scope.launch {
            if (windowState.placement == WindowPlacement.Maximized) {
                userPreferences.setWindowMaximized(true)
            } else {
                userPreferences.setWindowMaximized(false)
                userPreferences.setWindowSize(
                    windowState.size.width.value.toInt(),
                    windowState.size.height.value.toInt()
                )
            }
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

    val currentSong by currentSongFlow.collectAsState(initial = playerViewModel.uiState.value.currentSong)
    val playbackState by playbackStateFlow.collectAsState(initial = playerViewModel.uiState.value.playbackState)
    val artworkColors = rememberArtworkColors(currentSong?.thumbnailUrl)
    val themeMode by remember { userPreferences.themeMode }.collectAsState(ThemeMode.SYSTEM)
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    MelodistTheme(artworkColors = artworkColors, userPreferences = userPreferences) {
        val surfaceColor = MaterialTheme.colorScheme.surface

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
            theme = if (isDark) {
                JewelTheme.darkThemeDefinition()
            } else {
                JewelTheme.lightThemeDefinition()
            },
            styling = ComponentStyling.decoratedWindow(titleBarStyle = titleBarStyle),
        ) {
            CompositionLocalProvider(
                LocalArtworkColors provides artworkColors,
                LocalSnackbarHostState provides snackbarHostState,
                LocalPlayerViewModel provides playerViewModel,
                LocalDownloadViewModel provides downloadViewModel,
                LocalUserPreferences provides userPreferences,
            ) {
                DecoratedWindow(
                    onCloseRequest = { if (minimizeToTray) isVisible = false else handleExit() },
                    state = windowState,
                    visible = isVisible,
                    title = "Melodist",
                    icon = painterResource(Res.drawable.music),
                ) {
                    updateInfo?.let { info ->
                        AlertDialog(
                            onDismissRequest = { appViewModel.dismissUpdate() },
                            title = { Text("Actualización disponible") },
                            text = {
                                Text(
                                    "Hay una nueva versión de Melodist disponible.\n\n" +
                                            "Versión actual: ${info.currentVersion}\n" +
                                            "Nueva versión: ${info.latestVersion}\n\n" +
                                            "¿Deseas actualizar ahora?"
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    appViewModel.dismissUpdate()
                                    kotlinx.coroutines.runBlocking {
                                        try {
                                            java.awt.Desktop.getDesktop().browse(
                                                java.net.URI(info.downloadUrl ?: "https://github.com/AndresTarma1/Melodist/releases/latest")
                                            )
                                        } catch (_: Exception) {}
                                    }
                                }) {
                                    Text("Descargar")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { appViewModel.dismissUpdate() }) {
                                    Text("Más tarde")
                                }
                            }
                        )
                    }

                    window.minimumSize = Dimension(1024, 600)
                    DisposableEffect(Unit) {
                        val startMaximized = windowState.placement == WindowPlacement.Maximized
                        val listener = object : ComponentAdapter() {
                            override fun componentResized(e: ComponentEvent) {
                                window.removeComponentListener(this)
                                if (startMaximized) {
                                    window.extendedState = Frame.MAXIMIZED_BOTH
                                }
                                EventQueue.invokeLater {
                                    window.isVisible = true
                                    isVisible = true
                                }
                            }
                        }

                        window.addComponentListener(listener)
                        onDispose { window.removeComponentListener(listener) }
                    }

                    TitleBar {
                        MelodistTitleBar(
                            currentSong = currentSong?.title,
                            isPlaying = playbackState == PlaybackState.PLAYING,
                        )
                    }

                    NavigationDesktop(rootComponent)
                    SnackbarHost(hostState = snackbarHostState)
                }
            }
        }
    }
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
    Tray(
        icon = Res.drawable.music,
        tooltip = trayState.currentSong?.title ?: "Melodist",
        primaryAction = { onToggleVisibility() },
    ) {
        Item(
            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            label = if (isPlaying) "Pausar" else "Reproducir",
            onClick = { playerViewModel.togglePlayPause() }
        )
        Item(
            icon = Icons.Filled.SkipNext,
            label = "Siguiente",
            onClick = { playerViewModel.next() }
        )
        Item(
            icon = Icons.Filled.SkipPrevious,
            label = "Anterior",
            onClick = { playerViewModel.previous() }
        )
        Divider()
        Item(
            icon = Icons.Filled.OpenInFull,
            label = "Abrir Melodist",
            onClick = { onShow() }
        )
        Divider()
        Item(
            icon = Icons.Filled.ClosedCaption,
            label = "Salir",
            onClick = { handleExit() }
        )
    }
}

@Composable
private fun TitleBarScope.MelodistTitleBar(
    currentSong: String?,
    isPlaying: Boolean,
) {
    Row(
        modifier = Modifier.align(Alignment.Start).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Melodist",
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }

    AnimatedContent(
        targetState = currentSong,
        transitionSpec = {
            (fadeIn() + slideInVertically { it / 2 })
                .togetherWith(fadeOut() + slideOutVertically { -it / 2 })
        },
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
                } else {
                    Icon(
                        imageVector = Icons.Filled.Pause,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
                    Text(
                        text = song,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 300.dp),
                    )
            }
        }
    }
}
