package com.example.melodist.utils.cipher

import io.github.aakira.napier.Napier
import java.net.URLEncoder

object CipherDeobfuscator {
    private const val TAG = "CipherDeobfuscator"

    private var currentPlayerHash: String? = null

    /**
     * Deobfuscates a YouTube-style signature cipher with automatic retry on failure.
     *
     * If initial deobfuscation fails, retries using freshly fetched player JavaScript.
     *
     * @param signatureCipher The encoded signature cipher from YouTube.
     * @param videoId The YouTube video ID.
     * @return The deobfuscated URL string, or `null` if deobfuscation fails even after retry.
     */
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

    /**
     * Deobfuscates a signature cipher and appends the solved signature to the base URL.
     *
     * @return The URL with the deobfuscated signature appended, or `null` if required components are missing or signature solving fails.
     */
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

    /**
     * Transforms the n query parameter in a URL using the cipher solver.
     *
     * @param url The URL to transform.
     * @return The URL with the n parameter transformed by the cipher solver, or the original URL if the parameter is not found or transformation fails.
     */
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

    /**
     * Ensures the EJS cipher solver is prepared with the current player.js.
     *
     * @param forceRefresh If `true`, refetch player.js and reinitialize the solver even if already prepared.
     * @return `true` if the solver is ready, `false` if fetching player.js or initialization failed.
     */
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

    /**
     * Parses a query string into decoded key-value pairs.
     *
     * @param query A URL-encoded query string (e.g., "key1=value1&key2=value2").
     * @return A map of URL-decoded query parameters.
     */
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

    /**
     * Returns debug information about the cipher deobfuscator's state.
     *
     * @return A map containing the current player hash.
     */
    fun getDebugInfo(): Map<String, Any?> {
        return mapOf(
            "playerHash" to currentPlayerHash,
        )
    }
}
