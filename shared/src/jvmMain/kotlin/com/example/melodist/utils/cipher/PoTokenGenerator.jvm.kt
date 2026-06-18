package com.example.melodist.utils.cipher

import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.userAgent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

/**
 * JVM implementation of [PoTokenGenerator].
 *
 * Pipeline (the BotGuard "Web Anti-Abuse" / WAA protocol):
 *   1. POST /Create      → scrambled BotGuard challenge        (HTTP, here)
 *   2. run BotGuard       → botguardResponse + webPoSignalOutput (JCEF browser)
 *   3. POST /GenerateIT  → integrity token                      (HTTP, here)
 *   4. create minter      → from webPoSignalOutput + integrity  (JCEF browser)
 *   5. mint(identifier)   → poToken bytes                        (JCEF browser)
 *
 * Steps 2/4/5 need real browser APIs, so they run inside the JCEF Chromium
 * via [JcefBotGuardExecutor]. Everything else is plain Kotlin/Ktor.
 */
actual object PoTokenGenerator {
    private const val WAA_URL = "https://www.youtube.com/api/jnn/v1"
    private const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
    private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"

    private val httpClient = HttpClient(CIO) {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 5000
            requestTimeoutMillis = 15000
        }
    }

    actual suspend fun getWebClientPoToken(videoId: String, sessionId: String): PoTokenResult? {
        Napier.i("[PoToken] Generating PoToken for videoId=$videoId sessionId=${sessionId.take(8)}...")
        return try {
            JcefBotGuardExecutor.ensureInitialized(readAsset("po_token.html"))

            val challengeJson = fetchAndParseChallenge()
            Napier.i("[PoToken] Challenge parsed, running BotGuard in JCEF...")

            val botguardResponse = JcefBotGuardExecutor.runBotGuard(challengeJson)
            if (botguardResponse == null) {
                Napier.e("[PoToken] BotGuard returned no response")
                return null
            }
            Napier.d("[PoToken] BotGuard executed, response=${botguardResponse.take(50)}")

            val integrityToken = fetchIntegrityToken(botguardResponse)
            Napier.d("[PoToken] Integrity token obtained, size=${integrityToken.size}")

            JcefBotGuardExecutor.createMinter(newUint8Array(integrityToken))

            // The session-bound token MUST be minted first (once), then the videoId-bound one.
            // Binding/assignment matches Metrolist (the working reference):
            //   - the /player request carries the SESSION-bound token,
            //   - the stream URL's pot= carries the VIDEO_ID-bound token.
            // Swapping these makes the CDN reject every web-client stream with HTTP 403.
            val sessionBoundToken = mintBase64(sessionId)
            val videoBoundToken = mintBase64(videoId)
            if (sessionBoundToken == null || videoBoundToken == null) {
                Napier.e("[PoToken] Token minting returned null")
                return null
            }

            Napier.i("[PoToken] PoToken generated successfully")
            PoTokenResult(
                playerRequestPoToken = sessionBoundToken,
                streamingDataPoToken = videoBoundToken,
            )
        } catch (e: Exception) {
            Napier.e("[PoToken] Failed to generate PoToken: ${e.message}")
            null
        }
    }

    private suspend fun mintBase64(identifier: String): String? {
        val csv = JcefBotGuardExecutor.mintToken(newUint8Array(identifier.toByteArray())) ?: return null
        return poTokenBytesToBase64(csv)
    }

    private suspend fun fetchAndParseChallenge(): String {
        val response = httpClient.post("$WAA_URL/Create") {
            contentType(ContentType.parse("application/json+protobuf"))
            userAgent(USER_AGENT)
            headers {
                append("x-goog-api-key", GOOGLE_API_KEY)
                append("x-user-agent", "grpc-web-javascript/0.1")
            }
            setBody("""["$REQUEST_KEY"]""")
        }.bodyAsText()

        Napier.d("[PoToken] WAA Create response length=${response.length}")
        return parseChallengeData(response)
    }

    private fun parseChallengeData(rawChallengeData: String): String {
        val scrambled = Json.parseToJsonElement(rawChallengeData).jsonArray

        val challengeData = if (scrambled.size > 1 && scrambled[1].jsonPrimitive.isString) {
            val descrambled = descramble(scrambled[1].jsonPrimitive.content)
            Json.parseToJsonElement(descrambled).jsonArray
        } else {
            scrambled[0].jsonArray
        }

        val messageId = challengeData[0].jsonPrimitive.content
        val interpreterHash = challengeData[3].jsonPrimitive.content
        val program = challengeData[4].jsonPrimitive.content
        val globalName = challengeData[5].jsonPrimitive.content
        val clientExperimentsStateBlob = challengeData.getOrNull(7)?.jsonPrimitive?.content

        val privateDoNotAccessOrElseSafeScriptWrappedValue = challengeData[1]
            .takeIf { it !is JsonNull }
            ?.jsonArray
            ?.find { it.jsonPrimitive.isString }
        val privateDoNotAccessOrElseTrustedResourceUrlWrappedValue = challengeData[2]
            .takeIf { it !is JsonNull }
            ?.jsonArray
            ?.find { it.jsonPrimitive.isString }

        return Json.encodeToString(
            JsonObject.serializer(), JsonObject(
                mapOf(
                    "messageId" to JsonPrimitive(messageId),
                    "interpreterJavascript" to JsonObject(
                        mapOf(
                            "privateDoNotAccessOrElseSafeScriptWrappedValue" to (privateDoNotAccessOrElseSafeScriptWrappedValue
                                ?: JsonNull),
                            "privateDoNotAccessOrElseTrustedResourceUrlWrappedValue" to (privateDoNotAccessOrElseTrustedResourceUrlWrappedValue
                                ?: JsonNull)
                        )
                    ),
                    "interpreterHash" to JsonPrimitive(interpreterHash),
                    "program" to JsonPrimitive(program),
                    "globalName" to JsonPrimitive(globalName),
                    "clientExperimentsStateBlob" to JsonPrimitive(clientExperimentsStateBlob ?: "")
                )
            )
        )
    }

    private suspend fun fetchIntegrityToken(botguardResponse: String): ByteArray {
        val response = httpClient.post("$WAA_URL/GenerateIT") {
            contentType(ContentType.parse("application/json+protobuf"))
            userAgent(USER_AGENT)
            headers {
                append("x-goog-api-key", GOOGLE_API_KEY)
                append("x-user-agent", "grpc-web-javascript/0.1")
            }
            setBody("""["$REQUEST_KEY","$botguardResponse"]""")
        }.bodyAsText()

        Napier.d("[PoToken] WAA GenerateIT response length=${response.length}")
        val arr = Json.parseToJsonElement(response).jsonArray
        return base64ToByteArray(arr[0].jsonPrimitive.content)
    }

    //region Utility functions

    private fun descramble(scrambledChallenge: String): String {
        val bytes = base64ToByteArray(scrambledChallenge)
        return String(bytes.map { (it.toInt() + 97).toByte() }.toByteArray(), Charsets.UTF_8)
    }

    private fun base64ToByteArray(base64: String): ByteArray {
        val normalized = base64
            .replace('-', '+')
            .replace('_', '/')
            .replace('.', '=')
        return Base64.getDecoder().decode(normalized)
    }

    private fun newUint8Array(bytes: ByteArray): String {
        return "new Uint8Array([" + bytes.joinToString(separator = ",") { it.toUByte().toString() } + "])"
    }

    private fun poTokenBytesToBase64(poTokenCsv: String): String {
        return Base64.getEncoder().encodeToString(
            poTokenCsv.split(",")
                .map { it.trim().toUByte().toByte() }
                .toByteArray()
        )
            .replace("+", "-")
            .replace("/", "_")
    }

    private fun readAsset(path: String): String {
        val fullPath = "com/example/melodist/utils/cipher/assets/$path"
        val resource = javaClass.classLoader?.getResource(fullPath)
            ?: throw CipherException("Asset not found: $fullPath")
        return resource.readText()
    }
    //endregion
}
