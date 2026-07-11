package com.example.melodist.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Fast connectivity probe used to tell "no internet" apart from a per-song playback failure
 * (age-gated video, needs yt-dlp, etc). A raw TCP connect is quicker and more decisive than
 * waiting for the full resolve/yt-dlp cascade to time out on its own.
 */
actual object NetworkMonitor {
    actual suspend fun isOnline(timeoutMs: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { it.connect(InetSocketAddress("music.youtube.com", 443), timeoutMs) }
            true
        }.getOrDefault(false)
    }
}
