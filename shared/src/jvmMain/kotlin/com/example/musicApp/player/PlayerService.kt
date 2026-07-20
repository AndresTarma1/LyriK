package com.example.musicApp.player

import com.example.musicApp.data.repository.UserPreferencesRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.logging.Logger
import kotlin.time.Duration.Companion.milliseconds

class PlayerService
    (
    private val userPreferences: UserPreferencesRepository
            ){

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

        loadSavedVolume()

        // Fin natural de la pista, reportado por el evento END_FILE(EOF) de mpv (autoritativo).
        scope.launch {
            mpvPlayer.ended.collect {
                if (!endNotified) {
                    endNotified = true
                    _playbackState.value = PlaybackState.ENDED
                }
            }
        }
    }

    private fun loadSavedVolume() {
        val savedVolume = runBlocking {
            userPreferences.readSavedVolume()
        }
        _volume.value = savedVolume
        _previousVolume = savedVolume

        if (!isMpvDisabled) {
            mpvPlayer.volume = savedVolume.toFloat() / 100f
        }
    }

    /**
     * Carga e inicia la reproducción de la URL de audio especificada.
     *
     * Reinicia la posición y duración de reproducción. Si la carga falla, el estado de
     * reproducción se establece en ERROR y el error se registra en el log.
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
            // openUri es no bloqueante (lanza su propio job de IO) y prepara el resultado de
            // carga sincrónicamente, por lo que un awaitPlaybackStarted() posterior observa ESTA
            // carga, no un resultado obsoleto de la pista anterior.
            mpvPlayer.openUri(url)
        } catch (e: Exception) {
            _playbackState.value = PlaybackState.ERROR
            log.severe("Error al reproducir: ${e.message}")
        }
    }

    /**
     * Espera a que la reproducción inicie dentro de un tiempo de espera especificado.
     *
     * @return `true` si la reproducción inició dentro del tiempo de espera, `false` en caso contrario.
     */
    suspend fun awaitPlaybackStarted(timeoutMs: Long = 6000): Boolean {
        if (isMpvDisabled) return false
        return mpvPlayer.awaitPlaybackStarted(timeoutMs)
    }

    /**
     * Pausa la reproducción y actualiza el estado a `PAUSED`.
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
        // Enviar valores a mpv
        mpvPlayer.setEqualizer(bands)
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        if (isMpvDisabled) return
        mpvPlayer.setGaplessAudio(enabled)
    }

    fun setPlaybackSpeed(value: Float) {
        if (isMpvDisabled) return
        mpvPlayer.setSpeed(value)
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
                        // Solo progreso (el fin de la pista ahora viene del evento END_FILE de mpv,
                        // no de una heurística pos≈dur). 250ms mantiene la barra de búsqueda suave.
                        _duration.value = mpvPlayer.getDuration()
                        _position.value = mpvPlayer.getCurrentPosition()

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
                    // Captura silenciosa para el ticker en segundo plano
                }
                delay(1000.milliseconds)
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
            } catch (e: Throwable) { /* ignorar */ }
        }
    }
}
