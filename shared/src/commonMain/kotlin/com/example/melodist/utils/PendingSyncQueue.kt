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
 * Una acción remota de YouTube Music que falló al enviarse mientras estaba offline (o con el modo
 * offline activado) y debe reintentarse una vez que la conectividad vuelva. El estado local (BD, UI)
 * siempre se aplica inmediatamente sin importar la red — esto solo rastrea el lado *remoto* de la acción.
 *
 * Para soportar un nuevo tipo de acción: agregar una variante aquí, una rama en [PendingSyncQueue.flush],
 * y encolarla donde actualmente el envío en vivo solo registra y descarta en caso de fallo.
 */
@Serializable
sealed class PendingAction {
    @Serializable
    data class LikeSong(val songId: String, val liked: Boolean) : PendingAction()

    @Serializable
    data class SubscribeArtist(val channelId: String, val subscribed: Boolean) : PendingAction()
}

/**
 * Cola persistida de [PendingAction]s que no pudieron enviarse a YouTube mientras estaba offline.
 * Las reintenta automáticamente en segundo plano siempre que haya algo pendiente y una conexión
 * esté disponible — los llamadores solo [enqueue] en caso de fallo, no hay nada más que configurar.
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
