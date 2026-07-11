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
 * Keeps [OfflineGate] — the single choke point every YouTube network call checks — in sync with
 * both the user's manual offline toggle and actual connectivity. Must be eagerly created once at
 * app startup (it's a Koin single, resolved explicitly in main()) so the gate reflects reality
 * before the first request goes out, not just after something happens to touch it.
 */
class OfflineModeController(preferencesRepository: UserPreferencesRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var manualOffline = false

    init {
        // Reacts immediately to the manual toggle...
        preferencesRepository.offlineModeEnabled
            .onEach {
                manualOffline = it
                refresh()
            }
            .launchIn(scope)

        // ...and polls real connectivity, since we have no push-based OS network-change event.
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
