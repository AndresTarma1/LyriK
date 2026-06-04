package com.example.melodist.player

import com.example.melodist.data.repository.AudioQuality
import com.metrolist.innertube.models.response.PlayerResponse

object FormatSelector {
    fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
    ): PlayerResponse.StreamingData.Format? {
        val formats = playerResponse.streamingData?.adaptiveFormats
            ?.filter { it.isAudio && it.isOriginal }
            ?: return null

        return when (audioQuality) {
            AudioQuality.LOW -> formats.minByOrNull { it.bitrate }
            AudioQuality.NORMAL -> {
                val sorted = formats.sortedBy { it.bitrate }
                sorted.getOrNull(sorted.size / 2) ?: sorted.firstOrNull()
            }
            AudioQuality.HIGH -> formats.maxByOrNull { it.bitrate }
        }
    }
}
