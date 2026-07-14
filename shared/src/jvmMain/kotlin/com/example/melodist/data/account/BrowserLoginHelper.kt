package com.example.melodist.data.account

import com.example.melodist.platform.AppPaths
import com.example.melodist.platform.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import io.github.aakira.napier.Napier
import java.io.File

object BrowserLoginHelper {

    fun findBrowserExecutable(): File? =
        if (Platform.isWindows) findBrowserWindows() else findBrowserUnix()

    private fun findBrowserWindows(): File? {
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

    /** Linux/macOS: los binarios de la familia Chromium están en PATH o en ubicaciones conocidas. */
    private fun findBrowserUnix(): File? {
        val names = listOf(
            "google-chrome", "google-chrome-stable", "chromium", "chromium-browser",
            "brave-browser", "brave", "microsoft-edge", "microsoft-edge-stable",
        )
        val dirs = listOf(
            "/usr/bin", "/usr/local/bin", "/snap/bin", "/opt/google/chrome",
            "/opt/brave.com/brave", "/opt/microsoft/msedge",
            "/Applications/Google Chrome.app/Contents/MacOS", // macOS
        )
        for (dir in dirs) for (name in names) {
            val f = File(dir, name)
            if (f.exists() && f.canExecute()) return f
        }
        // Los paquetes de aplicaciones de macOS usan nombres de binarios con espacios
        File("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome").takeIf { it.exists() }?.let { return it }
        return null
    }

    private fun getBrowserName(browser: File): String {
        val p = browser.absolutePath.lowercase()
        return when {
            p.contains("edge") -> "Edge"
            p.contains("brave") -> "Brave"
            p.contains("chromium") -> "Chromium"
            p.contains("chrome") -> "Chrome"
            else -> "Browser"
        }
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

        val args = buildList {
            add(browser.absolutePath)
            add("--user-data-dir=${profileDir.absolutePath}")
            add("--no-first-run")
            add("--no-default-browser-check")
            add("--disable-default-apps")
            // Linux: forzar el almacén de contraseñas "básico" para que las cookies se cifren con la
            // contraseña fija "peanuts" (v10) en lugar de la clave del depósito GNOME/KWallet que no
            // podemos leer. Esto es lo que hace que las cookies del perfil nuevo sean descifrables sin
            // integración con el depósito de claves.
            if (Platform.isLinux) add("--password-store=basic")
            add("https://music.youtube.com")
        }
        val process = ProcessBuilder(args).redirectErrorStream(true).start()

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
