package com.example.melodist.utils.cipher

import io.github.aakira.napier.Napier
import java.net.URLEncoder

object CipherDeobfuscator {
    private const val TAG = "CipherDeobfuscator"

    private var currentPlayerHash: String? = null

    suspend fun deobfuscateStreamUrl(signatureCipher: String, videoId: String): String? {
        Napier.i("[CIPHER] deobfuscateStreamUrl videoId=$videoId")
        return try {
            deobfuscateInternal(signatureCipher, videoId, isRetry = false)
        } catch (e: Exception) {
            Napier.e(e) { "[CIPHER] Failed, retrying with fresh JS: ${e.message}" }
            try {
                PlayerJsFetcher.invalidateCache()
                currentPlayerHash = null
                deobfuscateInternal(signatureCipher, videoId, isRetry = true)
            } catch (retryE: Exception) {
                Napier.e(retryE) { "[CIPHER] Retry also failed: ${retryE.message}" }
                null
            }
        }
    }

    private suspend fun deobfuscateInternal(signatureCipher: String, videoId: String, isRetry: Boolean): String? {
        val params = parseQueryParams(signatureCipher)
        val obfuscatedSig = params["s"] ?: return null
        val sigParam = params["sp"] ?: "signature"
        val baseUrl = params["url"] ?: return null

        Napier.i("[CIPHER] Parsed: sigLen=${obfuscatedSig.length}, url=${baseUrl.take(60)}...")

        if (!ensurePrepared(forceRefresh = isRetry)) return null

        val deobfuscatedSig = EjsCipherSolver.solve("sig", obfuscatedSig) ?: run {
            Napier.e("[CIPHER] sig solve returned null")
            return null
        }
        val separator = if ("?" in baseUrl) "&" else "?"
        // n-transform is applied centrally by the caller (YTPlayerutils) so it runs exactly once.
        return "$baseUrl${separator}$sigParam=${URLEncoder.encode(deobfuscatedSig, "UTF-8")}"
    }

    suspend fun transformNParamInUrl(url: String): String {
        try {
            val nMatch = Regex("[?&]n=([^&]+)").find(url) ?: run {
                Napier.d("[NTRACE] No n param found, skipping")
                return url
            }
            val nValue = java.net.URLDecoder.decode(nMatch.groupValues[1], "UTF-8")

            if (!ensurePrepared(forceRefresh = false)) {
                Napier.e("[NTRACE] player not prepared, returning original URL")
                return url
            }

            val transformedN = EjsCipherSolver.solve("n", nValue) ?: run {
                Napier.e("[NTRACE] n solve returned null, returning original URL")
                return url
            }

            val result = url.replaceFirst(
                Regex("([?&])n=[^&]+"),
                "$1n=${URLEncoder.encode(transformedN, "UTF-8")}"
            )
            Napier.i("[NTRACE] n-transform SUCCESS")
            return result
        } catch (e: Exception) {
            Napier.e("[NTRACE] N-transform exception: ${e::class.simpleName}: ${e.message}")
            return url
        }
    }

    /** Fetches player.js (cached) and prepares the EJS solver for it. */
    private suspend fun ensurePrepared(forceRefresh: Boolean): Boolean {
        if (!forceRefresh && currentPlayerHash != null) return true

        Napier.i("[CIPHER] Fetching player.js (forceRefresh=$forceRefresh)...")
        val result = PlayerJsFetcher.getPlayerJs(forceRefresh = forceRefresh)
        if (result == null) {
            Napier.e("[CIPHER] PlayerJsFetcher.getPlayerJs() returned null")
            return false
        }
        val (playerJs, hash) = result
        return try {
            EjsCipherSolver.prepare(playerJs, hash)
            currentPlayerHash = hash
            Napier.i("[CIPHER] Solver ready for player hash=$hash")
            true
        } catch (e: Exception) {
            Napier.e("[CIPHER] EJS prepare failed: ${e.message}")
            false
        }
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (pair in query.split("&")) {
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val key = java.net.URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val value = java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                result[key] = value
            }
        }
        return result
    }

    fun getDebugInfo(): Map<String, Any?> {
        return mapOf(
            "playerHash" to currentPlayerHash,
        )
    }
}
