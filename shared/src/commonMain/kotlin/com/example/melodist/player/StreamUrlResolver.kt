package com.example.melodist.player

import com.example.melodist.utils.cipher.CipherDeobfuscator
import com.metrolist.innertube.NewPipeExtractor
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.response.PlayerResponse
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.head
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess

object StreamUrlResolver {
    private val validationClient by lazy {
        HttpClient(CIO) {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 3000
                requestTimeoutMillis = 5000
            }
        }
    }

    suspend fun resolveUrl(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        playerResponse: PlayerResponse,
    ): String? {
        val formatUrl = format.url
        Napier.i("[RESOLVE_URL] videoId=$videoId itag=${format.itag} url=${formatUrl?.take(80)} sigCipher=${format.signatureCipher?.take(50)} cipher=${format.cipher?.take(50)}")
        if (formatUrl != null) {
            Napier.i("[RESOLVE_URL] URL analysis: hasN=${"n=" in formatUrl} hasPot=${"pot=" in formatUrl} urlLength=${formatUrl.length}")
        }

        if (!formatUrl.isNullOrEmpty()) {
            return formatUrl
        }

        val deobfuscatedUrl = NewPipeExtractor.getStreamUrl(format, videoId)
        if (deobfuscatedUrl != null) {
            Napier.d("Using NewPipe-deobfuscated stream URL for $videoId itag=${format.itag}")
            return deobfuscatedUrl
        }

        val signatureCipher = format.signatureCipher ?: format.cipher
        if (!signatureCipher.isNullOrEmpty()) {
            Napier.d("Trying CipherDeobfuscator for $videoId itag=${format.itag}")
            val customUrl = CipherDeobfuscator.deobfuscateStreamUrl(signatureCipher, videoId)
            if (customUrl != null) {
                Napier.d("Using CipherDeobfuscator stream URL for $videoId itag=${format.itag}")
                return customUrl
            }
            Napier.d("CipherDeobfuscator failed for $videoId itag=${format.itag}")
        }

        val streamUrls = YouTube.getNewPipeStreamUrls(videoId)
        if (streamUrls.isNotEmpty()) {
            val streamUrl = streamUrls.find { it.first == format.itag }?.second
            if (streamUrl != null) {
                Napier.d("Using NewPipe StreamInfo URL for $videoId itag=${format.itag}")
                return streamUrl
            }
            val audioStream = streamUrls.find { urlPair ->
                playerResponse.streamingData?.adaptiveFormats?.any {
                    it.itag == urlPair.first && it.isAudio
                } == true
            }?.second
            if (audioStream != null) {
                Napier.d("Using NewPipe StreamInfo audio fallback URL for $videoId requestedItag=${format.itag}")
                return audioStream
            }
        }

        return null
    }

    suspend fun applyNTransform(url: String): String {
        return if ("n=" in url) {
            Napier.i("[N-TRANSFORM] Applying n-transform")
            CipherDeobfuscator.transformNParamInUrl(url)
        } else {
            url
        }
    }

    suspend fun validate(url: String): Boolean {
        return try {
            val response: HttpResponse = validationClient.head(url)
            response.status.isSuccess()
        } catch (e: Exception) {
            Napier.w("Stream URL validation request failed", e)
            false
        }
    }
}
