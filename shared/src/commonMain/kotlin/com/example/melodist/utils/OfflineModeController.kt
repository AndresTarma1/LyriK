package com.example.melodist.utils

import com.example.melodist.data.repository.UserPreferencesRepository
import com.metrolist.innertube.OfflineGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Mantiene [OfflineGate] — el único punto de control que verifica cada llamada de red de YouTube —
 * sincronizado tanto con el interruptor manual de offline del usuario como con la conectividad real.
 * Debe crearse una vez de forma eagerly al inicio de la aplicación (es un singleton de Koin, resuelto
 * explícitamente en main()) para que el control refleje la realidad antes de que se envíe la primera
 * solicitud, no solo después de que algo suceda para acceder a él.
 */
class OfflineModeController(preferencesRepository: UserPreferencesRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var manualOffline = false

    init {
        // Reacciona inmediatamente al interruptor manual...
        preferencesRepository.offlineModeEnabled
            .onEach {
                manualOffline = it
                refresh()
            }
            .launchIn(scope)

        // ...y consulta la conectividad real, ya que no tenemos un evento de cambio de red del SO basado en push.
        scope.launch {
            while (true) {
                refresh()
                delay(10_000)
            }
        }
    }

    private suspend fun refresh() {
        OfflineGate.isOffline = manualOffline || !NetworkMonitor.isOnline()
    }
}
