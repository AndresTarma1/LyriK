package com.example.melodist.lifecycle

import com.example.melodist.download.DownloadService
import com.example.melodist.overlay.GlobalHotkeyManager
import com.example.melodist.player.PlayerService
import com.example.melodist.player.WindowsMediaSession
import com.example.melodist.utils.SyncUtils

class AppLifecycleManager(
    private val mediaSession: WindowsMediaSession,
    private val playerService: PlayerService,
    private val downloadService: DownloadService,
    private val syncUtils: SyncUtils,
    private val hotkeyManager: GlobalHotkeyManager,
) {

    fun cleanUpAndExit() {
        // Must unregister the global low-level keyboard hook (WH_KEYBOARD_LL, via jnativehook)
        // BEFORE the process dies. If we don't, Windows keeps the dead hook in its system-wide
        // input chain until it notices the owning process is gone (LowLevelHooksTimeout, ~300ms
        // by default) — every mouse/keyboard event on the whole system stalls until then. That was
        // the cause of the "mouse freezes for a moment on exit" symptom.
        runCatching { hotkeyManager.stop() }
        runCatching { syncUtils.cancelAllSyncs() }
        runCatching { downloadService.release() }
        runCatching { mediaSession.release() }
        runCatching { playerService.release() }
        // Runtime.halt() skips JVM shutdown hooks (ours already ran above; any third-party
        // library's hook is skipped too). Safe here: our state is already saved and released;
        // anything a hook would "gracefully" free (temp files, GPU/native handles) is reclaimed
        // by the OS on process death anyway. exitProcess()/System.exit() would run those hooks
        // synchronously first, adding avoidable delay to close.
        Runtime.getRuntime().halt(0)
    }
}

