package com.example.melodist

import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import coil3.compose.setSingletonImageLoaderFactory
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.example.melodist.data.account.AccountManager
import com.example.melodist.bootstrap.AppEnvironment
import com.example.melodist.bootstrap.JvmConfigLauncher
import com.example.melodist.bootstrap.PlatformCrashHandler
import com.example.melodist.data.repository.UserPreferencesRepository
import com.example.melodist.di.appModule
import com.example.melodist.di.dataStoreModule
import com.example.melodist.lifecycle.AppLifecycleManager
import com.example.melodist.navigation.RootComponent
import com.example.melodist.player.WindowsMediaSession
import com.example.melodist.ui.components.CoilSetup
import com.example.melodist.viewmodels.AppViewModel
import com.example.melodist.viewmodels.DownloadViewModel
import com.example.melodist.viewmodels.PlayerViewModel
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import org.koin.core.context.startKoin

fun main() {
    AppEnvironment.initialize()
    PlatformCrashHandler.register()

    val koinApp = PlatformCrashHandler.runSafely("Error al iniciar Koin") {
        startKoin { modules(appModule, dataStoreModule) }
    }
    val koin = koinApp.koin

    PlatformCrashHandler.runSafely("Error iniciando AccountManager") {
        val dataStore = koin.get<DataStore<Preferences>>()
        AccountManager.init(dataStore)
    }


    koin.get<JvmConfigLauncher>().applySync()

    // Eagerly resolve so the network kill-switch reflects reality before the first request goes
    // out — Koin singles are lazy, and nothing else depends on this one to trigger its creation.
    koin.get<com.example.melodist.utils.OfflineModeController>()

    // Construct the VM now (the UI binds to it), but defer its native init (mpv) — see the thread below.
    val playerViewModel = PlatformCrashHandler.runSafely("Error creando PlayerViewModel") {
        koin.get<PlayerViewModel>()
    }
    val downloadViewModel = PlatformCrashHandler.runSafely("Error creando DownloadViewModel") {
        koin.get<DownloadViewModel>()
    }
    val appViewModel = PlatformCrashHandler.runSafely("Error creando AppViewModel") {
        koin.get<AppViewModel>()
    }
    val userPreferencesRepository = PlatformCrashHandler.runSafely("Error creando UserPreferencesRepository") {
        koin.get<UserPreferencesRepository>()
    }
    val lifecycleManager = koin.get<AppLifecycleManager>()

    // Defer non-UI native services off the startup critical path. None of these are needed to paint
    // the window — initializing them here delayed first paint by the cost of mpv, the SMTC media
    // session, the jnativehook global hook and the Listen Together setup. They come online a beat
    // after the UI on a background thread instead.
    Thread {
        // mpv (native player) first: play() also self-inits as a fallback if the user plays before
        // this runs (init() is idempotent), and any EQ/gapless requested meanwhile is cached and
        // applied on init — so deferring it loses nothing.
        PlatformCrashHandler.runSafely("Error inicializando reproductor (mpv)") {
            playerViewModel.initialize()
        }

        // SMTC (dev.toastbits:mediasession) has no persistent thread of its own — unlike
        // jnativehook, which spawns its own native hook thread. Its transport controls need the
        // creating thread to stay alive pumping a Windows message loop. Our previous throwaway
        // "lyrik-deferred-init" thread died right after registering it, silently orphaning the
        // session (Windows still showed it, but Play/Pause/Next stopped reaching our callbacks).
        // The AWT Event Dispatch Thread lives for the app's whole lifetime and already pumps
        // native messages, so initialize there instead.
        java.awt.EventQueue.invokeLater {
            PlatformCrashHandler.runSafely("Error iniciando WindowsMediaSession") {
                val mediaSession = koin.get<WindowsMediaSession>()
                mediaSession.initialize()
                mediaSession.setCallbacks(
                    onPlay = { playerViewModel.togglePlayPause() },
                    onPause = { playerViewModel.togglePlayPause() },
                    onNext = { playerViewModel.next() },
                    onPrevious = { playerViewModel.previous() },
                    onStop = { playerViewModel.stop() },
                )
                mediaSession.setPositionProvider { playerViewModel.progressState.value.positionMs }
            }
        }

        // Listen Together: wire the sync manager to the player.
        PlatformCrashHandler.runSafely("Error iniciando ListenTogetherManager") {
            val listenTogetherManager = koin.get<com.example.melodist.listentogether.ListenTogetherManager>()
            listenTogetherManager.initialize()
            listenTogetherManager.setPlayer(playerViewModel)
        }

        // Game overlay: start the global keyboard hook so the configured hotkey toggles the overlay
        // even while another app/game is focused.
        PlatformCrashHandler.runSafely("Error iniciando GlobalHotkeyManager") {
            koin.get<com.example.melodist.overlay.GlobalHotkeyManager>().start()
        }
    }.apply { name = "lyrik-deferred-init"; isDaemon = true }.start()

    // Read the saved window geometry BEFORE building the window so it opens in the right placement
    // from the first frame. Setting placement asynchronously (after composition) is what forced the
    // open-floating-then-maximize flicker and the componentResized workaround.
    val saved = runBlocking {
        Triple(
            userPreferencesRepository.windowWidth.first(),
            userPreferencesRepository.windowHeight.first(),
            userPreferencesRepository.windowMaximized.first(),
        )
    }

    application {
        setSingletonImageLoaderFactory { context -> CoilSetup.createImageLoader(context) }
        val windowState = rememberWindowState(
            width = saved.first.dp,
            height = saved.second.dp,
            position = WindowPosition(Alignment.Center),
            placement = if (saved.third) WindowPlacement.Maximized else WindowPlacement.Floating,
        )
        val lifecycle = remember { LifecycleRegistry() }
        val rootComponent = remember { RootComponent(DefaultComponentContext(lifecycle)) }

        App(
            rootComponent = rootComponent,
            appViewModel = appViewModel,
            playerViewModel = playerViewModel,
            downloadViewModel = downloadViewModel,
            userPreferences = userPreferencesRepository,
            windowState = windowState,
            onExit = { lifecycleManager.cleanUpAndExit() },
        )
    }
}

