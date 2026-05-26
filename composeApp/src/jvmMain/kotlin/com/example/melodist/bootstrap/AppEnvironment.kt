package com.example.melodist.bootstrap

import com.example.melodist.data.AppDirs
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeLocale
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.time.LocalDateTime

object AppEnvironment {

    fun initialize() {
        AppDirs.ensureDirectories()
        val tmpDir = AppDirs.tmpDir.also { it.mkdirs() }

        System.setProperty("org.sqlite.tmpdir", tmpDir.absolutePath)
        System.setProperty("java.io.tmpdir", tmpDir.absolutePath)
        System.setProperty("compose.swing.render.on.graphics", "true")

        // redirectStandardStreams()
        configureYouTubeLocale()
    }

    private fun redirectStandardStreams() {
        try {
            val sysOutFile = File(AppDirs.logsDir, "sysout.log")
            val sysErrFile = File(AppDirs.logsDir, "syserr.log")
            System.setOut(PrintStream(FileOutputStream(sysOutFile, true), true))
            System.setErr(PrintStream(FileOutputStream(sysErrFile, true), true))
            System.out.println("[${LocalDateTime.now()}] --- Application Starting ---")
            System.err.println("[${LocalDateTime.now()}] --- Application Starting ---")
        } catch (_: Exception) {
            // Ignore logging setup failures to avoid blocking startup.
        }
    }

    private fun configureYouTubeLocale() {
        val sysLocale = java.util.Locale.getDefault()
        val rawCountry = sysLocale.country
        val rawLang = sysLocale.toLanguageTag()
        val safeGl = if (rawCountry.matches(Regex("^[a-zA-Z]{2}$"))) rawCountry.uppercase() else "US"
        val safeHl = if (rawLang.matches(Regex("^[a-zA-Z]{2}(-[a-zA-Z]{2})?$"))) rawLang else "en-US"
        YouTube.locale = YouTubeLocale(safeGl, safeHl)
    }
}

