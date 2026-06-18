package com.example.melodist.player

import com.example.melodist.data.repository.AudioQuality
import com.example.melodist.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first

/**
 * Custom exception for age-restricted content.
 */
class AgeRestrictedException(message: String) : Exception(message)

class AudioStreamResolver(
    private val userPreferences: UserPreferencesRepository,
) {
    suspend fun resolveAudioStream(videoId: String): PlaybackData {
        val quality = userPreferences.audioQuality.first()
        return YTPlayerutils.playerResponseForPlayback(videoId = videoId, audioQuality = quality).fold(
            onSuccess = { data -> data },
            onFailure = { error -> throw Exception("Vaya error: $error") }
        )
    }

    /** Current user-selected audio quality (Baja/Normal/Alta), e.g. for the yt-dlp fallback. */
    suspend fun currentAudioQuality(): AudioQuality = userPreferences.audioQuality.first()
}
