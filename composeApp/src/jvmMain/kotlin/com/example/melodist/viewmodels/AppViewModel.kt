package com.example.melodist.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class AppUpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    var downloadUrl: String? = null,
)

class AppViewModel : ViewModel() {

    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo: StateFlow<AppUpdateInfo?> = _updateInfo.asStateFlow()

    private val currentVersion = "0.1.3"

    fun checkForUpdates() {
        viewModelScope.launch {
            val latest = withContext(Dispatchers.IO) {
                try {
                    fetchLatestVersion()
                } catch (_: Exception) { null }
            } ?: return@launch

            if (compareVersions(latest.tag, currentVersion) > 0) {
                _updateInfo.value = AppUpdateInfo(
                    currentVersion = currentVersion,
                    latestVersion = latest.tag.removePrefix("v"),
                    downloadUrl = latest.url,
                )
            }
        }
    }

    fun dismissUpdate() {
        _updateInfo.value = null
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
) {
    val tag: String get() = tag_name
    val url: String get() = html_url
}
