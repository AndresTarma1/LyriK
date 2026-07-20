package com.example.musicApp.player

/**
 * Possible states of the audio player.
 */
enum class PlaybackState {
    IDLE,
    LOADING,
    READY,
    PLAYING,
    PAUSED,
    BUFFERING,
    ERROR,
    ENDED
}

