package com.example.musicApp.utils

import kotlinx.coroutines.delay

/**
 * Reintenta [block] hasta [attempts] veces con retroceso exponencial (1s, 2s, 4s, ...), para
 * envíos de esfuerzo máximo a YouTube (like/unlike, agregar/eliminar de playlist) donde un fallo
 * de red transitorio no debería descartar silenciosamente la acción del usuario. Retorna el último
 * resultado (éxito o fallo).
 */
suspend fun <T> retryWithBackoff(
    attempts: Int = 3,
    initialDelayMs: Long = 1000,
    block: suspend () -> Result<T>,
): Result<T> {
    var lastResult: Result<T> = block()
    var delayMs = initialDelayMs
    repeat(attempts - 1) {
        if (lastResult.isSuccess) return lastResult
        delay(delayMs)
        delayMs *= 2
        lastResult = block()
    }
    return lastResult
}
