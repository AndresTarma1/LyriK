package com.example.musicApp.player

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.LinkedHashMap

object StreamCache {
    private val streamUrlCache = object : LinkedHashMap<String, Pair<String, Long>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<String, Long>>?): Boolean {
            return size > 20
        }
    }
    private val cacheMutex = Mutex()

    suspend fun get(key: String): String? {
        return cacheMutex.withLock {
            val cached = streamUrlCache[key]
            cached?.takeIf { System.currentTimeMillis() < it.second }?.first
        }
    }

    suspend fun put(key: String, url: String, ttlMillis: Long) {
        cacheMutex.withLock {
            streamUrlCache[key] = Pair(url, System.currentTimeMillis() + ttlMillis)
        }
    }

    suspend fun invalidateForVideo(videoId: String) {
        cacheMutex.lock()
        try {
            val keysToRemove = streamUrlCache.keys.filter { it.startsWith("$videoId|") }
            keysToRemove.forEach { streamUrlCache.remove(it) }
        } finally {
            cacheMutex.unlock()
        }
    }
}
