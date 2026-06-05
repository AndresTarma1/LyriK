package com.example.melodist.player

import com.example.melodist.data.repository.AudioQuality
import com.metrolist.innertube.NewPipeExtractor
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.response.PlayerResponse
import io.github.aakira.napier.Napier

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

        // TODO: PoToken generation for WEB_REMIX client
        // 1. Ensure dataSyncId/visitorData is available (fetch if null)
        // 2. Generate PoToken via PoTokenGenerator.getWebClientPoToken(videoId, sessionId)
        // 3. Pass poToken.playerRequestPoToken to YouTube.player() below
        // 4. After URL resolution, append pot=streamingDataPoToken to stream URLs
        //    for clients with useWebPoTokens=true
        

        val mainPlayerResponse =
            YouTube.player(videoId, playlistId, FallbackClients.mainClient, signatureTimestamp).getOrThrow()
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
                streamPlayerResponse =
                    YouTube.player(videoId, playlistId, fallbackClient, signatureTimestamp).getOrNull()
                fallbackClient
            }

            if (streamPlayerResponse?.playabilityStatus?.status == "OK") {
                val newPipeResponse = YouTube.newPipePlayer(videoId, streamPlayerResponse)
                val responseToUse = newPipeResponse ?: streamPlayerResponse
                val clientName = client?.clientName
                    ?: FallbackClients.mainClient.clientName
                Napier.d("Trying playback client $clientName for $videoId; newPipeResponse=${newPipeResponse != null}")

                format = FormatSelector.findFormat(responseToUse, audioQuality)
                if (format == null) {
                    Napier.w("No audio format found for $videoId using client $clientName")
                    continue
                }

                val cacheKey = "$videoId|${format.itag}"
                streamUrl = StreamCache.get(cacheKey)

                if (streamUrl == null) {
                    streamUrl = StreamUrlResolver.resolveUrl(format, videoId, responseToUse)
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

                // TODO: Append pot=streamingDataPoToken for PoToken-enabled clients
                streamUrl = StreamUrlResolver.applyNTransform(streamUrl)

                streamExpiresInSeconds = streamPlayerResponse.streamingData?.expiresInSeconds
                if (streamExpiresInSeconds == null) {
                    Napier.w("Missing stream expiry for $videoId using client $clientName")
                    continue
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

    private fun getSignatureTimestampOrNull(videoId: String): Int? {
        return NewPipeExtractor.getSignatureTimestamp(videoId).getOrNull()
    }

    suspend fun forceRefreshForVideo(videoId: String) {
        StreamCache.invalidateForVideo(videoId)
    }
}
