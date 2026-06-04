package com.example.melodist.utils.cipher

import io.github.aakira.napier.Napier
import java.net.URLEncoder

object CipherDeobfuscator {
    private const val TAG = "CipherDeobfuscator"

    private var jsExecutor: JsExecutor? = null
    private var currentPlayerHash: String? = null

    suspend fun deobfuscateStreamUrl(signatureCipher: String, videoId: String): String? {
        Napier.i("[CIPHER] deobfuscateStreamUrl videoId=$videoId")

        return try {
            deobfuscateInternal(signatureCipher, videoId, isRetry = false)
        } catch (e: Exception) {
            Napier.e(e) { "[CIPHER] Failed, retrying with fresh JS: ${e.message}" }
            try {
                PlayerJsFetcher.invalidateCache()
                jsExecutor = null
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

        val executor = getOrCreateExecutor(forceRefresh = isRetry) ?: return null

        val deobfuscatedSig = executor.deobfuscateSignature(obfuscatedSig)
        val separator = if ("?" in baseUrl) "&" else "?"
        return "$baseUrl${separator}$sigParam=${URLEncoder.encode(deobfuscatedSig, "UTF-8")}"
    }

    suspend fun transformNParamInUrl(url: String): String {
        Napier.i("[NTRACE] transformNParamInUrl called, urlLength=${url.length}")
        try {
            val nMatch = Regex("[?&]n=([^&]+)").find(url)
            if (nMatch == null) {
                Napier.i("[NTRACE] No n param found, skipping")
                return url
            }
            Napier.i("[NTRACE] n param found")

            val nValueEncoded = nMatch.groupValues[1]
            val nValue = java.net.URLDecoder.decode(nValueEncoded, "UTF-8")
            Napier.i("[NTRACE] nValue=$nValue")

            val executor = getOrCreateExecutor(forceRefresh = false)
            if (executor == null) {
                Napier.e("[NTRACE] getOrCreateExecutor returned null")
                return url
            }
            Napier.i("[NTRACE] Executor obtained, nFunctionAvailable=${executor.nFunctionAvailable}")

            if (!executor.nFunctionAvailable) {
                Napier.e("[NTRACE] nFunctionAvailable=false, returning original URL")
                return url
            }

            val transformedN = executor.transformN(nValue)
            Napier.i("[NTRACE] Transform result: $transformedN")

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

    private suspend fun getOrCreateExecutor(forceRefresh: Boolean): JsExecutor? {
        Napier.i("[NTRACE] getOrCreateExecutor forceRefresh=$forceRefresh existing=${jsExecutor != null}")
        if (!forceRefresh && jsExecutor != null) return jsExecutor

        jsExecutor?.close()
        jsExecutor = null

        Napier.i("[NTRACE] Fetching player.js...")
        val result = PlayerJsFetcher.getPlayerJs(forceRefresh = forceRefresh)
        if (result == null) {
            Napier.e("[NTRACE] PlayerJsFetcher.getPlayerJs() returned null")
            return null
        }
        val (playerJs, hash) = result
        Napier.i("[NTRACE] Player.js fetched: hash=$hash, length=${playerJs.length}")

        val analysis = FunctionNameExtractor.analyzePlayerJs(playerJs, knownHash = hash)
        if (analysis.sigInfo == null) {
            Napier.e("[NTRACE] No sig function found in player.js")
            return null
        }
        Napier.i("[NTRACE] analysis: sig=${analysis.sigInfo.name} n=${analysis.nFuncInfo?.name} ts=${analysis.signatureTimestamp}")

        Napier.i("[NTRACE] Creating JsExecutor...")
        val executor = try {
            JsExecutor.create(playerJs = playerJs, sigInfo = analysis.sigInfo, nFuncInfo = analysis.nFuncInfo)
        } catch (e: Exception) {
            Napier.e("[NTRACE] JsExecutor.create failed: ${e::class.simpleName}: ${e.message}")
            return null
        }

        Napier.i("[NTRACE] JsExecutor created: sigAvail=${executor.sigFunctionAvailable} nAvail=${executor.nFunctionAvailable} nName=${executor.discoveredNFuncName}")

        jsExecutor = executor
        currentPlayerHash = hash
        return executor
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
            "hasExecutor" to (jsExecutor != null),
            "playerHash" to currentPlayerHash,
            "nFunctionAvailable" to jsExecutor?.nFunctionAvailable,
            "sigFunctionAvailable" to jsExecutor?.sigFunctionAvailable,
            "discoveredNFuncName" to jsExecutor?.discoveredNFuncName,
            "usingHardcodedMode" to jsExecutor?.usingHardcodedMode,
        )
    }
}
