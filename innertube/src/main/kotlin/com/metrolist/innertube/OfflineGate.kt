package com.metrolist.innertube

import io.ktor.client.plugins.api.createClientPlugin

/**
 * Global network kill-switch, checked by [InnerTube]'s HTTP client before every request. This is
 * the single choke point "offline mode" needs: every [YouTube] call goes through [InnerTube],
 * so flipping this one flag blocks the whole app's network traffic (search, browse, library sync,
 * likes, ...) without having to gate each call site individually.
 *
 * Set by `com.example.melodist.utils.OfflineModeController` in the app module, which keeps it in
 * sync with the user's manual offline toggle and actual connectivity.
 */
object OfflineGate {
    @Volatile
    var isOffline: Boolean = false
}

class OfflineException : IllegalStateException("Offline mode is active — no network requests are made")

/** Aborts the request before it hits the network when [OfflineGate.isOffline] is set. */
val OfflineGatePlugin = createClientPlugin("OfflineGate") {
    onRequest { _, _ ->
        if (OfflineGate.isOffline) throw OfflineException()
    }
}
