package com.example.melodist.player

import com.example.melodist.data.repository.AudioQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

/**
 * Last-resort stream resolver that shells out to **yt-dlp** to obtain a direct audio URL.
 *
 * Melodist's in-process pipeline (WEB_REMIX + EJS sig/n + JCEF poToken) covers normal videos,
 * but some "hard" videos 403 on every client we can build (they need yt-dlp's encrypted
 * web_embedded flow). yt-dlp handles those, so we fall back to it when mpv fails to actually
 * start playing the resolved stream.
 *
 * Resolution is invoked only on playback failure, so its ~1-3s startup cost never affects the
 * common (working) path.
 */
object YtDlpResolver {
    private val log = Logger.getLogger("YtDlpResolver")

    // Videos that the in-process pipeline couldn't play this session (all clients 403). Remembering
    // them lets the caller skip the slow in-process + mpv-failure cycle on repeat plays and go
    // straight to yt-dlp. Session-only (cleared on restart, in case YouTube/clients change).
    private val knownYtDlpOnly = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun markNeedsYtDlp(videoId: String) { knownYtDlpOnly.add(videoId) }
    fun needsYtDlp(videoId: String): Boolean = knownYtDlpOnly.contains(videoId)

    /**
     * Returns a yt-dlp format selector string for the specified audio quality.
     *
     * @param quality The desired audio quality level.
     * @return A yt-dlp `-f` format selector string.
     */
    private fun formatSelector(quality: AudioQuality): String = when (quality) {
        AudioQuality.LOW -> "worstaudio/bestaudio[abr<=70]/bestaudio"
        AudioQuality.NORMAL -> "bestaudio[abr<=128]/bestaudio"
        AudioQuality.HIGH -> "bestaudio"
    }

    /**
     * Resolves a direct audio stream URL for the given video ID.
     *
     * Requires yt-dlp to be available either as a bundled executable or on the system PATH.
     *
     * @return A direct audio stream URL, or `null` if yt-dlp is unavailable or resolution fails.
     */
    suspend fun resolveAudioUrl(
        videoId: String,
        quality: AudioQuality = AudioQuality.NORMAL,
    ): String? = withContext(Dispatchers.IO) {
        val exe = ytDlpPath ?: run {
            log.warning("yt-dlp not found (not bundled and not on PATH); cannot fall back")
            return@withContext null
        }
        val watchUrl = "https://music.youtube.com/watch?v=$videoId"
        try {
            log.info("yt-dlp fallback: resolving $videoId (quality=$quality) via $exe")
            val args = buildList {
                add(exe); add("--no-warnings"); add("--no-playlist")
                add("-f"); add(formatSelector(quality))
                // Pass the signed-in cookie so age-restricted / login-required videos resolve. It must
                // be a Netscape cookies.txt file: a header-cookie is scoped only to the download host,
                // not to the youtube.com API calls yt-dlp makes during extraction.
                cookiesFile()?.let { add("--cookies"); add(it.absolutePath) }
                add("-g"); add(watchUrl)
            }
            val proc = ProcessBuilder(args).start()

            val stdout = proc.inputStream.bufferedReader().readText()
            val stderr = proc.errorStream.bufferedReader().readText()
            if (!proc.waitFor(45, TimeUnit.SECONDS)) {
                proc.destroyForcibly()
                log.warning("yt-dlp timed out for $videoId")
                return@withContext null
            }
            val url = stdout.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("http") }
            if (url == null) {
                log.warning("yt-dlp returned no URL for $videoId: ${stderr.take(300)}")
            } else {
                log.info("yt-dlp fallback resolved $videoId (urlLen=${url.length})")
            }
            url
        } catch (e: Exception) {
            log.warning("yt-dlp invocation failed for $videoId: ${e.message}")
            null
        }
    }

    private var cachedCookiesFile: File? = null
    private var cachedCookieValue: String? = null

    /**
     * Writes the signed-in cookie to a Netscape `cookies.txt` (the format yt-dlp applies to the
     * youtube.com extraction calls), for both `.youtube.com` and `.google.com`. Cached until the
     * cookie changes. Returns null when not logged in.
     */
    private fun cookiesFile(): File? {
        val cookie = com.metrolist.innertube.YouTube.cookie?.takeIf { it.isNotBlank() } ?: return null
        cachedCookiesFile?.let { if (cachedCookieValue == cookie && it.exists()) return it }
        return try {
            val dir = File(System.getProperty("user.home"), ".melodist").apply { mkdirs() }
            val file = File(dir, "ytdlp-cookies.txt")
            val sb = StringBuilder("# Netscape HTTP Cookie File\n")
            cookie.split(';').forEach { pair ->
                val idx = pair.indexOf('=')
                if (idx <= 0) return@forEach
                val name = pair.substring(0, idx).trim()
                val value = pair.substring(idx + 1).trim()
                if (name.isEmpty()) return@forEach
                for (domain in listOf(".youtube.com", ".google.com")) {
                    // domain \t includeSubdomains \t path \t secure \t expiry \t name \t value
                    sb.append(domain).append("\tTRUE\t/\tTRUE\t2147483647\t").append(name).append('\t').append(value).append('\n')
                }
            }
            file.writeText(sb.toString())
            cachedCookiesFile = file
            cachedCookieValue = cookie
            file
        } catch (e: Exception) {
            log.warning("Failed to write yt-dlp cookies file: ${e.message}")
            null
        }
    }

    val isAvailable: Boolean get() = ytDlpPath != null

    /** Bundled binary (shipped next to libmpv) takes precedence over a system PATH install. */
    private val ytDlpPath: String? by lazy { locateYtDlp() }

    /**
     * Determines the absolute path to the yt-dlp executable.
     * 
     * Prefers bundled executables in known resource directories, then falls back 
     * to a system-wide lookup.
     *
     * @return The absolute path to yt-dlp, or `null` if not found.
     */
    private fun locateYtDlp(): String? {
        val userDir = File(System.getProperty("user.dir"))
        val rootDir = userDir.parentFile ?: userDir
        val resProp = System.getProperty("compose.application.resources.dir")
        val candidates = buildList {
            resProp?.let { add(File(it, "yt-dlp.exe")); add(File(File(it, "windows"), "yt-dlp.exe")) }
            add(File(userDir, "resources/yt-dlp.exe"))
            add(File(userDir, "mpv-resources/windows/yt-dlp.exe"))
            add(File(rootDir, "mpv-resources/windows/yt-dlp.exe"))
        }
        candidates.firstOrNull { it.exists() }?.let {
            log.info("Using bundled yt-dlp: ${it.absolutePath}")
            return it.absolutePath
        }
        // Fall back to a system install, resolving its absolute path via where/which.
        return resolveOnPath("yt-dlp")?.also { log.info("Using system yt-dlp: $it") }
    }

    /**
     * Locates an executable on the system PATH.
     *
     * @param name The name of the executable to locate.
     * @return The absolute path to the executable if found, `null` otherwise.
     */
    private fun resolveOnPath(name: String): String? = try {
        val which = if (System.getProperty("os.name").startsWith("Windows", true)) "where" else "which"
        val proc = ProcessBuilder(which, name).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText()
        if (proc.waitFor(5, TimeUnit.SECONDS) && proc.exitValue() == 0) {
            out.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
        } else null
    } catch (e: Exception) {
        // `where`/`which` unavailable — assume not present.
        null
    }
}
