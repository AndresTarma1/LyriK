package com.example.melodist

import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
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

    val playerViewModel = PlatformCrashHandler.runSafely("Error creando PlayerViewModel") {
        koin.get<PlayerViewModel>().also { it.initialize() }
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

    application {
        setSingletonImageLoaderFactory { context -> CoilSetup.createImageLoader(context) }
        val windowState = rememberWindowState(
            width = 1200.dp,
            height = 800.dp,
            position = WindowPosition(Alignment.Center),
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

