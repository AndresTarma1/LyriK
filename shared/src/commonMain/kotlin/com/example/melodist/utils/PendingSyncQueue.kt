package com.example.melodist.utils

import com.example.melodist.data.repository.UserPreferencesRepository
import com.metrolist.innertube.YouTube
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * A remote YouTube Music action that failed to push while offline (or with offline mode on) and
 * should be retried once connectivity is back. Local state (DB, UI) is always applied immediately
 * regardless of network — this only tracks the *remote* side of the action.
 *
 * To support a new action type: add a variant here, a branch in [PendingSyncQueue.flush], and
 * enqueue it wherever the live push currently just logs-and-drops on failure.
 */
@Serializable
sealed class PendingAction {
    @Serializable
    data class LikeSong(val songId: String, val liked: Boolean) : PendingAction()

    @Serializable
    data class SubscribeArtist(val channelId: String, val subscribed: Boolean) : PendingAction()
}

/**
 * Persisted queue of [PendingAction]s that couldn't be pushed to YouTube while offline. Retries
 * them automatically in the background whenever there's something pending and a connection is
 * available — callers just [enqueue] on failure, nothing else to wire up.
 */
class PendingSyncQueue(private val preferencesRepository: UserPreferencesRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            while (true) {
                if (preferencesRepository.pendingActions.first().isNotEmpty() && NetworkMonitor.isOnline()) {
                    flush()
                }
                delay(30_000)
            }
        }
    }

    suspend fun enqueue(action: PendingAction) {
        preferencesRepository.addPendingAction(action)
    }

    suspend fun flush() {
        val pending = preferencesRepository.pendingActions.first()
        for (action in pending) {
            val result = runCatching {
                when (action) {
                    is PendingAction.LikeSong -> YouTube.likeVideo(action.songId, action.liked).getOrThrow()
                    is PendingAction.SubscribeArtist ->
                        YouTube.subscribeChannel(action.channelId, action.subscribed).getOrThrow()
                }
            }
            if (result.isSuccess) {
                preferencesRepository.removePendingAction(action)
            } else {
                Napier.w("PendingSyncQueue: retry failed for $action: ${result.exceptionOrNull()?.message}")
            }
        }
    }
}
