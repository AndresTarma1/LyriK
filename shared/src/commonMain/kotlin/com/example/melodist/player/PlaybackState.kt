package com.example.melodist.player

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

