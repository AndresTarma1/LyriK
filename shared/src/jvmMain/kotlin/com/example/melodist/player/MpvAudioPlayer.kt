package com.example.melodist.player

import com.sun.jna.Pointer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.logging.Logger

class MpvAudioPlayer {
    private val log = Logger.getLogger("MpvAudioPlayer")
    private var handle: Pointer? = null
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var openUriJob: Job? = null

    // Result of the most recent openUri load: true once the NEW file is actually progressing,
    // false if it failed to open (e.g. 403). Resolved by the openUri coroutine itself so the
    // check keys off the new load, not the previous track's lingering state.
    @Volatile
    private var loadResult: CompletableDeferred<Boolean> = CompletableDeferred<Boolean>().apply { complete(false) }

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
            }
        } catch (e: Exception) {
            log.severe("MpvAudioPlayer init failed: ${e.message}")
            throw e
        }
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
            // Unblock any pending await from a superseded load, then arm a fresh result.
            loadResult.takeIf { !it.isCompleted }?.complete(false)
            val result = CompletableDeferred<Boolean>()
            loadResult = result
            openUriJob = scope.launch {

                withContext(Dispatchers.IO){
                    // Use the dedicated `user-agent` property: putting User-Agent inside
                    // `http-header-fields` does NOT override ffmpeg's default "Lavf/..." UA,
                    // which googlevideo rejects with 403.
                    MpvLib.INSTANCE.mpv_set_property_string(h, "user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0")
                    MpvLib.INSTANCE.mpv_set_property_string(h, "referrer", "https://music.youtube.com")
                    MpvLib.INSTANCE.mpv_command(h, arrayOf("loadfile", uri, "replace", null))
                }

                MpvLib.INSTANCE.mpv_set_property_string(h, "pause", "no")
                // Don't flip _isPlaying optimistically: the position ticker turns LOADING into
                // PLAYING whenever isPlaying is true, which would hide the loading state (and lie
                // about playback) when the load actually fails. Reflect the real result instead.
                _isPlaying.value = false

                val started = detectPlaybackStarted(uri)
                _isPlaying.value = started
                if (!result.isCompleted) result.complete(started)
            }
        }
    }

    /**
     * Determines whether playback of the specified URI has started in mpv.
     *
     * Verifies that mpv is playing the requested file and that audio has begun playback by polling
     * mpv state up to the specified timeout.
     *
     * @param uri The URI to verify.
     * @param timeoutMs The maximum time in milliseconds to wait for playback to start.
     * @return `true` if playback started, `false` if the file failed to load or the timeout was exceeded.
     */
    private suspend fun detectPlaybackStarted(uri: String, timeoutMs: Long = 8000): Boolean {
        val streamId = Regex("[?&]id=([^&]+)").find(uri)?.groupValues?.get(1)
        fun isCurrentFile(): Boolean {
            val path = getMpvPropertyString("path") ?: return false
            if (streamId != null) return path.contains(streamId)
            /**
 * Normalizes a path by converting backslashes to forward slashes and removing trailing slashes.
 *
 * @return The normalized path.
 */
            fun norm(s: String) = s.replace('\\', '/').trimEnd('/')
            return norm(path).equals(norm(uri), ignoreCase = true) ||
                norm(path).endsWith(norm(uri).substringAfterLast('/'))
        }

        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            delay(150)
            handle ?: return false
            val idle = getMpvPropertyString("idle-active") == "yes"
            // Idle after we committed to loading => our file failed/ended (404/403/EOF).
            if (idle && System.currentTimeMillis() - start > 600) return false
            if (isCurrentFile()) {
                val pos = getMpvPropertyString("time-pos")?.toDoubleOrNull() ?: -1.0
                val cache = getMpvPropertyString("demuxer-cache-time")?.toDoubleOrNull() ?: -1.0
                if (pos > 0.3 || cache > 0.3) return true
            }
        }
        return isCurrentFile() && (getMpvPropertyString("time-pos")?.toDoubleOrNull() ?: 0.0) > 0.3
    }

    /**
     * Determines whether the most recent URI load successfully started playback.
     *
     * @return `true` if playback started, `false` if it failed or the wait timed out.
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

//    fun logMemoryStats() {
//        val cacheState = getMpvPropertyString("demuxer-cache-state")
//        val audioOut = getMpvPropertyString("audio-out-params")
//        val filterStats = getMpvPropertyString("filter-stats")
//        val af = getMpvPropertyString("af")
//        log.info("=== mpv memory ===")
//        log.info("  demuxer-cache: $cacheState")
//        log.info("  audio-out: $audioOut")
//        log.info("  filter-stats: $filterStats")
//        log.info("  af: $af")
//    }

    fun dispose() {
        openUriJob?.cancel()
        handle?.let {
            MpvLib.INSTANCE.mpv_terminate_destroy(it)
            handle = null
        }
        scope.cancel()
    }
}
