package com.example.melodist.player

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.response.PlayerResponse

data class ResolvedStream(
    val url: String,
    val itag: Int,
    val mimeType: String,
    val bitrate: Int,
    val contentLength: Long?,
    val sampleRate: Int?,
    val loudnessDb: Double?,
    val codecs: String,
    val expiresInSeconds: Long?,
)

/**
 * Resolved stream info including URL and format metadata.
 */

/**
 * Audio quality selection for stream resolution.
 */


/**
 * Custom exception for age-restricted content.
 */
class AgeRestrictedException(message: String) : Exception(message)

/**
 * Resolves a video ID into a playable audio stream URL.
 *
 * Strategy (mirrors Metrolist's YTPlayerUtils):
 *   1. MAIN client: WEB_REMIX (+ NewPipe deobfuscation + per-client signatureTimestamp)
 *   2. FALLBACK clients in order (each gets its own fresh signatureTimestamp):
 *      TVHTML5_SIMPLY_EMBEDDED_PLAYER, TVHTML5, ANDROID_VR_1_43_32, ANDROID_VR_1_61_48,
 *      ANDROID_CREATOR, IPADOS, ANDROID_VR_NO_AUTH, MOBILE, IOS, WEB, WEB_CREATOR
 */
class AudioStreamResolver {


    var quality = AudioQuality.AUTO

    /**
     * Returns full stream info (URL + format metadata) for [videoId], or `null` if all clients fail.
     * Used by DownloadService to get contentLength, itag, mimeType for chunked downloads.
     */
    suspend fun resolveAudioStream(videoId: String): YTPlayerutils.PlaybackData {
        return YTPlayerutils.playerResponseForPlayback(videoId = videoId, audioQuality = quality).fold(
            onSuccess = { data ->
                data // Este es el valor que se retorna
            },
            onFailure = { error ->
                // Lanzamos una excepción con tu mensaje personalizado
                throw Exception("Vaya error: ${error.message}")
            }
        )
    }

    /**
     * Picks the best audio-only format from a [PlayerResponse] and returns full metadata.
     */
    private fun extractBestAudioStream(response: PlayerResponse): ResolvedStream? {
        if (response.playabilityStatus.status != "OK") return null

        val allFormats = response.streamingData?.adaptiveFormats ?: emptyList()
        val audioFormats = allFormats.filter { it.isAudio && it.isOriginal && it.url != null }

        if (audioFormats.isEmpty()) return null

        val mp4Formats = audioFormats.filter { it.mimeType.contains("audio/mp4") }.ifEmpty { audioFormats }

        val bestFormat = when (quality) {
            AudioQuality.LOW -> mp4Formats.minByOrNull { it.bitrate }
            AudioQuality.AUTO -> {
                // Pick a mid-range bitrate (prefer ~128kbps)
                val sorted = mp4Formats.sortedBy { it.bitrate }
                sorted.getOrNull(sorted.size / 2) ?: sorted.firstOrNull()
            }

            AudioQuality.HIGH -> mp4Formats.maxByOrNull { it.bitrate }
        } ?: return null

        // Extract codecs from mimeType (e.g. "audio/mp4; codecs=\"mp4a.40.2\"" → "mp4a.40.2")
        val codecs = Regex("""codecs="([^"]+)"""").find(bestFormat.mimeType)?.groupValues?.get(1)
            ?: bestFormat.mimeType.substringAfter("codecs=").trim('"')

        return ResolvedStream(
            url = bestFormat.url!!,
            itag = bestFormat.itag,
            mimeType = bestFormat.mimeType,
            bitrate = bestFormat.bitrate,
            contentLength = bestFormat.contentLength,
            sampleRate = bestFormat.audioSampleRate,
            loudnessDb = bestFormat.loudnessDb,
            codecs = codecs,
            expiresInSeconds = response.streamingData?.expiresInSeconds?.toLong()
        )
    }
}
