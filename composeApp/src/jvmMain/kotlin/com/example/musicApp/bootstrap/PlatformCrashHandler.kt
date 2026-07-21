package com.example.musicApp.bootstrap

import com.example.musicApp.data.AppDirs
import com.example.musicApp.data.repository.CrashReportRepository
import com.example.musicApp.viewmodels.AppViewModel
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentLinkedDeque

object PlatformCrashHandler {

    private const val MAX_RECENT_LOGS = 50
    private val recentLogs = ConcurrentLinkedDeque<String>()

    fun register() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            logCrash(thread, throwable)
        }
    }

    fun addLog(message: String) {
        recentLogs.addLast("[${LocalDateTime.now()}] $message")
        while (recentLogs.size > MAX_RECENT_LOGS) {
            recentLogs.pollFirst()
        }
    }

    fun getRecentLogs(): List<String> = recentLogs.toList()

    private fun logCrash(thread: Thread, throwable: Throwable) {
        runCatching {
            val report = CrashReportRepository.buildCrashReport(
                thread = thread,
                throwable = throwable,
                appVersion = AppViewModel.CURRENT_VERSION,
                recentLogs = getRecentLogs(),
            )
            CrashReportRepository.saveCrashReport(report)

            val logsDir = AppDirs.logsDir.also { if (!it.exists()) it.mkdirs() }
            val logFile = File(logsDir, "startup.log")
            val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
            val entry = buildString {
                appendLine("[${LocalDateTime.now()}] Uncaught exception on thread '${thread.name}'")
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
}
