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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.util.Base64
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess

data class PoTokenResult(
    val playerRequestPoToken: String,
    val streamingDataPoToken: String,
)

class PoTokenException(message: String) : Exception(message)

    object PoTokenGenerator {
        private const val TAG = "PoTokenGenerator"
    private const val WAA_URL = "https://www.youtube.com/api/jnn/v1"
    private const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
    private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"

    private val httpClient = HttpClient(CIO) {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 5000
            requestTimeoutMillis = 15000
        }
    }

    private var solverInitialized = false

    private val polyglot by lazy {
        Context.newBuilder("js")
            .allowHostAccess(HostAccess.ALL)
            .allowHostClassLookup { true }
            .build()
    }

    private fun eval(code: String) {
        polyglot.eval("js", code)
    }

    private fun evalS(code: String): String {
        val result = polyglot.eval("js", code)
        return if (result.isString) result.asString() else result.toString()
    }

    private suspend fun ensureSolverLoaded() {
        if (solverInitialized) return
        Napier.i("[PoToken] Loading solver JS into GraalJS Polyglot...")

        try {
            // Polyglot context uses Java interop - crypto.subtle will be polyfilled with Java MessageDigest
            eval("var window = this; var globalThis = this; var self = this; var navigator = {}; var document = {};")

            // Crypto polyfill: crypto.getRandomValues (pure JS) + crypto.subtle.digest (Java MessageDigest via host interop)
            eval("""
                (function() {
                    try { if (typeof crypto === 'undefined') crypto = {}; } catch(e) { crypto = {}; }
                    if (typeof crypto.getRandomValues !== 'function') {
                        crypto.getRandomValues = function(array) {
                            for (var i = 0; i < array.length; i++) array[i] = Math.floor(Math.random() * 256);
                            return array;
                        };
                    }
                    // Use Java MessageDigest for crypto.subtle via GraalVM host interop
                    if (!crypto.subtle) {
                        try {
                            var MessageDigest = Java.type('java.security.MessageDigest');
                            crypto.subtle = {};
                            crypto.subtle.digest = function(algorithm, data) {
                                var algo = typeof algorithm === 'string' ? algorithm : (algorithm.name || 'SHA-256');
                                try {
                                    var md = MessageDigest.getInstance(algo);
                                    var sourceBytes = (data instanceof ArrayBuffer) ? new Uint8Array(data) : new Uint8Array(data);
                                    var javaBytes = Java.to(sourceBytes, 'byte[]');
                                    var digest = md.digest(javaBytes);
                                    var result = new Uint8Array(digest.length);
                                    for (var i = 0; i < digest.length; i++) result[i] = digest[i] & 0xFF;
                                    return Promise.resolve(result.buffer);
                                } catch(e) {
                                    return Promise.reject(new Error('Digest failed: ' + e.message));
                                }
                            };
                        } catch(e) {
                            // Java interop not available, fall back to simple JS implementation
                            crypto.subtle = {};
                            crypto.subtle.digest = function(algorithm, data) {
                                return Promise.resolve(new ArrayBuffer(32));
                            };
                        }
                    }
                })();
            """.trimIndent())

            eval(readAsset("solver/meriyah.js"))
            eval("var meriyah = this.meriyah;")

            eval(readAsset("solver/astring.js"))
            eval("var astring = this.astring;")

            eval(readAsset("solver/yt.solver.core.js"))

            val botGuardJs = extractFunctionsFromHtml(readAsset("po_token.html"))
            eval(botGuardJs)

            eval("""
                var __webPoSignalOutput = null;

                function runBotGuardSync(challengeData) {
                    var interpreterJavascript = challengeData.interpreterJavascript.privateDoNotAccessOrElseSafeScriptWrappedValue;
                    if (interpreterJavascript) {
                        new Function(interpreterJavascript)();
                    }

                    var bgVm = this[challengeData.globalName];
                    if (!bgVm) throw new Error('VM not found: ' + challengeData.globalName);
                    if (!bgVm.a) throw new Error('VM has no .a method');

                    var bgVmFunctions = null;
                    var vmResult = bgVm.a(challengeData.program, function(a, b, c, d) {
                        bgVmFunctions = { asyncSnapshotFunction: a, shutdownFunction: b, passEventFunction: c, checkCameraFunction: d };
                    }, true, undefined, function() {}, [ [], [] ]);

                    if (!bgVmFunctions || !bgVmFunctions.asyncSnapshotFunction) {
                        throw new Error('asyncSnapshotFunction not available');
                    }

                    var syncSnapshot = (vmResult && vmResult.length > 0) ? vmResult[0] : null;
                    var webPoSignalOutput = [];
                    var response = null;

                    bgVmFunctions.asyncSnapshotFunction(function(r) {
                        response = r;
                    }, [null, null, webPoSignalOutput, false]);

                    // If async didn't populate wpo, try sync version
                    if (syncSnapshot && typeof syncSnapshot === 'function' && webPoSignalOutput.length === 0) {
                        var syncWpo = [];
                        try {
                            var syncResponse = syncSnapshot([null, null, syncWpo, false]);
                            if (syncWpo.length > 0) {
                                webPoSignalOutput = syncWpo;
                                if (!response) response = syncResponse;
                            }
                        } catch (e) {
                            // sync failed, keep async result
                        }
                    }

                    __webPoSignalOutput = webPoSignalOutput;
                    return response;
                }

                function mintPoTokenSync(integrityToken, identifier) {
                    var webPoSignalOutput = __webPoSignalOutput;
                    if (!webPoSignalOutput || webPoSignalOutput.length === 0) {
                        throw new Error('webPoSignalOutput not initialized. JCEF-based BotGuard executor required - GraalJS lacks browser APIs (canvas, WebGL) needed by BotGuard minter');
                    }
                    var getMinter = webPoSignalOutput[0];
                    if (!getMinter) throw new Error('PMD:Undefined');
                    if (typeof getMinter !== 'function') throw new Error('PMD:NotFunction');

                    var mintCallback = getMinter(integrityToken);
                    if (mintCallback && typeof mintCallback.then === 'function') {
                        throw new Error('Async getMinter not supported');
                    }
                    if (typeof mintCallback !== 'function') {
                        throw new Error('APF:NotFunction');
                    }

                    var poTokenBytes = mintCallback(identifier);
                    if (poTokenBytes && typeof poTokenBytes.then === 'function') {
                        throw new Error('Async mintCallback not supported');
                    }
                    if (!(poTokenBytes instanceof Uint8Array)) {
                        throw new Error('ODM:Invalid - result is not Uint8Array');
                    }
                    return poTokenBytes;
                }
            """.trimIndent())

            solverInitialized = true
            Napier.i("[PoToken] Solver JS loaded successfully")
        } catch (e: Exception) {
            Napier.e("[PoToken] Failed to load solver JS: ${e.message}")
            throw CipherException("Failed to load solver JS: ${e.message}")
        }
    }

    suspend fun getWebClientPoToken(videoId: String, sessionId: String): PoTokenResult? {
        Napier.i("[PoToken] Generating PoToken for videoId=$videoId sessionId=${sessionId.take(8)}...")
        return try {
            ensureSolverLoaded()

            val challengeJson = fetchAndParseChallenge()
            Napier.i("[PoToken] Challenge parsed, running BotGuard...")

            val botguardResponse = executeBotGuardSynchronous(challengeJson)
            Napier.d("[PoToken] BotGuard executed, response=${botguardResponse?.take(50)}")

            val integrityToken = fetchIntegrityToken(botguardResponse)
            Napier.d("[PoToken] Integrity token obtained, size=${integrityToken.size}")

            val streamingToken = mintToken(integrityToken, sessionId)
            val playerToken = mintToken(integrityToken, videoId)

            if (streamingToken == null || playerToken == null) {
                Napier.e("[PoToken] Token minting returned null")
                return null
            }

            val result = PoTokenResult(playerToken, streamingToken)
            Napier.i("[PoToken] PoToken generated successfully")
            result
        } catch (e: Exception) {
            Napier.e("[PoToken] Failed to generate PoToken: ${e.message}")
            null
        }
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

    private fun executeBotGuardSynchronous(challengeJson: String): String? {
        val js = """
            (function() {
                try {
                    var challengeData = $challengeJson;
                    var botguardResponse = runBotGuardSync(challengeData);
                    var wpoLen = __webPoSignalOutput ? __webPoSignalOutput.length : 0;
                    return JSON.stringify({botguardResponse: botguardResponse, wpoLen: wpoLen});
                } catch(e) {
                    return JSON.stringify({error: e.message, stack: e.stack});
                }
            })();
        """.trimIndent()

        val result = evalS(js)

        val parsed = Json.parseToJsonElement(result).jsonObject
        val error = parsed["error"]?.jsonPrimitive?.contentOrNull
        if (error != null) {
            throw PoTokenException("BotGuard execution failed: $error")
        }

        val wpoLen = parsed["wpoLen"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        if (wpoLen == 0) {
            Napier.w("[PoToken] BotGuard: webPoSignalOutput is empty - PoToken will require JCEF-based executor")
        }

        return parsed["botguardResponse"]?.jsonPrimitive?.contentOrNull
    }

    private suspend fun fetchIntegrityToken(botguardResponse: String?): ByteArray {
        if (botguardResponse == null) throw PoTokenException("botguardResponse is null")

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
        return parseIntegrityToken(response)
    }

    private fun parseIntegrityToken(rawData: String): ByteArray {
        val arr = Json.parseToJsonElement(rawData).jsonArray
        val base64 = arr[0].jsonPrimitive.content
        return base64ToByteArray(base64)
    }

    private fun mintToken(integrityToken: ByteArray, identifier: String): String? {
        val integrityTokenJs = newUint8Array(integrityToken)
        val identifierJs = newUint8Array(identifier.toByteArray())

        val wpoLen = evalS("(__webPoSignalOutput && __webPoSignalOutput.length) || 0").toIntOrNull() ?: 0
        if (wpoLen == 0) {
            Napier.w("[PoToken] Mint skipped - webPoSignalOutput empty, JCEF executor required for BotGuard minter")
            return null
        }

        val js = """
            (function() {
                try {
                    var it = $integrityTokenJs;
                    var id = $identifierJs;
                    var poTokenBytes = mintPoTokenSync(it, id);
                    var result = [];
                    for (var i = 0; i < poTokenBytes.length; i++) {
                        result.push(poTokenBytes[i]);
                    }
                    return result.join(',');
                } catch(e) {
                    return JSON.stringify({error: e.message, stack: e.stack});
                }
            })();
        """.trimIndent()

        val result = evalS(js)

        if (result.startsWith("{\"error\"")) {
            Napier.e("[PoToken] Mint token failed: $result")
            return null
        }

        return poTokenBytesToBase64(result)
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

    private fun extractFunctionsFromHtml(html: String): String {
        val scriptStart = html.indexOf("<script>")
        val scriptEnd = html.indexOf("</script>")
        if (scriptStart < 0 || scriptEnd < 0) return ""
        return html.substring(scriptStart + 8, scriptEnd)
    }
    //endregion
}
