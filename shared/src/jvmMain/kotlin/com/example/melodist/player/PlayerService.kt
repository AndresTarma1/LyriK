package com.example.melodist.player

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.logging.Logger
import kotlin.time.Duration.Companion.milliseconds

class PlayerService {

    private val log = Logger.getLogger("PlayerService")
    private val mpvPlayer = MpvAudioPlayer()
    private val isMpvDisabled = false

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _volume = MutableStateFlow(100)
    val volume: StateFlow<Int> = _volume.asStateFlow()

    private var _previousVolume = 100

    private var initAttempted = false

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var tickJob: Job? = null

    @Volatile
    private var isTransitioning = false

    @Volatile
    private var endNotified = false

    private var prevPlayingPos = 0L

    fun init() {
        if (initAttempted) return
        initAttempted = true
        if (isMpvDisabled) {
            log.warning("mpv disabled via -Dmelodist.disableMpv=true")
            return
        }
        mpvPlayer.init()
        startPositionTicker()
    }

    /**
     * Loads and initiates playback of the specified audio URL.
     *
     * Resets the playback position and duration. If loading fails, the playback state is
     * set to ERROR and the error is logged.
     */
    fun play(url: String) {
        init()
        if (isMpvDisabled) {
            _playbackState.value = PlaybackState.ERROR
            _position.value = 0L
            _duration.value = 0L
            return
        }
        try {
            _playbackState.value = PlaybackState.LOADING
            isTransitioning = false
            endNotified = false
            prevPlayingPos = 0L
            _position.value = 0L
            _duration.value = 0L
            // openUri is non-blocking (it launches its own IO job) and arms the load-result
            // synchronously, so a subsequent awaitPlaybackStarted() observes THIS load, not a
            // stale result from the previous track.
            mpvPlayer.openUri(url)
        } catch (e: Exception) {
            _playbackState.value = PlaybackState.ERROR
            log.severe("Error al reproducir: ${e.message}")
        }
    }

    /**
     * Waits for playback to start within a specified timeout.
     *
     * @return `true` if playback started within the timeout, `false` otherwise.
     */
    suspend fun awaitPlaybackStarted(timeoutMs: Long = 6000): Boolean {
        if (isMpvDisabled) return false
        return mpvPlayer.awaitPlaybackStarted(timeoutMs)
    }

    /**
     * Pauses playback and updates the playback state to `PAUSED`.
     */
    fun pause() {
        isTransitioning = false
        endNotified = false
        _playbackState.value = PlaybackState.PAUSED
        if (isMpvDisabled) return
        mpvPlayer.pause()
    }

    fun resume() {
        isTransitioning = false
        endNotified = false
        _playbackState.value = PlaybackState.PLAYING
        if (isMpvDisabled) return
        mpvPlayer.play()
    }

    fun togglePlayPause() {
        if (_playbackState.value == PlaybackState.PLAYING) pause() else resume()
    }

    fun stop() {
        isTransitioning = false
        endNotified = false
        prevPlayingPos = 0L
        _playbackState.value = PlaybackState.IDLE
        _position.value = 0L
        _duration.value = 0L
        if (isMpvDisabled) return
        mpvPlayer.stop()
    }

    fun seekTo(millis: Long) {
        if (isMpvDisabled) return
        val dur = _duration.value
        if (dur > 0) {
            val endThresholdMs = 1000L
            if (millis < dur - endThresholdMs) {
                endNotified = false
            }
            prevPlayingPos = 0L
            mpvPlayer.seekTo(millis.toFloat() / dur.toFloat())
        }
    }

    fun setVolume(value: Int) {
        _volume.value = value
        if (isMpvDisabled) return
        mpvPlayer.volume = value.toFloat() / 100f
    }

    fun toggleMute() {
        if (_volume.value > 0) {
            _previousVolume = _volume.value
            setVolume(0)
        } else {
            setVolume(_previousVolume)
        }
    }

    fun setEqualizer(bands: List<Float>) {
        if (isMpvDisabled) return
        // Send values to mpv
        mpvPlayer.setEqualizer(bands)
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        if (isMpvDisabled) return
        mpvPlayer.setGaplessAudio(enabled)
    }

    fun release() {
        tickJob?.cancel()
        if (!isMpvDisabled) {
            mpvPlayer.dispose()
        }
        scope.cancel()
    }

    private fun startPositionTicker() {
        if (isMpvDisabled) return
        tickJob = scope.launch {
            while (isActive) {
                try {
                    val shouldPoll = _playbackState.value == PlaybackState.PLAYING ||
                            _playbackState.value == PlaybackState.LOADING

                    if (shouldPoll) {
                        val dur = mpvPlayer.getDuration()
                        _duration.value = dur

                        val pos = mpvPlayer.getCurrentPosition()
                        _position.value = pos

                        val endThresholdMs = 200L
                        // Detecta si mpv reseteó time-pos a 0 después de que la canción terminó
                        val posReset = prevPlayingPos > endThresholdMs && pos <= 100 &&
                                _playbackState.value == PlaybackState.PLAYING

                        val looksEnded =
                            !endNotified &&
                            _playbackState.value != PlaybackState.LOADING &&
                            (
                                (dur > endThresholdMs && pos >= (dur - endThresholdMs)) ||
                                posReset
                            )

                        // Guarda la posición para detectar reseteo en la próxima iteración
                        if (pos > 0 && _playbackState.value == PlaybackState.PLAYING) {
                            prevPlayingPos = pos
                        }

                        if (looksEnded) {
                            endNotified = true
                            _playbackState.value = PlaybackState.ENDED
                            delay(500.milliseconds)
                            continue
                        }

                        if (!isTransitioning) {
                            val playing = mpvPlayer.isPlaying.value
                            if (playing && _playbackState.value != PlaybackState.PLAYING) {
                                endNotified = false
                                _playbackState.value = PlaybackState.PLAYING
                            } else if (!playing && _playbackState.value == PlaybackState.PLAYING) {
                                _playbackState.value = PlaybackState.PAUSED
                            }
                        }
                    }
                } catch (e: Throwable) {
                    // silent catch for background ticker
                }
                val pollInterval = 1000L
                delay(pollInterval.milliseconds)
            }
        }
    }

    fun stopAudioOnly() {
        isTransitioning = true
        endNotified = false
        prevPlayingPos = 0L
        _position.value = 0L
        _duration.value = 0L
        if (isMpvDisabled) return
        scope.launch {
            try {
                mpvPlayer.pause()
            } catch (e: Throwable) { /* ignore */ }
        }
    }
}
