package com.example.melodist.utils

import kotlinx.coroutines.delay

/**
 * Retries [block] up to [attempts] times with exponential backoff (1s, 2s, 4s, ...), for
 * best-effort pushes to YouTube (like/unlike, playlist add/remove) where a transient network
 * failure shouldn't silently drop the user's action. Returns the last result (success or failure).
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
