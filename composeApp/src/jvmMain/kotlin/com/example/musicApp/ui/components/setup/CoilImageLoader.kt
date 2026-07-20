package com.example.musicApp.ui.components

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.example.musicApp.data.AppDirs
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newFixedThreadPoolContext
import okio.Path.Companion.toPath

/**
 * Crea un [ImageLoader] optimizado con caché en memoria y en disco.
 */
object CoilSetup {

    // Pool de hilos dedicado para la obtención y decodificación de imágenes. Limitamos el despachador
    // IO global (`kotlinx.coroutines.io.parallelism=16`) por memoria; en sesiones largas ese pool se
    // satura con E/S bloqueante (resolución de streams, validaciones de URL de 8s, descargas), lo que
    // ahogaba a Coil y hacía que nuevas miniaturas dejaran de cargar mientras las en caché seguían
    // mostrándose. Aislar Coil en su propio pool soluciona esto sin elevar el límite global.
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
