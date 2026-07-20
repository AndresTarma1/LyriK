package com.example.musicApp.lifecycle

import com.example.musicApp.download.DownloadService
import com.example.musicApp.overlay.GlobalHotkeyManager
import com.example.musicApp.player.PlayerService
import com.example.musicApp.player.WindowsMediaSession
import com.example.musicApp.utils.SyncUtils

class AppLifecycleManager(
    private val mediaSession: WindowsMediaSession,
    private val playerService: PlayerService,
    private val downloadService: DownloadService,
    private val syncUtils: SyncUtils,
    private val hotkeyManager: GlobalHotkeyManager,
) {

    fun cleanUpAndExit() {
        // Se debe desregistrar el hook global de teclado de bajo nivel (WH_KEYBOARD_LL, via jnativehook)
        // ANTES de que el proceso muera. Si no lo hacemos, Windows mantiene el hook muerto en su cadena
        // de entrada de todo el sistema hasta que detecta que el proceso propietario desapareció
        // (LowLevelHooksTimeout, ~300ms por defecto) — cada evento de ratón/teclado en todo el sistema
        // se bloquea hasta entonces. Esa era la causa del síntoma de "el ratón se congela un momento al salir".
        runCatching { hotkeyManager.stop() }
        runCatching { syncUtils.cancelAllSyncs() }
        runCatching { downloadService.release() }
        runCatching { mediaSession.release() }
        runCatching { playerService.release() }
        // Runtime.halt() omite los shutdown hooks del JVM (los nuestros ya se ejecutaron arriba;
        // cualquier hook de biblioteca de terceros también se omite). Aquí es seguro: nuestro estado
        // ya fue guardado y liberado; cualquier cosa que un hook "libere elegantemente" (archivos
        // temporales, manejadores GPU/nativos) es reclamada por el SO al morir el proceso de todas formas.
        // exitProcess()/System.exit() ejecutaría esos hooks sincronísticamente primero, añadiendo
        // un retraso evitable al cierre.
        Runtime.getRuntime().halt(0)
    }
}

