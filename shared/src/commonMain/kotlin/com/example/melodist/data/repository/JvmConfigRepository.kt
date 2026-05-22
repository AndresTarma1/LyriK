package com.example.melodist.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class RenderApi(
    val label: String,
    val description: String,
    val jvmArg: String?,
) {
    DIRECTX(
        label = "DirectX",
        description = "Renderer por defecto en Windows. Usa aceleracion por GPU y suele ser el mejor equilibrio.",
        jvmArg = null,
    ),
    OPENGL(
        label = "OpenGL",
        description = "Renderer alternativo por GPU. Puede ayudar en algunos equipos, pero aqui puede fallar con imagenes circulares.",
        jvmArg = "-Dskiko.renderApi=OPENGL",
    ),
    SOFTWARE(
        label = "Software",
        description = "Renderiza por CPU. En pruebas consume menos memoria nativa y evito errores visuales a cambio de consumo de CPU.",
        jvmArg = "-Dskiko.renderApi=SOFTWARE",
    ),
    ANGLE(
        label = "ANGLE",
        description = "Renderer que traduce llamadas OpenGL a DirectX. Puede ser útil en equipos con drivers OpenGL problemáticos, pero no es oficialmente soportado en Skia.",
        jvmArg = "-Dskiko.renderApi=ANGLE",
    )
}

data class JvmConfig(
    val xmx: String = "512m",
    val xms: String = "64m",
    val useG1GC: Boolean = true,
    val useZGC: Boolean = false,
    val gcLogging: Boolean = false,
    val renderApi: RenderApi = RenderApi.DIRECTX,
) {
    companion object {
        fun defaults() = JvmConfig()
    }

    fun validate(): JvmValidationResult {
        if (!isValidMemory(xmx)) return JvmValidationResult.InvalidXmx
        if (!isValidMemory(xms)) return JvmValidationResult.InvalidXms
        if (memoryToMb(xms) < 64) return JvmValidationResult.XmsTooLow
        if (useG1GC && useZGC) return JvmValidationResult.IncompatibleGC
        return JvmValidationResult.Valid
    }

    fun toJvmArgs(): List<String> {
        val args = mutableListOf<String>()
        args.add("-Xms$xms")
        args.add("-Xmx$xmx")
        if (useG1GC) args.add("-XX:+UseG1GC")
        if (useZGC) args.add("-XX:+UseZGC")
        if (gcLogging) args.add("-Xlog:gc")
        renderApi.jvmArg?.let(args::add)
        return args
    }

    private fun isValidMemory(value: String): Boolean {
        val regex = Regex("""^\d+[mgMG]$""")
        return regex.matches(value) && value.dropLast(1).toIntOrNull() != null
    }

    private fun memoryToMb(value: String): Int {
        val amount = value.dropLast(1).toIntOrNull() ?: return 0
        return if (value.last().lowercaseChar() == 'g') amount * 1024 else amount
    }
}

sealed class JvmValidationResult {
    object Valid : JvmValidationResult()
    object InvalidXmx : JvmValidationResult()
    object InvalidXms : JvmValidationResult()
    object XmsTooLow : JvmValidationResult()
    object IncompatibleGC : JvmValidationResult()

    val errorMessage: String?
        get() = when (this) {
            is Valid -> null
            is InvalidXmx -> "Memoria maxima invalida. Usa formato como 512m o 1g"
            is InvalidXms -> "Memoria inicial invalida. Usa formato como 64m o 1g"
            is XmsTooLow -> "Memoria inicial invalida. El minimo es 64m"
            is IncompatibleGC -> "G1GC y ZGC no pueden activarse simultaneamente"
        }
}

data class JvmRuntimeInfo(
    val usedMemory: Long,
    val freeMemory: Long,
    val maxMemory: Long,
    val processorCount: Int,
    val jvmName: String,
    val javaVersion: String,
) {
    companion object {
        fun current(): JvmRuntimeInfo {
            val runtime = Runtime.getRuntime()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            return JvmRuntimeInfo(
                usedMemory = totalMemory - freeMemory,
                freeMemory = freeMemory,
                maxMemory = runtime.maxMemory(),
                processorCount = runtime.availableProcessors(),
                jvmName = System.getProperty("java.vm.name", "Unknown"),
                javaVersion = System.getProperty("java.version", "Unknown"),
            )
        }
    }
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> String.format("%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> String.format("%.0f MB", bytes / 1_048_576.0)
        bytes >= 1_024 -> String.format("%.0f KB", bytes / 1_024.0)
        else -> "$bytes B"
    }
}

class JvmConfigRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val XMX = stringPreferencesKey("jvm_xmx")
        val XMS = stringPreferencesKey("jvm_xms")
        val G1GC = booleanPreferencesKey("jvm_g1gc")
        val ZGC = booleanPreferencesKey("jvm_zgc")
        val GC_LOGGING = booleanPreferencesKey("jvm_gc_logging")
        val RENDER_API = stringPreferencesKey("jvm_render_api")
    }

    val config: Flow<JvmConfig> = dataStore.data.map { prefs ->
        JvmConfig(
            xmx = prefs[Keys.XMX] ?: JvmConfig.defaults().xmx,
            xms = prefs[Keys.XMS] ?: JvmConfig.defaults().xms,
            useG1GC = prefs[Keys.G1GC] ?: JvmConfig.defaults().useG1GC,
            useZGC = prefs[Keys.ZGC] ?: JvmConfig.defaults().useZGC,
            gcLogging = prefs[Keys.GC_LOGGING] ?: JvmConfig.defaults().gcLogging,
            renderApi = prefs[Keys.RENDER_API]?.let { stored ->
                runCatching { RenderApi.valueOf(stored) }.getOrNull()
            } ?: JvmConfig.defaults().renderApi,
        )
    }

    suspend fun updateConfig(config: JvmConfig) {
        dataStore.edit { prefs ->
            prefs[Keys.XMX] = config.xmx
            prefs[Keys.XMS] = config.xms
            prefs[Keys.G1GC] = config.useG1GC
            prefs[Keys.ZGC] = config.useZGC
            prefs[Keys.GC_LOGGING] = config.gcLogging
            prefs[Keys.RENDER_API] = config.renderApi.name
        }
    }

    suspend fun updateRenderApi(renderApi: RenderApi) {
        dataStore.edit { prefs ->
            prefs[Keys.RENDER_API] = renderApi.name
        }
    }

    suspend fun resetToDefaults() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.XMX)
            prefs.remove(Keys.XMS)
            prefs.remove(Keys.G1GC)
            prefs.remove(Keys.ZGC)
            prefs.remove(Keys.GC_LOGGING)
            prefs.remove(Keys.RENDER_API)
        }
    }
}
