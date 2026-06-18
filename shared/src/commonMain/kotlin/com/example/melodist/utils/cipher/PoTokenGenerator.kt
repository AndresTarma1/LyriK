package com.example.melodist.utils.cipher

data class PoTokenResult(
    val playerRequestPoToken: String,
    val streamingDataPoToken: String,
)

class PoTokenException(message: String) : Exception(message)

/**
 * Generates WEB/WEB_REMIX poTokens (BotGuard / WAA integrity tokens).
 *
 * The actual implementation lives in the JVM source set because minting a poToken
 * requires real browser APIs (canvas/WebGL) that only an embedded Chromium (JCEF,
 * shipped with the JetBrains Runtime) can provide. A pure-JS engine such as GraalJS
 * can run the BotGuard interpreter far enough to obtain the integrity-token request,
 * but never populates `webPoSignalOutput`, so it cannot mint the final token.
 */
expect object PoTokenGenerator {
    /**
 * Generates WEB/WEB_REMIX poTokens for web client requests.
 *
 * @return A [PoTokenResult] with both tokens, or `null` if generation failed.
 */
    suspend fun getWebClientPoToken(videoId: String, sessionId: String): PoTokenResult?
}
