package com.example.melodist.data.account

import com.example.melodist.platform.Platform
import io.github.aakira.napier.Napier
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

enum class BrowserType {
    CHROMIUM, FIREFOX
}

data class BrowserProfile(
    val name: String,
    val userDataDir: File,
    val cookieDbPath: File,
    val localStatePath: File,
    val type: BrowserType = BrowserType.CHROMIUM
)

sealed class CookieExtractResult {
    data class Success(val cookie: String, val browserName: String) : CookieExtractResult()
    data class Error(val message: String) : CookieExtractResult()
}

object BrowserCookieExtractor {

    fun detectBrowsers(): List<BrowserProfile> =
        if (Platform.isWindows) detectBrowsersWindows() else detectBrowsersUnix()

    private fun detectBrowsersWindows(): List<BrowserProfile> {
        val browsers = mutableListOf<BrowserProfile>()
        val localAppData = System.getenv("LOCALAPPDATA") ?: return browsers
        val appData = System.getenv("APPDATA") ?: return browsers

        val chromiumCandidates = listOf(
            "Chrome" to File(localAppData, "Google/Chrome/User Data"),
            "Edge" to File(localAppData, "Microsoft/Edge/User Data"),
            "Opera" to File(appData, "Opera Software/Opera Stable"),
            "Opera GX" to File(appData, "Opera Software/Opera GX Stable"),
            "Brave" to File(localAppData, "BraveSoftware/Brave-Browser/User Data"),
            "Vivaldi" to File(localAppData, "Vivaldi/User Data"),
        )

        for ((name, dir) in chromiumCandidates) {
            if (!dir.exists()) continue
            val cookieDb = File(dir, "Default/Network/Cookies").takeIf { it.exists() }
                ?: File(dir, "Default/Cookies").takeIf { it.exists() }
                ?: File(dir, "Network/Cookies").takeIf { it.exists() }
                ?: File(dir, "Cookies").takeIf { it.exists() }
            val localState = File(dir, "Local State")

            if (cookieDb != null && localState.exists()) {
                browsers.add(BrowserProfile(name, dir, cookieDb, localState, BrowserType.CHROMIUM))
            }
        }

        detectFirefoxInRoot(File(appData, "Mozilla/Firefox"))?.let { browsers.add(it) }

        return browsers
    }

    /**
     * Linux/macOS. Existing Chromium-family profiles encrypt cookies with a key stored in the OS
     * keyring (GNOME Keyring/KWallet) we can't read, so we don't offer them — the browser-login
     * flow (fresh profile forced to the "basic" store) is the supported path there. Firefox stores
     * cookies unencrypted, so detecting existing Firefox profiles works directly.
     */
    private fun detectBrowsersUnix(): List<BrowserProfile> {
        val browsers = mutableListOf<BrowserProfile>()
        val home = System.getProperty("user.home") ?: return browsers

        val firefoxRoots = listOf(
            File(home, ".mozilla/firefox"),
            File(home, "snap/firefox/common/.mozilla/firefox"),
            File(home, ".var/app/org.mozilla.firefox/.mozilla/firefox"), // Flatpak
            File(home, "Library/Application Support/Firefox"),            // macOS
        )
        for (root in firefoxRoots) {
            if (!root.exists()) continue
            detectFirefoxInRoot(root)?.let { browsers.add(it); break }
        }
        return browsers
    }

    private fun detectFirefoxInRoot(firefoxDir: File): BrowserProfile? {
        val profilesIni = File(firefoxDir, "profiles.ini")
        if (!profilesIni.exists()) return null

        var defaultProfilePath: String? = null
        var currentPath: String? = null
        var currentIsDefault = false

        for (line in profilesIni.readLines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[") && currentPath != null) {
                if (currentIsDefault) {
                    defaultProfilePath = currentPath
                    break
                }
                currentPath = null
                currentIsDefault = false
            }
            when {
                trimmed.startsWith("Path=", ignoreCase = true) ->
                    currentPath = trimmed.substringAfter("=")
                trimmed.equals("Default=1", ignoreCase = true) ->
                    currentIsDefault = true
            }
        }
        if (defaultProfilePath == null && currentIsDefault && currentPath != null) {
            defaultProfilePath = currentPath
        }

        if (defaultProfilePath == null) {
            val installsIni = File(firefoxDir, "installs.ini")
            if (installsIni.exists()) {
                for (line in installsIni.readLines()) {
                    if (line.trim().startsWith("Default=", ignoreCase = true)) {
                        defaultProfilePath = line.trim().substringAfter("=")
                        break
                    }
                }
            }
        }

        val profileDir = if (defaultProfilePath != null) {
            val path = defaultProfilePath.replace("\\", "/")
            if (File(path).isAbsolute) File(path) else File(firefoxDir, path)
        } else {
            firefoxDir.resolve("Profiles").listFiles()
                ?.firstOrNull { File(it, "cookies.sqlite").exists() }
        }

        val cookieDb = profileDir?.let { File(it, "cookies.sqlite") }
        if (cookieDb != null && cookieDb.exists()) {
            return BrowserProfile(
                name = "Firefox",
                userDataDir = profileDir,
                cookieDbPath = cookieDb,
                localStatePath = profileDir,
                type = BrowserType.FIREFOX
            )
        }
        return null
    }

    fun extractYouTubeCookies(browser: BrowserProfile): CookieExtractResult {
        return when (browser.type) {
            BrowserType.CHROMIUM -> extractChromiumCookies(browser)
            BrowserType.FIREFOX -> extractFirefoxCookies(browser)
        }
    }

    private fun extractFirefoxCookies(browser: BrowserProfile): CookieExtractResult {
        val tempDb = Files.createTempFile("ml_ff_cookies_", ".db").toFile()
        try {
            browser.cookieDbPath.copyTo(tempDb, overwrite = true)
        } catch (_: Exception) {
            try {
                copyLockedFile(browser.cookieDbPath, tempDb)
            } catch (_: Exception) {
                tempDb.delete()
                return CookieExtractResult.Error("Can't access Firefox cookies. Try closing Firefox and retry.")
            }
        }

        val cookieMap = mutableMapOf<String, String>()
        val cookieDomain = mutableMapOf<String, String>()
        try {
            Class.forName("org.sqlite.JDBC")
            DriverManager.getConnection("jdbc:sqlite:${tempDb.absolutePath}").use { conn ->
                val stmt = conn.prepareStatement(
                    """SELECT name, value, host FROM moz_cookies
                       WHERE host LIKE '%youtube.com' OR host LIKE '%.google.com'
                       ORDER BY CASE WHEN host LIKE '%youtube.com' THEN 1 ELSE 2 END"""
                )
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val name = rs.getString("name")
                    val host = rs.getString("host")
                    val value = rs.getString("value")

                    if (name in cookieMap && cookieDomain[name]?.contains("youtube") == true) continue
                    if (!value.isNullOrBlank()) {
                        cookieMap[name] = value
                        cookieDomain[name] = host
                    }
                }
            }
        } catch (e: Exception) {
            return CookieExtractResult.Error("Failed to read Firefox cookie database: ${e.message}")
        } finally {
            tempDb.delete()
        }

        return buildCookieResult(cookieMap, browser.name)
    }

    private fun extractChromiumCookies(browser: BrowserProfile): CookieExtractResult {
        Napier.i("Extracting cookies from ${browser.name}: db=${browser.cookieDbPath}, localState=${browser.localStatePath}")
        // Windows wraps the AES key with DPAPI in Local State; Linux/macOS derive it from a fixed
        // password ("peanuts" for the basic store we force during browser login).
        val masterKey = if (Platform.isWindows) {
            decryptMasterKey(browser.localStatePath)
                ?: return CookieExtractResult.Error("Failed to decrypt ${browser.name}'s encryption key. Make sure ${browser.name} is installed properly.")
        } else {
            linuxChromiumKey()
        }

        val tempDb = Files.createTempFile("ml_cookies_", ".db").toFile()
        val tempWal = File(tempDb.absolutePath + "-wal")
        val tempShm = File(tempDb.absolutePath + "-shm")
        try {
            browser.cookieDbPath.copyTo(tempDb, overwrite = true)
            val walFile = File(browser.cookieDbPath.absolutePath + "-wal")
            val shmFile = File(browser.cookieDbPath.absolutePath + "-shm")
            if (walFile.exists()) walFile.copyTo(tempWal, overwrite = true)
            if (shmFile.exists()) shmFile.copyTo(tempShm, overwrite = true)
        } catch (_: Exception) {
            try {
                copyLockedFile(browser.cookieDbPath, tempDb)
            } catch (_: Exception) {
                tempDb.delete(); tempWal.delete(); tempShm.delete()
                return CookieExtractResult.Error("Can't access ${browser.name}'s cookies. Try closing ${browser.name} and retry.")
            }
        }

        val cookieMap = mutableMapOf<String, String>()
        val cookieDomain = mutableMapOf<String, String>()
        try {
            Class.forName("org.sqlite.JDBC")
            DriverManager.getConnection("jdbc:sqlite:${tempDb.absolutePath}").use { conn ->
                val stmt = conn.prepareStatement(
                    """SELECT name, encrypted_value, value, host_key FROM cookies
                       WHERE host_key LIKE '%youtube.com' OR host_key LIKE '%.google.com'
                       ORDER BY CASE WHEN host_key LIKE '%youtube.com' THEN 1 ELSE 2 END"""
                )
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val name = rs.getString("name")
                    val host = rs.getString("host_key")
                    val encryptedValue = rs.getBytes("encrypted_value")
                    val plainValue = rs.getString("value")

                    if (name in cookieMap && cookieDomain[name]?.contains("youtube") == true) continue

                    val value = when {
                        encryptedValue != null && encryptedValue.size > 3 ->
                            decryptCookieValue(encryptedValue, masterKey)
                        !plainValue.isNullOrBlank() -> plainValue
                        else -> null
                    }

                    if (!value.isNullOrBlank()) {
                        val safe = value.filter { it.code >= 0x20 && it.code != 0x7F }
                        if (safe.isNotEmpty()) {
                            cookieMap[name] = safe
                            cookieDomain[name] = host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return CookieExtractResult.Error("Failed to read cookie database: ${e.message}")
        } finally {
            tempDb.delete(); tempWal.delete(); tempShm.delete()
        }

        Napier.i("${browser.name}: found ${cookieMap.size} cookies (keys: ${cookieMap.keys.take(10)})")
        return buildCookieResult(cookieMap, browser.name)
    }

    private fun copyLockedFile(source: File, dest: File) {
        // robocopy can copy a file Chrome holds an exclusive lock on (Windows-only). On Linux/macOS
        // SQLite files aren't exclusively locked, so the plain copyTo already succeeded — if we got
        // here on those platforms, there's nothing more to try.
        if (!Platform.isWindows) throw Exception("locked-file copy unsupported on this platform")
        val sourceDir = source.parentFile.absolutePath
        val destDir = dest.parentFile.absolutePath
        val dbName = source.name
        val result = ProcessBuilder(
            "robocopy", sourceDir, destDir, dbName, "/NJH", "/NJS", "/NP"
        ).redirectErrorStream(true).start()
        result.waitFor()
        val copiedFile = File(dest.parentFile, dbName)
        if (copiedFile.exists() && copiedFile != dest) {
            copiedFile.copyTo(dest, overwrite = true)
            copiedFile.delete()
        }
        if (!dest.exists() || dest.length() == 0L) {
            throw Exception("robocopy failed")
        }
    }

    private fun buildCookieResult(cookieMap: Map<String, String>, browserName: String): CookieExtractResult {
        if (cookieMap.isEmpty()) {
            return CookieExtractResult.Error("No YouTube cookies found in $browserName. Make sure you're signed in to music.youtube.com.")
        }

        val hasAuth = cookieMap.containsKey("SAPISID") || cookieMap.containsKey("__Secure-3PAPISID")
        if (!hasAuth) {
            return CookieExtractResult.Error("You're not signed in to YouTube Music in $browserName. Sign in first, then try again.")
        }

        val priority = listOf(
            "SAPISID", "__Secure-1PAPISID", "__Secure-3PAPISID",
            "SID", "__Secure-1PSID", "__Secure-3PSID",
            "HSID", "SSID", "APISID",
            "SIDCC", "__Secure-1PSIDCC", "__Secure-3PSIDCC",
            "__Secure-1PSIDTS", "__Secure-3PSIDTS",
            "LOGIN_INFO", "PREF", "SOCS"
        )
        val parts = mutableListOf<String>()
        for (key in priority) {
            cookieMap[key]?.let { parts.add("$key=$it") }
        }
        for ((key, value) in cookieMap) {
            if (key !in priority) {
                parts.add("$key=$value")
            }
        }

        return CookieExtractResult.Success(
            cookie = parts.joinToString("; "),
            browserName = browserName
        )
    }

    private fun decryptMasterKey(localStateFile: File): ByteArray? {
        return try {
            val json = localStateFile.readText()
            val match = """"encrypted_key"\s*:\s*"([^"]+)"""".toRegex().find(json) ?: return null
            val raw = Base64.getDecoder().decode(match.groupValues[1])

            if (raw.size < 6 || String(raw, 0, 5) != "DPAPI") return null
            decryptWithDPAPI(raw.copyOfRange(5, raw.size))
        } catch (e: Exception) {
            Napier.e("Master key decryption failed: ${e.message}")
            null
        }
    }

    private fun decryptWithDPAPI(encrypted: ByteArray): ByteArray? {
        return try {
            val b64 = Base64.getEncoder().encodeToString(encrypted)
            val ps = ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-Command",
                "Add-Type -AssemblyName System.Security; " +
                "[Convert]::ToBase64String(" +
                "[System.Security.Cryptography.ProtectedData]::Unprotect(" +
                "[Convert]::FromBase64String('$b64'),${'$'}null," +
                "[System.Security.Cryptography.DataProtectionScope]::CurrentUser))"
            ).redirectErrorStream(true).start()

            val output = ps.inputStream.bufferedReader().readText().trim()
            val exitCode = ps.waitFor()
            if (exitCode != 0 || output.isBlank()) return null
            Base64.getDecoder().decode(output.lines().last().trim())
        } catch (e: Exception) {
            Napier.e("DPAPI call failed: ${e.message}")
            null
        }
    }

    private fun decryptCookieValue(encrypted: ByteArray, masterKey: ByteArray): String? =
        if (Platform.isWindows) decryptCookieValueWindows(encrypted, masterKey)
        else decryptCookieValueLinux(encrypted, masterKey)

    private fun decryptCookieValueWindows(encrypted: ByteArray, masterKey: ByteArray): String? {
        return try {
            if (encrypted.size < 16) return null
            val prefix = String(encrypted, 0, 3)

            if (prefix == "v10" || prefix == "v11" || prefix == "v20") {
                val nonce = encrypted.copyOfRange(3, 15)
                val ciphertext = encrypted.copyOfRange(15, encrypted.size)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    SecretKeySpec(masterKey, "AES"),
                    GCMParameterSpec(128, nonce)
                )
                val decrypted = cipher.doFinal(ciphertext)
                stripBindingHash(decrypted)
            } else {
                decryptWithDPAPI(encrypted)?.let { String(it) }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Linux Chromium cookie decryption: `v10`/`v11` prefix, then AES-128-CBC with a 16-space IV and
     * PKCS#7 padding (contrast to Windows' AES-GCM). Newer Chrome also prepends a 32-byte SHA-256
     * domain-binding hash, stripped by [stripBindingHash].
     */
    private fun decryptCookieValueLinux(encrypted: ByteArray, key: ByteArray): String? {
        return try {
            if (encrypted.size < 3) return null
            val prefix = String(encrypted, 0, 3)
            if (prefix != "v10" && prefix != "v11") {
                // Unencrypted plaintext (older/basic profiles occasionally store it raw).
                return String(encrypted, Charsets.UTF_8)
            }
            val ciphertext = encrypted.copyOfRange(3, encrypted.size)
            if (ciphertext.isEmpty() || ciphertext.size % 16 != 0) return null

            val cipher = Cipher.getInstance("AES/CBC/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                IvParameterSpec(ByteArray(16) { 0x20 }) // 16 spaces
            )
            var decrypted = cipher.doFinal(ciphertext)

            // Remove PKCS#7 padding.
            val pad = decrypted.lastOrNull()?.toInt()?.and(0xFF) ?: 0
            if (pad in 1..16 && pad <= decrypted.size) {
                decrypted = decrypted.copyOfRange(0, decrypted.size - pad)
            }
            stripBindingHash(decrypted)
        } catch (e: Exception) {
            null
        }
    }

    /** AES-128 key for Linux "basic" password store: PBKDF2(HMAC-SHA1, "peanuts", "saltysalt", 1). */
    private fun linuxChromiumKey(): ByteArray {
        val spec = PBEKeySpec("peanuts".toCharArray(), "saltysalt".toByteArray(), 1, 128)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
    }

    private fun stripBindingHash(decrypted: ByteArray): String {
        val HASH_LEN = 32
        if (decrypted.size <= HASH_LEN) {
            return String(decrypted, Charsets.UTF_8)
        }

        val hasBindingHash = (0 until HASH_LEN).any { i ->
            val b = decrypted[i].toInt() and 0xFF
            b < 0x20 || b > 0x7E
        }

        return if (hasBindingHash) {
            String(decrypted, HASH_LEN, decrypted.size - HASH_LEN, Charsets.UTF_8)
        } else {
            String(decrypted, Charsets.UTF_8)
        }
    }
}
