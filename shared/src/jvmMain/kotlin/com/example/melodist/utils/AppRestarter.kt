package com.example.melodist.utils

import com.example.melodist.data.repository.JvmConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess

object AppRestarter {

    val requiredJvmArgs = listOf(
        "--add-modules=java.sql",
        "--enable-native-access=ALL-UNNAMED",
        "-Dorg.sqlite.tmpdir=${System.getProperty("user.home")}/.melodist/tmp",
        "-XX:+UseStringDeduplication",
        "-XX:+UseCompressedOops",
        "-XX:MaxHeapFreeRatio=30",
        "-XX:MinHeapFreeRatio=10",
    )

    val gcTuningArgs = listOf(
        "-XX:MaxGCPauseMillis=80",
    )

    fun previewJvmArgs(config: JvmConfig): List<String> =
        requiredJvmArgs + gcTuningArgs + config.toJvmArgs()

    suspend fun restartWithJvmArgs(config: JvmConfig) {
        withContext(Dispatchers.IO) {
            try {
                val javaHome = System.getProperty("java.home")
                val javaBin = if (isWindows()) "$javaHome\\bin\\java.exe" else "$javaHome/bin/java"
                val classpath = System.getProperty("java.class.path")
                val mainClass = System.getProperty("sun.java.command")?.split(" ")?.firstOrNull()
                    ?: "com.example.melodist.MainKt"

                val command = mutableListOf<String>()
                command.add(javaBin)
                command.addAll(previewJvmArgs(config))
                command.add("-cp")
                command.add(classpath)
                command.add(mainClass)

                val processBuilder = ProcessBuilder(command)
                processBuilder.inheritIO()
                processBuilder.start()

                exitProcess(0)
            } catch (e: Exception) {
                e.printStackTrace()
                exitProcess(0)
            }
        }
    }

    private fun isWindows(): Boolean {
        return System.getProperty("os.name").lowercase().contains("win")
    }
}
