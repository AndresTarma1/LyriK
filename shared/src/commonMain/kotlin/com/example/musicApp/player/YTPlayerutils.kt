package com.example.musicApp.player

import com.example.musicApp.data.repository.AudioQuality
import com.example.musicApp.utils.cipher.PoTokenResult
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.response.PlayerResponse
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object YTPlayerutils {
    /**
     * Resuelve los datos de reproducción y la URL del stream para un video con la calidad de audio especificada.
     *
     * @return Un `Result` que contiene los datos de reproducción resueltos y la URL del stream.
     */
    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality = AudioQuality.NORMAL,
    ): Result<PlaybackData> = runCatching {

        val signatureTimestamp = getSignatureTimestampOrNull(videoId)
        Napier.i { "Signature timestamp for $videoId: ${signatureTimestamp ?: "null"}" }

        val isLoggedIn = YouTube.cookie != null

        if (YouTube.visitorData == null) {
            YouTube.visitorData().onSuccess { vd ->
                YouTube.visitorData = vd
                Napier.i("visitorData obtained: ${vd.take(8)}...")
            }.onFailure {
                Napier.w("Failed to fetch visitorData: ${it.message}")
            }
        }

        // La generación de PoToken/JCEF está intencionalmente DESHABILITADA: el Chromium embebido (JCEF) consumía
        // mucha RAM y provocaba tirones en el hilo de UI, y el fallback con yt-dlp ya cubre los
        // videos que de otro modo necesitarían un poToken de streaming. Resolvemos con clientes sin
        // poToken y dejamos que YtDlpResolver maneje cualquier cosa que falle al reproducir.
        val poTokenResult: PoTokenResult? = null

        val mainPlayerResponse =
            YouTube.player(
                videoId,
                playlistId,
                FallbackClients.mainClient,
                signatureTimestamp,
                null,
            ).getOrThrow()
        Napier.d("Resolving playback stream for $videoId with quality=$audioQuality signatureTimestamp=$signatureTimestamp")
        val audioConfig = mainPlayerResponse.playerConfig?.audioConfig
        val videoDetails = mainPlayerResponse.videoDetails
        val playbackTracking = mainPlayerResponse.playbackTracking
        var format: PlayerResponse.StreamingData.Format? = null
        var streamUrl: String? = null
        var streamExpiresInSeconds: Int? = null
        var streamPlayerResponse: PlayerResponse? = null

        for (clientIndex in (-1 until FallbackClients.streamFallbackClients.size)) {
            format = null
            streamUrl = null
            streamExpiresInSeconds = null

            val client = if (clientIndex == -1) {
                streamPlayerResponse = mainPlayerResponse
                null
            } else {
                val fallbackClient = FallbackClients.streamFallbackClients[clientIndex]
                if (fallbackClient.loginRequired && !isLoggedIn && YouTube.cookie == null) {
                    Napier.d("Skipping playback client ${fallbackClient.clientName} for $videoId because login is required")
                    continue
                }
                val clientPoToken =
                    if (fallbackClient.useWebPoTokens) null else null
                val result = YouTube.player(videoId, playlistId, fallbackClient, signatureTimestamp, clientPoToken)
                streamPlayerResponse = result.getOrNull()
                if (streamPlayerResponse == null) {
                    Napier.w("Player response null for ${fallbackClient.clientName} for $videoId: ${result.exceptionOrNull()?.message}")
                }
                fallbackClient
            }

            // Con los poTokens deshabilitados, los clientes web (useWebPoTokens) dan error 403 en el CDN y cargarían
            // player.js para sig/n. Se omite completamente su resolución de stream — WEB_REMIX sigue sirviendo
            // como fuente de metadatos arriba; los clientes no-web + yt-dlp cubren la reproducción.
            if ((client ?: FallbackClients.mainClient).useWebPoTokens) {
                continue
            }

            if (streamPlayerResponse?.playabilityStatus?.status == "OK") {
                val newPipeResponse = withContext(Dispatchers.IO) {
                    YouTube.newPipePlayer(videoId, streamPlayerResponse)
                }
                val responseToUse = newPipeResponse ?: streamPlayerResponse
                var clientName = client?.clientName
                    ?: FallbackClients.mainClient.clientName
                Napier.d("Trying playback client $clientName for $videoId; newPipeResponse=${newPipeResponse != null}")

                format = FormatSelector.findFormat(responseToUse, audioQuality)
                if (format == null) {
                    Napier.w("No audio format found for $videoId using client $clientName")
                    continue
                }

                clientName = client?.clientName ?: FallbackClients.mainClient.clientName
                val cacheKey = "$videoId|${format.itag}|$clientName"
                streamUrl = StreamCache.get(cacheKey)

                if (streamUrl == null) {
                    streamUrl = withContext(Dispatchers.IO) {
                        StreamUrlResolver.resolveUrl(format, videoId, responseToUse)
                    }
                    if (streamUrl != null) {
                        val ttl = minOf(
                            streamPlayerResponse.streamingData?.expiresInSeconds?.times(1000L)
                                ?: 300_000L,
                            300_000L,
                        )
                        StreamCache.put(cacheKey, streamUrl, ttl)
                    }
                }

                if (streamUrl == null) {
                    Napier.w("No stream URL found for $videoId itag=${format.itag} using client $clientName")
                    continue
                }

                // Agregar pot=streamingDataPoToken para clientes con PoToken habilitado (WEB_REMIX, WEB, TVHTML5).
                val effectiveClient = client ?: FallbackClients.mainClient
                val streamingPot = null

                // Transformación de n solo para clientes web (igual que Metrolist). Los clientes no-web (VISIONOS,
                // IOS, ANDROID_VR, ...) obtienen URLs cuya n está ausente o ya es manejada por NewPipe;
                // transformarlas con la función n del reproductor web las rompería.
                val needsNTransform = effectiveClient.useWebPoTokens ||
                    effectiveClient.clientName in listOf("WEB", "WEB_REMIX", "WEB_CREATOR", "TVHTML5")
                if (needsNTransform) {
                    streamUrl = StreamUrlResolver.applyNTransform(streamUrl)
                }

                streamExpiresInSeconds = streamPlayerResponse.streamingData?.expiresInSeconds
                if (streamExpiresInSeconds == null) {
                    Napier.w("Missing stream expiry for $videoId using client $clientName; defaulting to 6h")
                    streamExpiresInSeconds = 21600 // 6 hours default
                }

                if (clientIndex == FallbackClients.streamFallbackClients.size - 1) {
                    break
                }

                if (StreamUrlResolver.validate(streamUrl)) {
                    Napier.d("Resolved stream for $videoId using client $clientName itag=${format.itag}")
                    break
                } else {
                    Napier.w("Stream URL validation failed for $videoId using client $clientName itag=${format.itag}; trying fallback client")
                }
            } else if (streamPlayerResponse != null) {
                Napier.w(
                    "Playback client response not OK for $videoId: status=${streamPlayerResponse.playabilityStatus.status}, reason=${streamPlayerResponse.playabilityStatus.reason}",
                )
            }
        }

        if (streamPlayerResponse == null) {
            throw Exception("Bad stream player response")
        }
        if (streamPlayerResponse.playabilityStatus.status != "OK") {
            throw Error(streamPlayerResponse.playabilityStatus.reason ?: "Unknown error")
        }
        if (streamExpiresInSeconds == null) throw Exception("Missing stream expire time")
        if (format == null) throw Exception("Could not find format")
        if (streamUrl == null) throw Exception("Could not find stream url")

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
        return YouTube.player(videoId, playlistId, client = FallbackClients.mainClient)
    }

    private var cachedSignatureTimestamp: Int? = null

    /**
     * Calcula y almacena en caché el timestamp de firma para el reproductor de YouTube.
     *
     * @return El timestamp de firma en días desde la época de Unix.
     */
    private fun getSignatureTimestampOrNull(videoId: String): Int? {
        // signatureTimestamp = días desde la época de Unix (enfoque de SimpMusic)
        // El reproductor de YouTube usa esto para verificar que el cliente está actualizado
        cachedSignatureTimestamp?.let { return it }
        val ts = (System.currentTimeMillis() / 86400000L).toInt()
        cachedSignatureTimestamp = ts
        Napier.d("Computed signatureTimestamp=$ts for $videoId (days since epoch)")
        return ts
    }

    /**
     * Invalida las URLs de stream en caché para un video.
     *
     * @param videoId El ID del video.
     */
    suspend fun forceRefreshForVideo(videoId: String) {
        StreamCache.invalidateForVideo(videoId)
    }
}
