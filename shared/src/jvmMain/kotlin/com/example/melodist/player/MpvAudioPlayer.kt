package com.example.melodist.player

import com.sun.jna.Pointer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.logging.Logger

class MpvAudioPlayer {
    private val log = Logger.getLogger("MpvAudioPlayer")
    private var handle: Pointer? = null
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var openUriJob: Job? = null

    // Emitted when the current track finishes naturally (mpv END_FILE with reason EOF).
    private val _ended = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val ended: SharedFlow<Unit> = _ended.asSharedFlow()

    // Result of the most recent openUri load: completed by the mpv event loop — true on the first
    // PLAYBACK_RESTART (the new file is decoding/playing), false on END_FILE/ERROR (e.g. a 403).
    // Event-driven instead of polling, and immune to the previous track's lingering state.
    @Volatile
    private var loadResult: CompletableDeferred<Boolean> = CompletableDeferred<Boolean>().apply { complete(false) }

    @Volatile
    private var eventThread: Thread? = null
    @Volatile
    private var eventLoopRunning = false

    /**
     * Retrieves an mpv property value as a string.
     *
     * @param name The name of the mpv property to retrieve.
     * @return The property value as a string, or `null` if the handle is not initialized or the property is unavailable.
     */
    private fun getMpvPropertyString(name: String): String? {
        val ptr = handle?.let { MpvLib.INSTANCE.mpv_get_property_string(it, name) } ?: return null
        return try {
            ptr.getString(0L)
        } finally {
            MpvLib.INSTANCE.mpv_free(ptr)
        }
    }

    /**
     * Initializes the mpv audio playback instance with audio-only configuration and Windows WASAPI output.
     *
     * If the mpv instance is already initialized, this method returns immediately. Otherwise, it creates
     * a new mpv instance and configures it for stereo audio playback with video rendering disabled,
     * native Windows audio output, and optimized buffering parameters.
     *
     * @throws Exception if mpv initialization fails.
     */
    fun init() {
        if (handle != null) return
        try {
            handle = MpvLib.INSTANCE.mpv_create()
            handle?.let {
                // We always pass fully-resolved direct stream URLs; don't let mpv invoke yt-dlp
                // (its ytdl_hook fallback spawns yt-dlp on open failure, adding latency/noise).
                MpvLib.INSTANCE.mpv_set_property_string(it, "ytdl", "no")

                // Configuraciones críticas para estabilidad
                MpvLib.INSTANCE.mpv_set_property_string(it, "video", "no")
                MpvLib.INSTANCE.mpv_set_property_string(it, "audio-display", "no")
                MpvLib.INSTANCE.mpv_set_property_string(it, "audio-channels", "stereo")
                MpvLib.INSTANCE.mpv_set_property_string(it, "ao", "wasapi") // Forzar salida nativa de Windows

                MpvLib.INSTANCE.mpv_set_property_string(it, "initial-audio-sync", "no")
                MpvLib.INSTANCE.mpv_set_property_string(it, "hr-seek", "no")

                MpvLib.INSTANCE.mpv_initialize(it)

// 3. Límites de memoria y control de búfer optimizado
                MpvLib.INSTANCE.mpv_set_property_string(it, "cache", "yes")
                MpvLib.INSTANCE.mpv_set_property_string(it, "cache-secs", "10") // Caché de tiempo corto para arrancar rápido
                MpvLib.INSTANCE.mpv_set_property_string(it, "demuxer-max-bytes", "10485760") // 10 MiB
                MpvLib.INSTANCE.mpv_set_property_string(it, "demuxer-max-back-bytes", "2097152") // 2 MiB

                MpvLib.INSTANCE.mpv_set_property_string(it, "cache-pause", "yes")

                startEventLoop(it)
            }
        } catch (e: Exception) {
            log.severe("MpvAudioPlayer init failed: ${e.message}")
            throw e
        }
    }

    /**
     * Background thread draining mpv's event queue. Core events (END_FILE, PLAYBACK_RESTART) are
     * delivered without observing any property, so this needs no mpv_observe_property. It drives:
     *  - load success/failure ([loadResult]) — replaces the old polling-based detection,
     *  - the natural end-of-track signal ([ended]) — replaces the heuristic pos≈dur check,
     *  - the real playing state ([_isPlaying]).
     */
    private fun startEventLoop(h: Pointer) {
        if (eventThread != null) return
        eventLoopRunning = true
        eventThread = Thread({
            while (eventLoopRunning) {
                val evPtr = try {
                    MpvLib.INSTANCE.mpv_wait_event(h, -1.0)
                } catch (e: Throwable) {
                    null
                } ?: continue
                when (evPtr.getInt(0)) { // event_id @ offset 0
                    MpvLib.MPV_EVENT_SHUTDOWN -> eventLoopRunning = false

                    MpvLib.MPV_EVENT_PLAYBACK_RESTART -> {
                        // New file is decoding/playing (also fires after seeks — harmless).
                        _isPlaying.value = true
                        loadResult.takeIf { !it.isCompleted }?.complete(true)
                    }

                    MpvLib.MPV_EVENT_END_FILE -> {
                        // data @ offset 16 -> mpv_event_end_file; reason is its first int.
                        val reason = evPtr.getPointer(16)?.getInt(0) ?: -1
                        when (reason) {
                            MpvLib.MPV_END_FILE_REASON_ERROR -> {
                                _isPlaying.value = false
                                loadResult.takeIf { !it.isCompleted }?.complete(false)
                            }
                            MpvLib.MPV_END_FILE_REASON_EOF -> {
                                _isPlaying.value = false
                                loadResult.takeIf { !it.isCompleted }?.complete(true)
                                _ended.tryEmit(Unit)
                            }
                            // STOP/REDIRECT/QUIT: file was replaced or app closing — ignore.
                        }
                    }
                }
            }
        }, "mpv-events").apply { isDaemon = true; start() }
    }
    /**
     * Initiates playback of the given URI.
     *
     * Cancels any previously pending load operation. The outcome is available via `awaitPlaybackStarted()`.
     *
     * @param uri The URI to load.
     */
    fun openUri(uri: String) {
        handle?.let { h ->
            openUriJob?.cancel()
            // Unblock any pending await from a superseded load, then arm a fresh result that the
            // event loop will complete (PLAYBACK_RESTART => true, END_FILE/ERROR => false).
            loadResult.takeIf { !it.isCompleted }?.complete(false)
            loadResult = CompletableDeferred()
            // Until the new file actually starts, report not-playing so the UI stays in LOADING
            // rather than flipping to PLAYING on the previous track's lingering state.
            _isPlaying.value = false
            openUriJob = scope.launch {
                withContext(Dispatchers.IO) {
                    MpvLib.INSTANCE.mpv_set_property_string(h, "user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0")
                    MpvLib.INSTANCE.mpv_set_property_string(h, "referrer", "https://music.youtube.com")
                    MpvLib.INSTANCE.mpv_command(h, arrayOf("loadfile", uri, "replace", null))
                    MpvLib.INSTANCE.mpv_set_property_string(h, "pause", "no")
                }
            }
        }
    }

    /**
     * Suspends until the most recent [openUri] load is known to have started (true) or failed
     * (false). Resolved by the mpv event loop; [timeoutMs] guards against a load that never
     * produces an event (e.g. a stuck connection).
     */
    suspend fun awaitPlaybackStarted(timeoutMs: Long = 10000): Boolean {
        val r = loadResult
        return withTimeoutOrNull(timeoutMs) { r.await() } ?: false
    }

    /**
     * Resumes playback.
     */
    fun play() {
        handle?.let {
            MpvLib.INSTANCE.mpv_set_property_string(it, "pause", "no")
            _isPlaying.value = true
        }
    }

    fun pause() {
        handle?.let {
            MpvLib.INSTANCE.mpv_set_property_string(it, "pause", "yes")
            _isPlaying.value = false
        }
    }

    fun stop() {
        handle?.let {
            MpvLib.INSTANCE.mpv_command(it, arrayOf("stop", null))
            _isPlaying.value = false
        }
    }

    fun seekTo(percent: Float) {
        handle?.let {
            val position = (percent * 100).coerceIn(0f, 100f)
            MpvLib.INSTANCE.mpv_command(it, arrayOf("seek", "$position", "absolute-percent", null))
        }
    }

    var volume: Float
        get() {
            return getMpvPropertyString("volume")?.toFloatOrNull() ?: 100f
        }
        set(value) {
            handle?.let {
                val vol = (value * 100).toInt().coerceIn(0, 100)
                MpvLib.INSTANCE.mpv_set_property_string(it, "volume", "$vol")
            }
        }

    fun setGaplessAudio(enabled: Boolean) {
        handle?.let {
            MpvLib.INSTANCE.mpv_set_property_string(it, "gapless-audio", if (enabled) "yes" else "no")
        }
    }

    fun setEqualizer(bands: List<Float>) {
        if (bands.size != 10) return
        handle?.let { mpv ->
            // En MPV, el filtro equalizer necesita la ganancia en formato:
            // f=frecuencia:width=ancho:g=ganancia
            // Es más simple usar firequalizer con ganancia:
            // af="lavfi=[firequalizer=gain='cubic_interpolate(f)':gain_entry='entry(32,gain1);entry(64,gain2)...']"

            val freqs = listOf(32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
            val entries = bands.mapIndexed { index, gain ->
                "entry(${freqs[index]},$gain)"
            }.joinToString(";")

            // Si todos son cero, limpiamos el filtro.
            if (bands.all { it == 0f }) {
                MpvLib.INSTANCE.mpv_set_property_string(mpv, "af", "")
            } else {
                val filter = "lavfi=[firequalizer=gain='cubic_interpolate(f)':gain_entry='$entries']"
                MpvLib.INSTANCE.mpv_set_property_string(mpv, "af", filter)
            }
        }
    }

    fun getDuration(): Long {
        val durStr = getMpvPropertyString("duration") ?: "0"
        return ((durStr.toDoubleOrNull() ?: 0.0) * 1000).toLong()
    }

    fun getCurrentPosition(): Long {
        val posStr = getMpvPropertyString("time-pos") ?: "0"
        return ((posStr.toDoubleOrNull() ?: 0.0) * 1000).toLong()
    }

    fun dispose() {
        openUriJob?.cancel()
        eventLoopRunning = false
        handle?.let {
            MpvLib.INSTANCE.mpv_wakeup(it) // unblock the event thread's mpv_wait_event
            eventThread?.join(1000)
            eventThread = null
            MpvLib.INSTANCE.mpv_terminate_destroy(it)
            handle = null
        }
        scope.cancel()
    }
}
