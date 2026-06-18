package com.example.melodist.player

import com.example.melodist.data.repository.AudioQuality
import com.example.melodist.utils.cipher.PoTokenResult
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.NewPipeExtractor
import com.metrolist.innertube.models.response.PlayerResponse
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object YTPlayerutils {
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

        // PoToken/JCEF generation is intentionally DISABLED: the embedded Chromium (JCEF) used a
        // lot of RAM and stuttered the UI thread, and the yt-dlp fallback already covers the
        // videos that would otherwise need a streaming poToken. We resolve with poToken-free
        // clients here and let YtDlpResolver handle anything that fails to actually play.
        val poTokenResult: PoTokenResult? = null

        val mainPlayerResponse =
            YouTube.player(
                videoId,
                playlistId,
                FallbackClients.mainClient,
                signatureTimestamp,
                poTokenResult?.playerRequestPoToken,
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
                    if (fallbackClient.useWebPoTokens) poTokenResult?.playerRequestPoToken else null
                val result = YouTube.player(videoId, playlistId, fallbackClient, signatureTimestamp, clientPoToken)
                streamPlayerResponse = result.getOrNull()
                if (streamPlayerResponse == null) {
                    Napier.w("Player response null for ${fallbackClient.clientName} for $videoId: ${result.exceptionOrNull()?.message}")
                }
                fallbackClient
            }

            // With poTokens disabled, web clients (useWebPoTokens) 403 on the CDN and would load
            // player.js for sig/n. Skip their stream resolution entirely — WEB_REMIX still served
            // as the metadata source above; non-web clients + yt-dlp cover playback.
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

                // Append pot=streamingDataPoToken for PoToken-enabled clients (WEB_REMIX, WEB, TVHTML5).
                val effectiveClient = client ?: FallbackClients.mainClient
                val streamingPot = poTokenResult?.streamingDataPoToken
                if (effectiveClient.useWebPoTokens && streamingPot != null && "pot=" !in streamUrl) {
                    streamUrl += if ("?" in streamUrl) "&pot=$streamingPot" else "?pot=$streamingPot"
                    Napier.d("Appended pot= to stream URL for $videoId client=${effectiveClient.clientName}")
                }

                // n-transform only for web clients (same as Metrolist). Non-web clients (VISIONOS,
                // IOS, ANDROID_VR, ...) get URLs whose n is absent or already handled by NewPipe;
                // transforming those with the web player's n-function would break them.
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

    private fun getSignatureTimestampOrNull(videoId: String): Int? {
        // signatureTimestamp = days since Unix epoch (SimpMusic approach)
        // YouTube's player uses this to verify the client is up-to-date
        cachedSignatureTimestamp?.let { return it }
        val ts = (System.currentTimeMillis() / 86400000L).toInt()
        cachedSignatureTimestamp = ts
        Napier.d("Computed signatureTimestamp=$ts for $videoId (days since epoch)")
        return ts
    }

    suspend fun forceRefreshForVideo(videoId: String) {
        StreamCache.invalidateForVideo(videoId)
    }
}
