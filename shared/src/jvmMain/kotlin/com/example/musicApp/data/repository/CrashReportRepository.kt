package com.example.musicApp.data.repository

import com.example.musicApp.data.AppDirs
import io.github.aakira.napier.Napier
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Serializable
data class CrashReport(
    val timestamp: String,
    val appVersion: String,
    val os: String,
    val thread: String,
    val exception: String,
    val message: String,
    val stacktrace: String,
    val sent: Boolean = false,
)

object CrashReportRepository {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val crashDir: File by lazy {
        File(AppDirs.logsDir, "crash").apply { mkdirs() }
    }

    fun saveCrashReport(report: CrashReport) {
        runCatching {
            val fileName = "crash_${report.timestamp.replace(":", "-").replace(" ", "_")}.json"
            val file = File(crashDir, fileName)
            file.writeText(json.encodeToString(CrashReport.serializer(), report))
            Napier.i("Crash report saved: ${file.name}")
        }.onFailure { Napier.e("Failed to save crash report", it) }
    }

    fun getUnsentReports(): List<Pair<File, CrashReport>> {
        return runCatching {
            crashDir.listFiles()
                ?.filter { it.extension == "json" }
                ?.mapNotNull { file ->
                    runCatching {
                        val report = json.decodeFromString(CrashReport.serializer(), file.readText())
                        if (!report.sent) file to report else null
                    }.getOrNull()
                }
                ?.sortedByDescending { it.first.lastModified() }
                ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun getAllReports(): List<Pair<File, CrashReport>> {
        return runCatching {
            crashDir.listFiles()
                ?.filter { it.extension == "json" }
                ?.mapNotNull { file ->
                    runCatching {
                        file to json.decodeFromString(CrashReport.serializer(), file.readText())
                    }.getOrNull()
                }
                ?.sortedByDescending { it.first.lastModified() }
                ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun markAsSent(file: File) {
        runCatching {
            val report = json.decodeFromString(CrashReport.serializer(), file.readText())
            file.writeText(json.encodeToString(CrashReport.serializer(), report.copy(sent = true)))
        }
    }

    fun markAllAsSent() {
        getUnsentReports().forEach { (file, _) -> markAsSent(file) }
    }

    fun openCrashAsGitHubIssue(report: CrashReport) {
        runCatching {
            val title = "[Crash] ${report.exception} on ${report.appVersion}"
            val body = buildString {
                appendLine("## Crash Report")
                appendLine()
                appendLine("**Version:** ${report.appVersion}")
                appendLine("**OS:** ${report.os}")
                appendLine("**Thread:** ${report.thread}")
                appendLine("**Timestamp:** ${report.timestamp}")
                appendLine()
                appendLine("## Exception")
                appendLine("```")
                appendLine("${report.exception}: ${report.message}")
                appendLine("```")
                appendLine()
                appendLine("## Stacktrace")
                appendLine("```")
                appendLine(report.stacktrace)
                appendLine("```")
                appendLine()
                appendLine("## Steps to Reproduce")
                appendLine("1. ")
            }
            val url = "https://github.com/AndresTarma1/LyriK/issues/new" +
                "?title=${URLEncoder.encode(title, "UTF-8")}" +
                "&body=${URLEncoder.encode(body, "UTF-8")}"
            Desktop.getDesktop().browse(URI(url))
        }.onFailure { Napier.e("Failed to open GitHub issue", it) }
    }

    fun buildCrashReport(
        thread: Thread,
        throwable: Throwable,
        appVersion: String,
        recentLogs: List<String> = emptyList(),
    ): CrashReport {
        val os = "${System.getProperty("os.name")} ${System.getProperty("os.version")}"
        val stackTrace = throwable.stackTraceToString()
        val message = throwable.message ?: throwable.toString()
        val sw = java.io.StringWriter()
        throwable.printStackTrace(java.io.PrintWriter(sw))

        return CrashReport(
            timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            appVersion = appVersion,
            os = os,
            thread = thread.name,
            exception = throwable.javaClass.name,
            message = message,
            stacktrace = buildString {
                appendLine(sw.toString())
                if (recentLogs.isNotEmpty()) {
                    appendLine()
                    appendLine("--- Recent Logs ---")
                    recentLogs.forEach { appendLine(it) }
                }
            },
        )
    }
}
