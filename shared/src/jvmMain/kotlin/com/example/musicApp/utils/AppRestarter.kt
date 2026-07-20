package com.example.musicApp.utils

import com.example.musicApp.data.repository.JvmConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess

object AppRestarter {

    private const val appliedMarkerArg = "-Dmelodist.jvmConfigApplied=true"

    val requiredJvmArgs = listOf(
        "--add-modules=java.sql",
        "--enable-native-access=ALL-UNNAMED",
        "-Dorg.sqlite.tmpdir=${System.getProperty("user.home")}/.melodist/tmp",
        "-XX:+UseStringDeduplication",
        "-XX:+UseCompressedOops",
        "-XX:MaxHeapFreeRatio=30",
        "-XX:MinHeapFreeRatio=10",
        "-XX:G1PeriodicGCInterval=10000",
        "-XX:G1PeriodicGCSystemLoadThreshold=0.0",
    )

    val gcTuningArgs = listOf(
        "-XX:MaxGCPauseMillis=80",
    )

    fun previewJvmArgs(config: JvmConfig): List<String> =
        requiredJvmArgs + gcTuningArgs + config.toJvmArgs() + appliedMarkerArg

    suspend fun restartWithJvmArgs(config: JvmConfig) {
        withContext(Dispatchers.IO) {
            try {
                // En una compilación instalada (jpackage), `jpackage.app-path` es el ejecutable
                // nativo del lanzador. Relanzar ESTE relee el .cfg de la aplicación (runtime
                // empaquetado, directorios de recursos, opciones de módulo) y, al iniciar,
                // JvmConfigLauncher.applySync() re-aplica el render guardado desde disco — así que
                // no es necesario pasar argumentos JVM por aquí. La ruta anterior
                // `java -cp <classpath> MainKt` descartaba todas las opciones de lanzamiento de
                // jpackage y el JVM nuevo moría de inmediato, lo que parecía "la app simplemente
                // se cierra y nunca vuelve a abrir". Solo se recurre al relanzamiento con java
                // cuando se ejecuta sin empaquetar (dev/gradle).
                val appPath = System.getProperty("jpackage.app-path")
                val command: List<String> = if (!appPath.isNullOrBlank()) {
                    listOf(appPath)
                } else {
                    val javaHome = System.getProperty("java.home")
                    val javaBin = if (isWindows()) "$javaHome\\bin\\java.exe" else "$javaHome/bin/java"
                    val classpath = System.getProperty("java.class.path")
                    val mainClass = System.getProperty("sun.java.command")?.split(" ")?.firstOrNull()
                        ?: "com.example.melodist.MainKt"
                    listOf(javaBin) + previewJvmArgs(config) + listOf("-cp", classpath, mainClass)
                }

                ProcessBuilder(command)
                    .directory(java.io.File(System.getProperty("user.dir")))
                    .start()

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
