package com.example.musicApp.player

import com.example.musicApp.data.repository.AudioQuality
import com.example.musicApp.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first

/**
 * Custom exception for age-restricted content.
 */
class AgeRestrictedException(message: String) : Exception(message)

class AudioStreamResolver(
    private val userPreferences: UserPreferencesRepository,
) {
    /**
     * Resolves audio stream data for a video using the user's current audio quality preference.
     *
     * @param videoId The ID of the video to resolve the audio stream for.
     * @return The playback data for the video.
     * @throws Exception If the audio stream cannot be resolved.
     */
    suspend fun resolveAudioStream(videoId: String): PlaybackData {
        val quality = userPreferences.audioQuality.first()
        return YTPlayerutils.playerResponseForPlayback(videoId = videoId, audioQuality = quality).fold(
            onSuccess = { data -> data },
            onFailure = { error -> throw Exception("Vaya error: $error") }
        )
    }

    /**
 * Retrieves the current user-selected audio quality preference.
 *
 * @return The user's current audio quality preference.
 */
    suspend fun currentAudioQuality(): AudioQuality = userPreferences.audioQuality.first()
}
