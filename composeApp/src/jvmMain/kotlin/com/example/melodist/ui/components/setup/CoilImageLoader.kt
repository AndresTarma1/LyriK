package com.example.melodist.ui.components

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.example.melodist.data.AppDirs
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newFixedThreadPoolContext
import okio.Path.Companion.toPath

/**
 * Creates an optimized [ImageLoader] with both memory and disk caching.
 */
object CoilSetup {

    // Dedicated thread pool for image fetch/decode. We cap the global IO dispatcher
    // (`kotlinx.coroutines.io.parallelism=16`) for memory; under long sessions that pool gets
    // saturated by blocking IO (stream resolve, 8s URL validations, downloads), which starved Coil
    // and made new thumbnails stop loading while cached ones still showed. Isolating Coil on its own
    // pool fixes that without raising the global cap.
    @OptIn(DelicateCoroutinesApi::class)
    private val imageDispatcher = newFixedThreadPoolContext(8, "coil-io")

    fun createImageLoader(context: PlatformContext): ImageLoader {
        val cacheDir = AppDirs.imageCacheDir
        if (!cacheDir.exists()) cacheDir.mkdirs()

        return ImageLoader.Builder(context)
            .fetcherCoroutineContext(imageDispatcher)
            .decoderCoroutineContext(imageDispatcher)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(1024 * 1024 * 48)
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.absolutePath.toPath())
                    // El disco puede guardar más sin impacto en RAM
                    .maxSizeBytes(128L * 1024 * 1024)
                    .build()
            }
            .crossfade(200)
            .build()
    }
}
