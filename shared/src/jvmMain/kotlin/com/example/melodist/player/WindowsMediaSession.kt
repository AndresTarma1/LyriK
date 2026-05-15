package com.example.melodist.player

import dev.toastbits.mediasession.MediaSession
import dev.toastbits.mediasession.MediaSessionMetadata
import dev.toastbits.mediasession.MediaSessionPlaybackStatus
import java.util.logging.Logger

/**
 * Envoltorio ligero sobre `dev.toastbits:mediasession` para exponer una API sencilla a
 * `PlayerViewModel` y permitir que Windows reconozca a Melodist como reproductor multimedia.
 */
class WindowsMediaSession {

    private val log = Logger.getLogger("WindowsMediaSession")
    private var session: MediaSession? = null
    private var positionProvider: () -> Long = { 0L }

    private var onPlay: (() -> Unit)? = null
    private var onPause: (() -> Unit)? = null
    private var onNext: (() -> Unit)? = null
    private var onPrevious: (() -> Unit)? = null
    private var onStop: (() -> Unit)? = null

    fun setCallbacks(
        onPlay: () -> Unit,
        onPause: () -> Unit,
        onNext: () -> Unit,
        onPrevious: () -> Unit,
        onStop: () -> Unit
    ) {
        this.onPlay = onPlay
        this.onPause = onPause
        this.onNext = onNext
        this.onPrevious = onPrevious
        this.onStop = {
            onStop()
        }
        session?.let { attachCallbacks(it) }
    }

    fun setPositionProvider(provider: () -> Long) {
        positionProvider = provider
    }

    fun initialize() {
        if (session != null) return

        session = MediaSession.create(
            getPositionMs = { positionProvider() }
        ).also {

            if (it == null) {
                log.warning("No se pudo crear MediaSession: plataforma no soportada o error desconocido")
                return@also
            }
            attachCallbacks(it)

            it.setIdentity("Melodist.MusicPlayer")
            it.setDesktopEntry("melodist")
            it.setSupportedUriSchemes(listOf("file", "http", "https"))
            it.setSupportedMimeTypes(listOf("audio/mpeg", "audio/x-m4a", "audio/ogg", "audio/webm"))
            it.setEnabled(false)
        }
    }

    private fun attachCallbacks(session: MediaSession) {
        session.onPlay = { onPlay?.invoke() }
        session.onPause = { onPause?.invoke() }
        session.onNext = { onNext?.invoke() }
        session.onPrevious = { onPrevious?.invoke() }
        session.onStop = { onStop?.invoke() }
    }

    fun updateMetadata(title: String, artist: String, album: String, thumbnailUrl: String? = null) {
        val s = session ?: return

        if (title.isNotBlank() && title != "Melodist") {
            s.setEnabled(true)
        }

        session?.setMetadata(
            MediaSessionMetadata(
                title = title.ifBlank { "Melodist" },
                artist = artist.ifBlank { "Artista desconocido" },
                album = album,
                art_url = thumbnailUrl
            )
        )
    }

    fun setPlaybackStatus(isPlaying: Boolean, isPaused: Boolean) {
        val s = session ?: return

        if (isPlaying || isPaused) {
            s.setEnabled(true)
        }

        val status = when {
            isPlaying -> MediaSessionPlaybackStatus.PLAYING
            isPaused -> MediaSessionPlaybackStatus.PAUSED
            else -> MediaSessionPlaybackStatus.STOPPED
        }
        s.setPlaybackStatus(status)
    }

    fun resetToIdle() {
        updateMetadata(title = "Melodist", artist = "", album = "", thumbnailUrl = null)
        setPlaybackStatus(isPlaying = false, isPaused = false)
        updateMetadata(title = "", artist = "", album = "")
        session?.setEnabled(false) // Oculta el panel de Windows por completo
    }

    fun release() {
        session?.setEnabled(false)
    }
}