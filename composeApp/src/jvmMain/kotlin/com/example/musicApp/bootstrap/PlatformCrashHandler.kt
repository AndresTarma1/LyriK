package com.example.musicApp.bootstrap

import com.example.musicApp.data.AppDirs
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime

object PlatformCrashHandler {

    fun register() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            logStartupError("Uncaught exception on thread '${thread.name}'", throwable)
        }
    }

    fun logStartupError(context: String, throwable: Throwable) {
        runCatching {
            val logsDir = AppDirs.logsDir.also { if (!it.exists()) it.mkdirs() }
            val logFile = File(logsDir, "startup.log")
            val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
            val entry = buildString {
                appendLine("[${LocalDateTime.now()}] $context")
                appendLine(stackTrace)
                appendLine("------------------------------------------------------------")
            }
            logFile.appendText(entry)
        }
    }

    inline fun <T> runSafely(context: String, block: () -> T): T {
        return try {
            block()
        } catch (e: Throwable) {
            logStartupError(context, e)
            throw e
        }
    }
}

