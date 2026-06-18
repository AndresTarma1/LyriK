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
     * @param videoId   identifier the *streaming* (stream URL `pot=`) poToken is bound to.
     * @param sessionId identifier the *player request* poToken is bound to
     *                  (visitorData when logged out, dataSyncId when logged in).
     * @return both tokens, or null if generation failed (caller should fall back).
     */
    suspend fun getWebClientPoToken(videoId: String, sessionId: String): PoTokenResult?
}
