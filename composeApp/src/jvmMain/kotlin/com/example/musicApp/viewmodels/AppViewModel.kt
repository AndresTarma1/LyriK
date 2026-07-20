package com.example.musicApp.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

data class AppUpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    /** Página de release de GitHub — alternativa si no se encuentra un activo de instalador. */
    val releaseUrl: String? = null,
    /** URL de descarga directa del activo del instalador .msi/.exe. */
    val installerUrl: String? = null,
    val installerName: String? = null,
    val installerSize: Long? = null,
)

/**
 * Todo el ciclo de vida de actualización en un solo flow. La descarga se ejecuta en segundo plano (el usuario
 * sigue usando la app); cuando termina mostramos un prompt "instalar ahora / después", y el estado [Ready]
 * persiste para que Configuración pueda ofrecer "instalar actualización" sin volver a descargar.
 */
sealed interface UpdateStatus {
    data object None : UpdateStatus
    /** [progress] en 0f..1f, o -1f cuando el servidor no reportó un content length. */
    data class Downloading(val info: AppUpdateInfo, val progress: Float) : UpdateStatus
    /** Instalador completamente descargado y en disco, listo para iniciar. */
    data class Ready(val info: AppUpdateInfo, val file: File) : UpdateStatus
    /** Existe una versión más nueva pero no tiene activo .msi/.ej. (ej. Linux) — abrir la página de release. */
    data class ManualOnly(val info: AppUpdateInfo) : UpdateStatus
    data class Failed(val info: AppUpdateInfo) : UpdateStatus
}

/** Retroalimentación para la entrada manual "buscar actualizaciones" en Configuración. */
sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data object UpToDate : UpdateCheckState
    data object Failed : UpdateCheckState
}

class AppViewModel : ViewModel() {

    companion object {
        /** Fuente única de verdad para la versión de la app mostrada/comparada (también se muestra en Configuración). */
        const val CURRENT_VERSION = "0.5.0"
    }

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.None)
    val updateStatus: StateFlow<UpdateStatus> = _status.asStateFlow()

    /** Flag de una sola vez que controla el modal "instalar ahora / después"; se establece cuando una descarga termina. */
    private val _showInstallPrompt = MutableStateFlow(false)
    val showInstallPrompt: StateFlow<Boolean> = _showInstallPrompt.asStateFlow()

    private val _checkState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val checkState: StateFlow<UpdateCheckState> = _checkState.asStateFlow()

    private var downloadJob: Job? = null

    /**
     * Busca un release más nuevo. Al iniciar se pasa [manual] = false: una actualización encontrada se descarga
     * silenciosamente en segundo plano y solo interrumpe al usuario con el prompt de instalación cuando está lista.
     * Desde Configuración se pasa [manual] = true para que se muestre la retroalimentación de actualizado/fallido,
     * y — si ya hay un instalador descargado — el prompt de instalación se reabre en vez de no hacer nada.
     */
    fun checkForUpdates(manual: Boolean = false) {
        when (_status.value) {
            is UpdateStatus.Ready -> { if (manual) _showInstallPrompt.value = true; return }
            is UpdateStatus.Downloading -> return // ya está trabajando; no apilar descargas
            else -> {}
        }

        viewModelScope.launch {
            if (manual) _checkState.value = UpdateCheckState.Checking
            val latest = withContext(Dispatchers.IO) {
                runCatching { fetchLatestVersion() }.getOrNull()
            }
            if (latest == null) {
                if (manual) _checkState.value = UpdateCheckState.Failed
                return@launch
            }
            if (compareVersions(latest.tag, CURRENT_VERSION) <= 0) {
                _status.value = UpdateStatus.None
                if (manual) _checkState.value = UpdateCheckState.UpToDate
                return@launch
            }

            if (manual) _checkState.value = UpdateCheckState.Idle
            val installer = latest.installerAsset()
            val info = AppUpdateInfo(
                currentVersion = CURRENT_VERSION,
                latestVersion = latest.tag.removePrefix("v"),
                releaseUrl = latest.html_url,
                installerUrl = installer?.browser_download_url,
                installerName = installer?.name,
                installerSize = installer?.size?.takeIf { it > 0 },
            )
            if (installer == null) {
                _status.value = UpdateStatus.ManualOnly(info)
                return@launch
            }

            // Reusar un instalador que ya está en disco de una sesión anterior (coincidente por tamaño) en vez de
            // descargar de nuevo — esto es lo que hace que "el instalador sigue ahí en el siguiente inicio" funcione.
            val existing = existingInstaller(info)
            if (existing != null) {
                _status.value = UpdateStatus.Ready(info, existing)
                _showInstallPrompt.value = true
            } else {
                startDownload(info)
            }
        }
    }

    private fun startDownload(info: AppUpdateInfo) {
        if (info.installerUrl == null) return
        downloadJob?.cancel()
        _status.value = UpdateStatus.Downloading(info, -1f)
        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            val file = runCatching {
                downloadInstaller(info.installerUrl, targetFile(info)) { progress ->
                    _status.value = UpdateStatus.Downloading(info, progress)
                }
            }.onFailure { Napier.e("Update download failed: ${it.message}") }.getOrNull()

            if (file != null) {
                _status.value = UpdateStatus.Ready(info, file)
                _showInstallPrompt.value = true
            } else {
                _status.value = UpdateStatus.Failed(info)
            }
        }
    }

    /** Reintentar una descarga fallida desde la entrada "buscar" o el prompt. */
    fun retryDownload() {
        (_status.value as? UpdateStatus.Failed)?.let { startDownload(it.info) }
    }

    /** Inicia el instalador descargado y, en caso de éxito, llama a [onQuit] para que la app pueda cerrarse. */
    fun installUpdate(onQuit: () -> Unit) {
        val ready = _status.value as? UpdateStatus.Ready ?: return
        viewModelScope.launch {
            _showInstallPrompt.value = false
            val ok = withContext(Dispatchers.IO) {
                runCatching { launchInstaller(ready.file) }
                    .onFailure { Napier.e("Update launch failed: ${it.message}") }
                    .isSuccess
            }
            if (ok) onQuit()
        }
    }

    /** Ocultar el prompt pero mantener el instalador descargado + [UpdateStatus.Ready] para después. */
    fun postponeInstall() {
        _showInstallPrompt.value = false
    }

    /** Reiniciar la retroalimentación transitoria de Configuración (actualizado/fallido) a inactivo. */
    fun clearCheckFeedback() {
        _checkState.value = UpdateCheckState.Idle
    }

    private fun updateDir(): File =
        File(System.getProperty("java.io.tmpdir"), "lyrik-update").apply { mkdirs() }

    private fun targetFile(info: AppUpdateInfo): File =
        File(updateDir(), info.installerName ?: "LyriK-setup.msi")

    /** El archivo del instalador si ya está completamente presente en disco (coincidente por tamaño), o null. */
    private fun existingInstaller(info: AppUpdateInfo): File? {
        val file = targetFile(info)
        return if (file.exists() && info.installerSize != null && file.length() == info.installerSize) file
        else null
    }

    private suspend fun downloadInstaller(url: String, file: File, onProgress: (Float) -> Unit): File {
        // Sin timeout de solicitud: un instalador es de decenas de MB y toda la transferencia debe caber en una
        // sola solicitud. Mantener timeouts de socket/conexión para que una conexión realmente muerta aún falle.
        HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 30 * 60 * 1000L
                socketTimeoutMillis = 60_000
                connectTimeoutMillis = 30_000
            }
        }.use { client ->
            client.prepareGet(url).execute { response ->
                val total = response.contentLength() ?: -1L
                val channel = response.bodyAsChannel()
                file.outputStream().use { out ->
                    val buffer = ByteArray(256 * 1024)
                    var read = 0L
                    var lastPercent = -1
                    while (!channel.isClosedForRead) {
                        val n = channel.readAvailable(buffer, 0, buffer.size)
                        if (n <= 0) break
                        out.write(buffer, 0, n)
                        read += n
                        if (total > 0) {
                            val percent = ((read * 100) / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent / 100f)
                            }
                        } else if (lastPercent != -2) {
                            lastPercent = -2
                            onProgress(-1f)
                        }
                    }
                }
            }
        }
        return file
    }

    /** Ejecuta el instalador descargado. MSI pasa por msiexec; un .exe se inicia directamente. */
    private fun launchInstaller(file: File) {
        val cmd = if (file.name.endsWith(".msi", ignoreCase = true)) {
            listOf("msiexec", "/i", file.absolutePath)
        } else {
            listOf(file.absolutePath)
        }
        ProcessBuilder(cmd).start()
    }

    private val jsonParser = Json { ignoreUnknownKeys = true }

    private suspend fun fetchLatestVersion(): GithubRelease? {
        val json = HttpClient().use { client ->
            client.get("https://api.github.com/repos/AndresTarma1/LyriK/releases/latest").bodyAsText()
        }
        return jsonParser.decodeFromString<GithubRelease>(json)
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.trimStart('v').split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.trimStart('v').split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(parts1.size, parts2.size)) {
            val diff = (parts1.getOrElse(i) { 0 }) - (parts2.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }
}

@Serializable
private data class GithubRelease(
    val tag_name: String = "",
    val html_url: String = "",
    val assets: List<GithubAsset> = emptyList(),
) {
    val tag: String get() = tag_name

    /** Prefiere el instalador MSI; usa EXE como alternativa si solo está publicado ese. */
    fun installerAsset(): GithubAsset? =
        assets.firstOrNull { it.name.endsWith(".msi", ignoreCase = true) }
            ?: assets.firstOrNull { it.name.endsWith(".exe", ignoreCase = true) }
}

@Serializable
private data class GithubAsset(
    val name: String = "",
    val browser_download_url: String = "",
    val size: Long = 0,
)
