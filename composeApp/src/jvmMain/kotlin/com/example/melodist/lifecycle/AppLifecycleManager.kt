package com.example.melodist.lifecycle

import com.example.melodist.player.DownloadService
import com.example.melodist.player.PlayerService
import com.example.melodist.player.WindowsMediaSession
import com.example.melodist.utils.SyncUtils
import kotlin.system.exitProcess
import org.koin.core.context.stopKoin

class AppLifecycleManager(
    private val mediaSession: WindowsMediaSession,
    private val playerService: PlayerService,
    private val downloadService: DownloadService,
    private val syncUtils: SyncUtils,
) {

    fun cleanUpAndExit() {
        mediaSession.release()
        runCatching { playerService.release() }
        runCatching { downloadService.release() }
        runCatching { syncUtils.cancelAllSyncs() }
        stopKoin()
        exitProcess(0)
    }
}

