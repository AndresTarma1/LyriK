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

    /** Helper: llama a mpv_get_property_string y libera la memoria nativa con mpv_free. */
    private fun getMpvPropertyString(name: String): String? {
        val ptr = handle?.let { MpvLib.INSTANCE.mpv_get_property_string(it, name) } ?: return null
        return try {
            ptr.getString(0L)
        } finally {
            MpvLib.INSTANCE.mpv_free(ptr)
        }
    }

    fun init() {
        if (handle != null) return
        try {
            handle = MpvLib.INSTANCE.mpv_create()
            handle?.let {
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
    fun openUri(uri: String) {
        handle?.let { h ->
            openUriJob?.cancel()
            openUriJob = scope.launch {

                withContext(Dispatchers.IO){
                    MpvLib.INSTANCE.mpv_command(h, arrayOf("loadfile", uri, "replace", null))
                }

                MpvLib.INSTANCE.mpv_set_property_string(h, "pause", "no")
                _isPlaying.value = true
            }
        }
    }

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
