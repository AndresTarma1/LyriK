package com.example.melodist.viewmodels

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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
    /** GitHub release page — fallback if no installer asset is found. */
    val releaseUrl: String? = null,
    /** Direct download URL of the .msi/.exe installer asset. */
    val installerUrl: String? = null,
    val installerName: String? = null,
    val installerSize: Long? = null,
)

/** Progress of the in-app download + install. */
sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    /** [progress] in 0f..1f, or -1f when the server didn't report a content length. */
    data class Downloading(val progress: Float) : UpdateDownloadState
    data object Launching : UpdateDownloadState
    data class Failed(val message: String) : UpdateDownloadState
}

class AppViewModel : ViewModel() {

    companion object {
        /** Single source of truth for the displayed/compared app version (also shown in Settings). */
        const val CURRENT_VERSION = "0.4.0"
    }

    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo: StateFlow<AppUpdateInfo?> = _updateInfo.asStateFlow()

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    private val currentVersion = CURRENT_VERSION

    fun checkForUpdates() {
        viewModelScope.launch {
            val latest = withContext(Dispatchers.IO) {
                try {
                    fetchLatestVersion()
                } catch (_: Exception) { null }
            } ?: return@launch

            if (compareVersions(latest.tag, currentVersion) > 0) {
                val installer = latest.installerAsset()
                _updateInfo.value = AppUpdateInfo(
                    currentVersion = currentVersion,
                    latestVersion = latest.tag.removePrefix("v"),
                    releaseUrl = latest.html_url,
                    installerUrl = installer?.browser_download_url,
                    installerName = installer?.name,
                    installerSize = installer?.size?.takeIf { it > 0 },
                )
            }
        }
    }

    /**
     * Hides the dialog. If an installer is available, a download is kicked off (or left running)
     * in the background — silently, no progress shown — so it's already on disk and ready the next
     * time the user chooses to update, without needing to wait for the download again.
     */
    fun dismissUpdate() {
        val info = _updateInfo.value
        _updateInfo.value = null
        if (info?.installerUrl != null) startOrJoinDownload(info)
    }

    // Single in-flight download shared by the foreground "Actualizar" click and any background
    // download kicked off by dismissUpdate(), so clicking Update while a background download is
    // already running joins it instead of starting a duplicate.
    private var downloadJob: Deferred<File?>? = null

    private fun startOrJoinDownload(info: AppUpdateInfo): Deferred<File?> {
        downloadJob?.let { if (it.isActive) return it }
        val job = viewModelScope.async(Dispatchers.IO) {
            runCatching { ensureDownloaded(info) }
                .onFailure { Napier.e("Update download failed: ${it.message}") }
                .getOrNull()
        }
        downloadJob = job
        return job
    }

    /**
     * Ensures the installer is fully downloaded and installs it, then invokes [onLaunched] so the
     * app can quit and let the installer replace its files. Joins a download already running in the
     * background (started by a previous "Más tarde") instead of starting a second one.
     */
    fun downloadAndInstall(onLaunched: () -> Unit) {
        val info = _updateInfo.value ?: return
        if (info.installerUrl == null) return

        viewModelScope.launch {
            if (_downloadState.value !is UpdateDownloadState.Downloading) {
                _downloadState.value = UpdateDownloadState.Downloading(-1f)
            }
            val file = startOrJoinDownload(info).await()
            if (file == null) {
                _downloadState.value = UpdateDownloadState.Failed("download")
                return@launch
            }
            _downloadState.value = UpdateDownloadState.Launching
            val launched = withContext(Dispatchers.IO) {
                runCatching { launchInstaller(file) }
                    .onFailure { Napier.e("Update launch failed: ${it.message}") }
                    .isSuccess
            }
            if (launched) onLaunched() else _downloadState.value = UpdateDownloadState.Failed("launch")
        }
    }

    /**
     * Returns the installer file, downloading it only if not already fully present on disk (e.g.
     * from a previous background download — matched by expected size). Stored under the system temp
     * dir so it survives across app restarts: `%TEMP%/lyrik-update/<installer-name>`.
     */
    private suspend fun ensureDownloaded(info: AppUpdateInfo): File {
        val name = info.installerName ?: "LyriK-setup.msi"
        val dir = File(System.getProperty("java.io.tmpdir"), "lyrik-update").apply { mkdirs() }
        val file = File(dir, name)
        if (file.exists() && info.installerSize != null && file.length() == info.installerSize) {
            Napier.i("Update installer already downloaded: ${file.absolutePath}")
            return file
        }
        return downloadInstaller(info.installerUrl!!, file)
    }

    private suspend fun downloadInstaller(url: String, file: File): File {
        // No request timeout: an installer is tens of MB and the whole transfer must fit in one
        // request. Keep socket/connect timeouts so a truly dead connection still fails.
        HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 30 * 60 * 1000L // 30 min — effectively no cap for a download
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
                        // Throttle UI updates to whole-percent changes so we don't flood recomposition.
                        if (total > 0) {
                            val percent = ((read * 100) / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                _downloadState.value = UpdateDownloadState.Downloading(percent / 100f)
                            }
                        } else if (lastPercent != -2) {
                            lastPercent = -2
                            _downloadState.value = UpdateDownloadState.Downloading(-1f)
                        }
                    }
                }
            }
        }
        return file
    }

    /** Runs the downloaded installer. MSI goes through msiexec; an .exe is launched directly. */
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

    /** Prefer the MSI installer; fall back to an EXE if that's all that's published. */
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
