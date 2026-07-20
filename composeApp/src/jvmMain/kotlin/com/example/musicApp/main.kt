package com.example.musicApp

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
import com.example.musicApp.data.account.AccountManager
import com.example.musicApp.bootstrap.AppEnvironment
import com.example.musicApp.bootstrap.JvmConfigLauncher
import com.example.musicApp.bootstrap.PlatformCrashHandler
import com.example.musicApp.data.repository.UserPreferencesRepository
import com.example.musicApp.di.appModule
import com.example.musicApp.di.dataStoreModule
import com.example.musicApp.lifecycle.AppLifecycleManager
import com.example.musicApp.navigation.RootComponent
import com.example.musicApp.player.WindowsMediaSession
import com.example.musicApp.ui.components.CoilSetup
import com.example.musicApp.viewmodels.AppViewModel
import com.example.musicApp.viewmodels.DownloadViewModel
import com.example.musicApp.viewmodels.PlayerViewModel
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.example.musicApp.overlay.GlobalHotkeyManager
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

    // Facilitar la prueba de modo offline (sin Internet) para depuración y QA. Se puede forzar en el
    koin.get<com.example.musicApp.utils.OfflineModeController>()

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

    // Aplazar los servicios nativos que no son de la interfaz de usuario fuera de la ruta crítica de inicio. Ninguno de ellos es necesario para pintar
    // la ventana; inicializarlos aquí retrasa la primera pintura debido al costo de mpv, la sesión de medios SMTC,
    // el gancho global jnativehook y la configuración de Listen Together. Se activan un instante
    // después de la interfaz de usuario en un hilo en segundo plano.
    Thread {
        PlatformCrashHandler.runSafely("Error inicializando reproductor (mpv)") {
            playerViewModel.initialize()
        }

        // SMTC (dev.toastbits:mediasession) no tiene un hilo persistente propio, a diferencia de
        // jnativehook, que genera su propio hilo de enlace nativo. Sus controles de transporte necesitan que el
        // hilo creador se mantenga activo, procesando un bucle de mensajes de Windows. Nuestro anterior hilo desechable
        // "lyrik-deferred-init" murió justo después de registrarlo, dejando la
        // sesión huérfana silenciosamente (Windows aún la mostraba, pero Reproducir/Pausar/Siguiente dejó de llegar a nuestras devoluciones de llamada).
        // El hilo de despacho de eventos de AWT existe durante toda la vida útil de la aplicación y ya procesa
        // mensajes nativos, así que inicialícelo allí.
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

        // Escuchen juntos: conecte el administrador de sincronización a el reproductor.
        // Basado en (Metrolist)
        PlatformCrashHandler.runSafely("Error iniciando ListenTogetherManager") {
            val listenTogetherManager = koin.get<com.example.musicApp.listentogether.ListenTogetherManager>()
            listenTogetherManager.initialize()
            listenTogetherManager.setPlayer(playerViewModel)
        }

        // Superposición del juego: inicia el gancho de teclado global para que la tecla de acceso rápido configurada active o desactive la superposición.
        // Incluso cuando otra aplicación o juego esté en primer plano.
        PlatformCrashHandler.runSafely("Error iniciando GlobalHotkeyManager") {
            koin.get<GlobalHotkeyManager>().start()
        }
    }.apply { name = "lyrik-deferred-init"; isDaemon = true }.start()

    // Lee la config de tamaño/posición de ventana guardada y la aplica al estado inicial de la ventana.
    // Esto debe hacerse antes de crear la ventana para que se abra con el tamaño correcto.
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

