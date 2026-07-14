package com.example.melodist.player

import com.example.melodist.platform.Platform
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

    // Ajustes de audio solicitados antes de que exista el handle nativo (init diferido/perezoso
    // de mpv) — se almacenan aquí y se reaplican en init() para no perder nada.
    private var lastEqualizer: List<Float>? = null
    private var lastGapless: Boolean? = null
    private var lastSpeed: Float? = null
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var openUriJob: Job? = null

    // Se emite cuando la pista actual termina naturalmente (mpv END_FILE con razón EOF).
    private val _ended = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val ended: SharedFlow<Unit> = _ended.asSharedFlow()

    // Resultado de la carga más reciente de openUri: completado por el bucle de eventos de mpv —
    // true en el primer PLAYBACK_RESTART (el nuevo archivo se está decodificando/reproduciendo),
    // false en END_FILE/ERROR (por ejemplo, un 403). Basado en eventos en lugar de sondeo, y
    // inmune al estado residual de la pista anterior.
    @Volatile
    private var loadResult: CompletableDeferred<Boolean> = CompletableDeferred<Boolean>().apply { complete(false) }

    @Volatile
    private var eventThread: Thread? = null
    @Volatile
    private var eventLoopRunning = false

    /**
     * Obtiene el valor de una propiedad de mpv como cadena.
     *
     * @param name El nombre de la propiedad de mpv a obtener.
     * @return El valor de la propiedad como cadena, o `null` si el handle no está inicializado o la propiedad no está disponible.
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
     * Inicializa la instancia de reproducción de audio de mpv con configuración solo de audio
     * (salida nativa WASAPI en Windows; el backend autodetectado por mpv en otros sistemas).
     *
     * Si la instancia de mpv ya está inicializada, este método retorna inmediatamente. De lo
     * contrario, crea una nueva instancia de mpv y la configura para reproducción de audio estéreo
     * con renderizado de video desactivado y parámetros de búfer optimizados.
     *
     * @throws Exception si la inicialización de mpv falla.
     */
    fun init() {
        if (handle != null) return
        try {
            handle = MpvLib.INSTANCE.mpv_create()
            handle?.let {
                // Siempre pasamos URLs de flujo directo completamente resueltas; no dejamos que mpv
                // invoque yt-dlp (su fallback ytdl_hook lanza yt-dlp al fallar la apertura,
                // añadiendo latencia/ruido).
                MpvLib.INSTANCE.mpv_set_property_string(it, "ytdl", "no")

                // Configuraciones críticas para estabilidad
                MpvLib.INSTANCE.mpv_set_property_string(it, "video", "no")
                MpvLib.INSTANCE.mpv_set_property_string(it, "audio-display", "no")
                MpvLib.INSTANCE.mpv_set_property_string(it, "audio-channels", "stereo")
                // WASAPI es solo para Windows — forzarlo en Linux/macOS falla silenciosamente al
                // abrir un dispositivo de audio (mpv_initialize tiene éxito, pero la reproducción
                // se bloquea esperando uno que nunca se abre, sin error aparente). En otros
                // sistemas, dejar "ao" sin configurar: la autodetección de mpv
                // (pipewire/pulse/alsa, o coreaudio en macOS) es más robusta que adivinar un backend.
                if (Platform.isWindows) {
                    MpvLib.INSTANCE.mpv_set_property_string(it, "ao", "wasapi")
                }

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
            // Reaplicar ajustes solicitados antes de que existiera el handle (init diferido/perezoso).
            lastEqualizer?.let { bands -> setEqualizer(bands) }
            lastGapless?.let { enabled -> setGaplessAudio(enabled) }
            lastSpeed?.let { speed -> setSpeed(speed) }
        } catch (e: Exception) {
            log.severe("MpvAudioPlayer init failed: ${e.message}")
            throw e
        }
    }

    /**
     * Hilo en segundo plano que drena la cola de eventos de mpv. Los eventos principales
     * (END_FILE, PLAYBACK_RESTART) se entregan sin observar ninguna propiedad, por lo que no
     * se necesita mpv_observe_property. Controla:
     *  - éxito/fallo de la carga ([loadResult]) — reemplaza la detección basada en sondeo,
     *  - la señal de fin natural de la pista ([ended]) — reemplaza la heurística pos≈dur,
     *  - el estado real de reproducción ([_isPlaying]).
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
                        // El nuevo archivo se está decodificando/reproduciendo (también se dispara
                        // después de búsquedas — inofensivo).
                        _isPlaying.value = true
                        loadResult.takeIf { !it.isCompleted }?.complete(true)
                    }

                    MpvLib.MPV_EVENT_END_FILE -> {
                        // datos en offset 16 -> mpv_event_end_file; la razón es su primer int.
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
                            // STOP/REDIRECT/QUIT: el archivo fue reemplazado o la app se está cerrando — ignorar.
                        }
                    }
                }
            }
        }, "mpv-events").apply { isDaemon = true; start() }
    }
    /**
     * Inicia la reproducción de la URI dada.
     *
     * Cancela cualquier operación de carga pendiente anterior. El resultado está disponible
     * mediante `awaitPlaybackStarted()`.
     *
     * @param uri La URI a cargar.
     */
    fun openUri(uri: String) {
        handle?.let { h ->
            openUriJob?.cancel()
            // Desbloquear cualquier await pendiente de una carga reemplazada, y preparar un nuevo
            // resultado que el bucle de eventos completará (PLAYBACK_RESTART => true,
            // END_FILE/ERROR => false).
            loadResult.takeIf { !it.isCompleted }?.complete(false)
            loadResult = CompletableDeferred()
            // Hasta que el nuevo archivo realmente inicie, reportar que no se reproduce para que la
            // UI se mantenga en LOADING en lugar de cambiar a PLAYING con el estado residual de la
            // pista anterior.
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
     * Se suspende hasta que se conozca que la carga más reciente de [openUri] ha iniciado
     * (true) o fallado (false). Resuelto por el bucle de eventos de mpv; [timeoutMs] protege
     * contra una carga que nunca produce un evento (por ejemplo, una conexión bloqueada).
     */
    suspend fun awaitPlaybackStarted(timeoutMs: Long = 10000): Boolean {
        val r = loadResult
        return withTimeoutOrNull(timeoutMs) { r.await() } ?: false
    }

    /**
     * Reanuda la reproducción.
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

    fun setSpeed(value: Float) {
        val clamped = value.coerceIn(0.25f, 3f)
        lastSpeed = clamped
        handle?.let {
            MpvLib.INSTANCE.mpv_set_property_string(it, "speed", clamped.toString())
        }
    }

    fun setGaplessAudio(enabled: Boolean) {
        lastGapless = enabled
        handle?.let {
            MpvLib.INSTANCE.mpv_set_property_string(it, "gapless-audio", if (enabled) "yes" else "no")
        }
    }

    fun setEqualizer(bands: List<Float>) {
        if (bands.size != 10) return
        lastEqualizer = bands
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
            MpvLib.INSTANCE.mpv_wakeup(it) // desbloquear el mpv_wait_event del hilo de eventos
            eventThread = null
            MpvLib.INSTANCE.mpv_terminate_destroy(it)
            handle = null
        }
        scope.cancel()
    }
}
