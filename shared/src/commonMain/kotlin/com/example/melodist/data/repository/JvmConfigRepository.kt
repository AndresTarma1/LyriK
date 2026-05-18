package com.example.melodist.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class JvmConfig(
    val xmx: String = "512m",
    val xms: String = "256m",
    val useG1GC: Boolean = true,
    val useZGC: Boolean = false,
    val gcLogging: Boolean = false,
) {
    companion object {
        fun defaults() = JvmConfig()
    }

    fun validate(): JvmValidationResult {
        if (!isValidMemory(xmx)) return JvmValidationResult.InvalidXmx
        if (!isValidMemory(xms)) return JvmValidationResult.InvalidXms
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
        return args
    }

    private fun isValidMemory(value: String): Boolean {
        val regex = Regex("""^\d+[mgMG]$""")
        return regex.matches(value) && value.dropLast(1).toIntOrNull() != null
    }
}

sealed class JvmValidationResult {
    object Valid : JvmValidationResult()
    object InvalidXmx : JvmValidationResult()
    object InvalidXms : JvmValidationResult()
    object IncompatibleGC : JvmValidationResult()

    val errorMessage: String?
        get() = when (this) {
            is Valid -> null
            is InvalidXmx -> "Memoria máxima inválida. Usa formato como 512m o 1g"
            is InvalidXms -> "Memoria inicial inválida. Usa formato como 128m o 1g"
            is IncompatibleGC -> "G1GC y ZGC no pueden activarse simultáneamente"
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
    }

    val config: Flow<JvmConfig> = dataStore.data.map { prefs ->
        JvmConfig(
            xmx = prefs[Keys.XMX] ?: "512m",
            xms = prefs[Keys.XMS] ?: "256m",
            useG1GC = prefs[Keys.G1GC] ?: true,
            useZGC = prefs[Keys.ZGC] ?: false,
            gcLogging = prefs[Keys.GC_LOGGING] ?: false,
        )
    }

    suspend fun updateConfig(config: JvmConfig) {
        dataStore.edit { prefs ->
            prefs[Keys.XMX] = config.xmx
            prefs[Keys.XMS] = config.xms
            prefs[Keys.G1GC] = config.useG1GC
            prefs[Keys.ZGC] = config.useZGC
            prefs[Keys.GC_LOGGING] = config.gcLogging
        }
    }

    suspend fun resetToDefaults() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.XMX)
            prefs.remove(Keys.XMS)
            prefs.remove(Keys.G1GC)
            prefs.remove(Keys.ZGC)
            prefs.remove(Keys.GC_LOGGING)
        }
    }
}
