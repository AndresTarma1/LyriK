package com.example.melodist

import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.example.melodist.data.AppDirs
import com.example.melodist.data.account.AccountManager
import com.example.melodist.data.repository.JvmConfig
import com.example.melodist.data.repository.JvmConfigRepository
import com.example.melodist.data.repository.UserPreferencesRepository
import com.example.melodist.di.appModule
import com.example.melodist.di.dataStoreModule
import com.example.melodist.navigation.RootComponent
import com.example.melodist.player.DownloadService
import com.example.melodist.player.PlayerService
import com.example.melodist.player.WindowsMediaSession
import com.example.melodist.utils.AppRestarter
import com.example.melodist.utils.SyncUtils
import com.example.melodist.viewmodels.AppViewModel
import com.example.melodist.viewmodels.DownloadViewModel
import com.example.melodist.viewmodels.PlayerViewModel
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeLocale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import kotlin.system.exitProcess
import java.io.PrintStream
import java.io.FileOutputStream
import java.lang.management.ManagementFactory
import coil3.compose.setSingletonImageLoaderFactory
import com.example.melodist.ui.components.CoilSetup

fun main() {

    setupEnvironments()

    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        logStartupError("Uncaught exception on thread '${thread.name}'", throwable)
    }

    val koinApp = try {
        startKoin { modules(appModule, dataStoreModule) }
    } catch (e: Throwable) {
        logStartupError("Error al iniciar Koin", e)
        throw e
    }

    val jvmConfigRepository = try {
        koinApp.koin.get<JvmConfigRepository>()
    } catch (e: Throwable) {
        logStartupError("Error creando JvmConfigRepository", e)
        throw e
    }

    runBlocking {
        val persistedConfig = jvmConfigRepository.config.first()
        if (shouldRestartWithPersistedJvmArgs(persistedConfig)) {
            AppRestarter.restartWithJvmArgs(persistedConfig)
            return@runBlocking
        }
    }

    // ✅ PlayerService se inicializa perezosamente — solo cuando se necesita
    val playerViewModel = try {
        koinApp.koin.get<PlayerViewModel>().also {
            // Inicializa MPV y el tick loop de forma diferida
            it.initialize()
        }
    } catch (e: Throwable) {
        logStartupError("Error creando PlayerViewModel", e)
        throw e
    }
    val downloadViewModel = try {
        koinApp.koin.get<DownloadViewModel>()
    } catch (e: Throwable) {
        logStartupError("Error creando DownloadViewModel", e)
        throw e
    }
    val appViewModel = try {
        koinApp.koin.get<AppViewModel>()
    } catch (e: Throwable) {
        logStartupError("Error creando AppViewModel", e)
        throw e
    }

    // ✅ WindowsMediaSession se inicializa solo si hay preferencia de media keys
    koinApp.koin.get<WindowsMediaSession>().apply {
        initialize()
        setCallbacks(
            onPlay = { playerViewModel.togglePlayPause() },
            onPause = { playerViewModel.togglePlayPause() },
            onNext = { playerViewModel.next() },
            onPrevious = { playerViewModel.previous() },
            onStop = { playerViewModel.stop() },
        )
        setPositionProvider { playerViewModel.progressState.value.positionMs }
    }

    val userPreferencesRepository = try {
        koinApp.koin.get<UserPreferencesRepository>()
    } catch (e: Throwable) {
        logStartupError("Error creando UserPreferencesRepository", e)
        throw e
    }

    val initialWidth: Int
    val initialHeight: Int
    val initialMaximized: Boolean

    runBlocking {
        initialWidth = userPreferencesRepository.windowWidth.first()
        initialHeight = userPreferencesRepository.windowHeight.first()
        initialMaximized = userPreferencesRepository.windowMaximized.first()
    }



    application {
        setSingletonImageLoaderFactory { context ->
            CoilSetup.createImageLoader(context)
        }
        System.setProperty("compose.swing.render.on.graphics", "true")
        val windowState = rememberWindowState(
            placement = if (initialMaximized) WindowPlacement.Maximized else WindowPlacement.Floating,
            width = initialWidth.dp,
            height = initialHeight.dp,
            position = WindowPosition(Alignment.Center),
        )


        val lifecycle = remember { LifecycleRegistry() }
        val rootComponent = remember {
            RootComponent(
                componentContext = DefaultComponentContext(lifecycle)
            )
        }

        fun doExit() {
            koinApp.koin.get<WindowsMediaSession>().release()
            runCatching { koinApp.koin.get<PlayerService>().release() }
            runCatching { koinApp.koin.get<DownloadService>().release() }
            runCatching { koinApp.koin.get<SyncUtils>().cancelAllSyncs() }
            stopKoin()
            exitProcess(0)
        }

        App(
            rootComponent = rootComponent,
            appViewModel = appViewModel,
            playerViewModel = playerViewModel,
            downloadViewModel = downloadViewModel,
            userPreferences = userPreferencesRepository,
            windowState = windowState,
            onExit = ::doExit
        )
    }
}

private fun setupEnvironments() {
    AppDirs.ensureDirectories()
    val tmpDir = AppDirs.tmpDir.also { it.mkdirs() }

    System.setProperty("org.sqlite.tmpdir", tmpDir.absolutePath)
    System.setProperty("java.io.tmpdir", tmpDir.absolutePath)

    // Redirect stdout and stderr to log files
    try {
        val sysOutFile = File(AppDirs.logsDir, "sysout.log")
        val sysErrFile = File(AppDirs.logsDir, "syserr.log")
        System.setOut(PrintStream(FileOutputStream(sysOutFile, true), true))
        System.setErr(PrintStream(FileOutputStream(sysErrFile, true), true))
        System.out.println("[${LocalDateTime.now()}] --- Application Starting ---")
        System.err.println("[${LocalDateTime.now()}] --- Application Starting ---")
    } catch (e: Exception) {
        // Can't log here initially if permissions fail, but try to log to error file anyway
    }

    AccountManager.init()

    val sysLocale = java.util.Locale.getDefault()
    val rawCountry = sysLocale.country
    val rawLang = sysLocale.toLanguageTag()
    val safeGl = if (rawCountry.matches(Regex("^[a-zA-Z]{2}$"))) rawCountry.uppercase() else "US"
    val safeHl = if (rawLang.matches(Regex("^[a-zA-Z]{2}(-[a-zA-Z]{2})?$"))) rawLang else "en-US"
    YouTube.locale = YouTubeLocale(safeGl, safeHl)

}

private fun logStartupError(context: String, throwable: Throwable) {
    runCatching {
        val logsDir = File(AppDirs.dataRoot, "logs")
        if (!logsDir.exists()) logsDir.mkdirs()

        val logFile = File(logsDir, "startup.log")
        val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val entry = buildString {
            appendLine("[${LocalDateTime.now()}] $context")
            appendLine(stackTrace)
            appendLine("------------------------------------------------------------")
        }
        logFile.appendText(entry)
    }
}

private const val jvmConfigAppliedProperty = "melodist.jvmConfigApplied"
private const val jvmConfigAppliedArg = "-D${jvmConfigAppliedProperty}=true"

private fun shouldRestartWithPersistedJvmArgs(config: JvmConfig): Boolean {
    if (System.getProperty(jvmConfigAppliedProperty) == "true") return false

    val desiredArgs = AppRestarter.previewJvmArgs(config).filterNot { it == jvmConfigAppliedArg }
    val runtimeArgs = ManagementFactory.getRuntimeMXBean().inputArguments
    val normalizedRuntimeArgs = runtimeArgs.map { it.trim() }

    return desiredArgs.any { desired ->
        val match = if (desired.startsWith("-Xmx", ignoreCase = true) || desired.startsWith("-Xms", ignoreCase = true)) {
            normalizedRuntimeArgs.any { it.equals(desired, ignoreCase = true) }
        } else {
            normalizedRuntimeArgs.contains(desired)
        }
        !match
    }
}
