package com.example.melodist.utils

import kotlinx.coroutines.delay

/**
 * Fast connectivity probe used to tell "no internet" apart from a per-song/per-request failure.
 * See the JVM actual for the concrete check.
 */
expect object NetworkMonitor {
    suspend fun isOnline(timeoutMs: Int = 1500): Boolean
}

/**
 * Suspends, polling at [pollIntervalMs], until [NetworkMonitor] reports we're back online. Used to
 * auto-resume/retry whatever was paused for lack of connectivity — cancel the coroutine calling
 * this to stop watching (e.g. the user manually retried, or picked a different song).
 */
suspend fun NetworkMonitor.awaitOnline(pollIntervalMs: Long = 5000) {
    while (!isOnline()) {
        delay(pollIntervalMs)
    }
}
