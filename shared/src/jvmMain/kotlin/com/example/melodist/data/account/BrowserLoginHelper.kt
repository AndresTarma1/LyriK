package com.example.melodist.data.account

import com.example.melodist.platform.AppPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import io.github.aakira.napier.Napier
import java.io.File

object BrowserLoginHelper {

    fun findBrowserExecutable(): File? {
        val localAppData = System.getenv("LOCALAPPDATA") ?: ""
        val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
        val programFilesX86 = System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)"

        val candidates = listOf(
            "$programFilesX86\\Microsoft\\Edge\\Application\\msedge.exe",
            "$programFiles\\Microsoft\\Edge\\Application\\msedge.exe",
            "$localAppData\\Microsoft\\Edge\\Application\\msedge.exe",
            "$programFiles\\Google\\Chrome\\Application\\chrome.exe",
            "$programFilesX86\\Google\\Chrome\\Application\\chrome.exe",
            "$localAppData\\Google\\Chrome\\Application\\chrome.exe",
            "$programFiles\\BraveSoftware\\Brave-Browser\\Application\\brave.exe",
            "$programFilesX86\\BraveSoftware\\Brave-Browser\\Application\\brave.exe",
            "$localAppData\\BraveSoftware\\Brave-Browser\\Application\\brave.exe",
        )

        return candidates.map(::File).firstOrNull { it.exists() }
    }

    private fun getBrowserName(browser: File): String = when {
        browser.absolutePath.contains("Edge", ignoreCase = true) -> "Edge"
        browser.absolutePath.contains("Chrome", ignoreCase = true) -> "Chrome"
        browser.absolutePath.contains("Brave", ignoreCase = true) -> "Brave"
        else -> "Browser"
    }

    private fun getLoginProfileDir(): File {
        AppPaths.ensureDirectories()
        val dir = File(AppPaths.localRoot, "login-profile")
        dir.mkdirs()
        return dir
    }

    suspend fun loginWithBrowser(
        onStatus: (String) -> Unit
    ): CookieExtractResult = withContext(Dispatchers.IO) {
        val browser = findBrowserExecutable()
            ?: return@withContext CookieExtractResult.Error("No browser found (need Edge or Chrome)")

        val browserName = getBrowserName(browser)
        val profileDir = getLoginProfileDir()

        Napier.i("Launching $browserName with profile at ${profileDir.absolutePath}")
        onStatus("Opening $browserName...")

        val process = ProcessBuilder(
            browser.absolutePath,
            "--user-data-dir=${profileDir.absolutePath}",
            "--no-first-run",
            "--no-default-browser-check",
            "--disable-default-apps",
            "https://music.youtube.com"
        ).redirectErrorStream(true).start()

        delay(3000)
        if (!process.isAlive) {
            Napier.w("$browserName exited quickly (possible handoff). Waiting for user to close browser...")
            onStatus("Sign in to YouTube Music, then close $browserName completely.")

            val result = pollForCookiesInProfile(profileDir, onStatus, browserName)
            if (result != null) return@withContext result

            return@withContext CookieExtractResult.Error(
                "$browserName exited unexpectedly. Try closing ALL $browserName windows first, then retry."
            )
        }

        onStatus("Sign in to YouTube Music, then close $browserName.")

        var waited = 0
        while (process.isAlive && waited < 600) {
            delay(2000)
            waited += 2
        }

        if (process.isAlive) {
            process.destroyForcibly()
            return@withContext CookieExtractResult.Error("Timed out waiting for browser to close")
        }

        delay(1000)

        onStatus("Reading cookies...")
        readCookiesFromProfile(profileDir, browserName)
    }

    private suspend fun pollForCookiesInProfile(
        profileDir: File,
        onStatus: (String) -> Unit,
        browserName: String
    ): CookieExtractResult? {
        for (attempt in 1..60) {
            delay(5000)

            val lockFile = File(profileDir, "lockfile")
            val singletonLock = File(profileDir, "SingletonLock")

            if (!lockFile.exists() && !singletonLock.exists()) {
                delay(1000)
                val result = readCookiesFromProfile(profileDir, browserName)
                if (result is CookieExtractResult.Success) return result
            }

            if (attempt % 6 == 0) {
                onStatus("Still waiting... Sign in and close $browserName when done.")
            }
        }
        return null
    }

    private fun readCookiesFromProfile(profileDir: File, browserName: String): CookieExtractResult {
        val cookieDb = File(profileDir, "Default/Network/Cookies").takeIf { it.exists() }
            ?: File(profileDir, "Default/Cookies").takeIf { it.exists() }
            ?: return CookieExtractResult.Error("No cookies found. Did you sign in to YouTube Music?")

        val localState = File(profileDir, "Local State")
        if (!localState.exists()) {
            return CookieExtractResult.Error("Browser profile incomplete (no Local State)")
        }

        val profile = BrowserProfile(
            name = "$browserName (login)",
            userDataDir = profileDir,
            cookieDbPath = cookieDb,
            localStatePath = localState,
            type = BrowserType.CHROMIUM
        )

        return BrowserCookieExtractor.extractYouTubeCookies(profile)
    }
}
