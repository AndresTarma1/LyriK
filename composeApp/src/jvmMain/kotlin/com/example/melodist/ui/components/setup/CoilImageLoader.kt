package com.example.melodist.ui.components

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.example.melodist.data.AppDirs
import okio.Path.Companion.toPath

/**
 * Creates an optimized [ImageLoader] with both memory and disk caching.
 */
object CoilSetup {

    fun createImageLoader(context: PlatformContext): ImageLoader {
        val cacheDir = AppDirs.imageCacheDir
        if (!cacheDir.exists()) cacheDir.mkdirs()

        return ImageLoader.Builder(context)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder()
                    // 20MB es suficiente para una app desktop de música.
                    // Las thumbnails son pequeñas y el disco cache cubre el resto.
                    .maxSizeBytes(1024 * 1024 * 20)
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

