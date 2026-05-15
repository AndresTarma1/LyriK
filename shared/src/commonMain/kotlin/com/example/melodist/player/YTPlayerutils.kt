package com.example.melodist.player

import com.metrolist.innertube.NewPipeExtractor
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.response.PlayerResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.head
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.LinkedHashMap

object YTPlayerutils {
    private val MAIN_CLIENT: YouTubeClient = YouTubeClient.WEB_REMIX

    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        YouTubeClient.TVHTML5,
        YouTubeClient.ANDROID_VR_1_43_32,
        YouTubeClient.ANDROID_VR_1_61_48,
        YouTubeClient.ANDROID_CREATOR,
        YouTubeClient.IPADOS,
        YouTubeClient.ANDROID_VR_NO_AUTH,
        YouTubeClient.MOBILE,
        YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        YouTubeClient.IOS,
        YouTubeClient.WEB,
        YouTubeClient.WEB_CREATOR
    )

    /**
     * Cliente HTTP singleton para validación de streams.
     * Evita crear un nuevo cliente en cada llamada (memory leak).
     */
    private val validationClient by lazy {
        HttpClient(CIO) {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 3000
                requestTimeoutMillis = 5000
            }
        }
    }

    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
    )

    /**
     * Cache simple con tamaño máximo para evitar peticiones repetidas de stream URL.
     * Clave: "$videoId|$format.itag"
     */
    private val streamUrlCache = object : LinkedHashMap<String, Pair<String, Long>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<String, Long>>?): Boolean {
            return size > 20
        }
    }
    private val cacheMutex = Mutex()

    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality = AudioQuality.AUTO,
    ): Result<PlaybackData> = runCatching {
        /**
         * This is required for some clients to get working streams however
         * it should not be forced for the [MAIN_CLIENT] because the response of the [MAIN_CLIENT]
         * is required even if the streams won't work from this client.
         * This is why it is allowed to be null.
         */
        val signatureTimestamp = getSignatureTimestampOrNull(videoId)

        val isLoggedIn = YouTube.cookie != null

        val mainPlayerResponse =
            YouTube.player(videoId, playlistId, MAIN_CLIENT, signatureTimestamp).getOrThrow()
        val audioConfig = mainPlayerResponse.playerConfig?.audioConfig
        val videoDetails = mainPlayerResponse.videoDetails
        val playbackTracking = mainPlayerResponse.playbackTracking
        var format: PlayerResponse.StreamingData.Format? = null
        var streamUrl: String? = null
        var streamExpiresInSeconds: Int? = null
        var streamPlayerResponse: PlayerResponse? = null

        for (clientIndex in (-1 until STREAM_FALLBACK_CLIENTS.size)) {
            // reset for each client
            format = null
            streamUrl = null
            streamExpiresInSeconds = null

            // decide which client to use for streams and load its player response
            val client: YouTubeClient
            if (clientIndex == -1) {
                // try with streams from main client first
                streamPlayerResponse = mainPlayerResponse
            } else {
                // after main client use fallback clients
                client = STREAM_FALLBACK_CLIENTS[clientIndex]

                if (client.loginRequired && !isLoggedIn && YouTube.cookie == null) {
                    // skip client if it requires login but user is not logged in
                    continue
                }

                streamPlayerResponse =
                    YouTube.player(videoId, playlistId, client, signatureTimestamp).getOrNull()
            }

            // process current client response
            if (streamPlayerResponse?.playabilityStatus?.status == "OK") {
                // Try to get streams using newPipePlayer method
                val newPipeResponse = YouTube.newPipePlayer(videoId, streamPlayerResponse)
                val responseToUse = newPipeResponse ?: streamPlayerResponse

                format =
                    findFormat(
                        responseToUse,
                        audioQuality,
                    )

                if (format == null) {
                    continue
                }

                // Revisar cache antes de validar red
                val cacheKey = "$videoId|${format.itag}"
                val cached = cacheMutex.withLock { streamUrlCache[cacheKey] }
                streamUrl = cached?.takeIf { System.currentTimeMillis() < it.second }?.first

                if (streamUrl == null) {
                    streamUrl = findUrlOrNull(format, videoId, responseToUse)
                    if (streamUrl != null) {
                        // Cache por 5 minutos (300_000 ms) o el tiempo de expiración del stream
                        val ttl = minOf(
                            streamPlayerResponse.streamingData?.expiresInSeconds?.times(1000L)
                                ?: 300_000L,
                            300_000L
                        )
                        cacheMutex.withLock {
                            streamUrlCache[cacheKey] = Pair(streamUrl, System.currentTimeMillis() + ttl)
                        }
                    }
                }

                if (streamUrl == null) {
                    continue
                }

                streamExpiresInSeconds = streamPlayerResponse.streamingData?.expiresInSeconds
                if (streamExpiresInSeconds == null) {
                    continue
                }


                if (clientIndex == STREAM_FALLBACK_CLIENTS.size - 1) {
                    break
                }

                if (validateStatus(streamUrl)) {
                    // working stream found
                    break
                }
            }
        }

        if (streamPlayerResponse == null) {
            throw Exception("Bad stream player response")
        }

        if (streamPlayerResponse.playabilityStatus.status != "OK") {
            val errorReason = streamPlayerResponse.playabilityStatus.reason
            throw Error("$errorReason")
        }

        if (streamExpiresInSeconds == null) {
            throw Exception("Missing stream expire time")
        }

        if (format == null) {
            throw Exception("Could not find format")
        }

        if (streamUrl == null) {
            throw Exception("Could not find stream url")
        }

        PlaybackData(
            audioConfig,
            videoDetails,
            playbackTracking,
            format,
            streamUrl,
            streamExpiresInSeconds,
        )
    }

    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
    ): Result<PlayerResponse> {
        return YouTube.player(videoId, playlistId, client = YouTubeClient.WEB_REMIX)
    }

    private fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
    ): PlayerResponse.StreamingData.Format? {

        val format = playerResponse.streamingData?.adaptiveFormats
            ?.filter { it.isAudio && it.isOriginal }
            ?.maxByOrNull {
                it.bitrate * when (audioQuality) {
                    AudioQuality.AUTO -> -1
                    AudioQuality.HIGH -> 1
                    AudioQuality.LOW -> -1
                } + (if (it.mimeType.startsWith("audio/webm")) 10240 else 0) // prefer opus stream
            }
        return format
    }

    /**
     * Checks if the stream url returns a successful status.
     * If this returns true the url is likely to work.
     * If this returns false the url might cause an error during playback.
     */
    private suspend fun validateStatus(url: String): Boolean {
        return try {
            // ✅ Usa el cliente singleton en vez de crear uno nuevo cada vez
            val response: HttpResponse = validationClient.head(url)
            response.status.isSuccess()
        } catch (e: Exception) {
            false
        }
    }

    private fun getSignatureTimestampOrNull(videoId: String): Int? {
        return NewPipeExtractor.getSignatureTimestamp(videoId)
            .getOrNull()
    }

    private fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        playerResponse: PlayerResponse
    ): String? {

        // First check if format already has a URL from newPipePlayer
        if (!format.url.isNullOrEmpty()) {
            return format.url
        }

        // Try to get URL using NewPipeExtractor signature deobfuscation
        val deobfuscatedUrl = NewPipeExtractor.getStreamUrl(format, videoId)
        if (deobfuscatedUrl != null) {
            return deobfuscatedUrl
        }

        // Fallback: try to get URL from StreamInfo
        val streamUrls = YouTube.getNewPipeStreamUrls(videoId)
        if (streamUrls.isNotEmpty()) {
            val streamUrl = streamUrls.find { it.first == format.itag }?.second
            if (streamUrl != null) {
                return streamUrl
            }

            // If exact itag not found, try to find any audio stream
            val audioStream = streamUrls.find { urlPair ->
                playerResponse.streamingData?.adaptiveFormats?.any {
                    it.itag == urlPair.first && it.isAudio
                } == true
            }?.second

            if (audioStream != null) {
                return audioStream
            }
        }

        return null
    }

    /**
     * Invalida la cache de stream URLs para un video específico.
     * Útil cuando se refresca manualmente la información de un video.
     */
    suspend fun forceRefreshForVideo(videoId: String) {
        cacheMutex.lock()
        try {
            val keysToRemove = streamUrlCache.keys.filter { it.startsWith("$videoId|") }
            keysToRemove.forEach { streamUrlCache.remove(it) }
        } finally {
            cacheMutex.unlock()
        }
    }
}