package com.example.melodist.utils.cipher

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.cef.CefApp
import org.cef.CefClient
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefCommandLine
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefAppHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefMessageRouterHandlerAdapter
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Runs the browser-dependent parts of BotGuard inside the JCEF Chromium shipped with
 * the JetBrains Runtime. A real browser context is required because BotGuard's minter
 * factory probes canvas/WebGL/timing APIs that headless JS engines (GraalJS) lack.
 *
 * The helper functions (`runBotGuard`, `createPoTokenMinter`, `obtainPoToken`) are
 * defined in `assets/po_token.html`, which we load into an offscreen browser. JS results
 * are returned to Kotlin through the `cefQuery` message-router bridge, correlated by reqId.
 */
object JcefBotGuardExecutor {
    @Volatile private var initialized = false
    private var client: CefClient? = null
    private var browser: CefBrowser? = null

    private val pending = ConcurrentHashMap<String, CompletableDeferred<JsResult>>()
    private val reqCounter = AtomicLong(0)

    private data class JsResult(val ok: Boolean, val data: Map<String, String>, val error: String?)

    @Synchronized
    fun ensureInitialized(htmlContent: String) {
        if (initialized) return
        Napier.i("[JCEF] Initializing BotGuard executor...")

        if (!CefApp.startup(arrayOf())) {
            throw PoTokenException("CefApp.startup failed (JCEF not available in this runtime)")
        }

        // The CEF runtime (libcef.dll, jcef_helper.exe, *.pak, icudtl.dat) lives in <jbr>/bin.
        // Since the app runs on the jbr_jcef runtime, java.home points there. We MUST set the
        // subprocess path explicitly, otherwise CEF relaunches java.exe for its helper processes
        // (causing the "/prefetch:N main class not found" errors).
        val cefBin = File(System.getProperty("java.home"), "bin")

        // Disable GPU/sandbox: this is a headless offscreen browser used only to run JS, and the
        // GPU subprocess is unavailable in that context ("GPU process isn't usable" FATAL).
        CefApp.addAppHandler(object : CefAppHandlerAdapter(arrayOf()) {
            override fun onBeforeCommandLineProcessing(processType: String?, commandLine: CefCommandLine?) {
                commandLine?.appendSwitch("no-sandbox")
                commandLine?.appendSwitch("disable-gpu")
                commandLine?.appendSwitch("disable-gpu-compositing")
                commandLine?.appendSwitch("disable-software-rasterizer")
                commandLine?.appendSwitch("disable-extensions")
            }
        })

        val settings = CefSettings().apply {
            windowless_rendering_enabled = true
            log_severity = CefSettings.LogSeverity.LOGSEVERITY_DISABLE
            cache_path = File(System.getProperty("user.home"), ".melodist/jcef-cache").absolutePath
            browser_subprocess_path = File(cefBin, "jcef_helper.exe").absolutePath
            resources_dir_path = cefBin.absolutePath
            locales_dir_path = File(cefBin, "locales").absolutePath
        }

        val app = CefApp.getInstance(settings)
        val cefClient = app.createClient()

        val router = CefMessageRouter.create()
        router.addHandler(object : CefMessageRouterHandlerAdapter() {
            override fun onQuery(
                browser: CefBrowser?,
                frame: CefFrame?,
                queryId: Long,
                request: String?,
                persistent: Boolean,
                callback: CefQueryCallback?,
            ): Boolean {
                if (request == null) {
                    callback?.failure(-1, "null request")
                    return true
                }
                try {
                    val obj = Json.parseToJsonElement(request).jsonObject
                    val reqId = obj["reqId"]?.jsonPrimitive?.content
                    if (reqId != null) {
                        val ok = obj["ok"]?.jsonPrimitive?.content == "true"
                        val error = obj["error"]?.jsonPrimitive?.contentOrNull()
                        val data = obj.filterKeys { it != "reqId" && it != "ok" && it != "error" }
                            .mapValues { it.value.jsonPrimitive.content }
                        pending.remove(reqId)?.complete(JsResult(ok, data, error))
                        callback?.success("")
                        return true
                    }
                } catch (e: Exception) {
                    Napier.w("[JCEF] Failed to parse query: ${e.message}")
                }
                callback?.failure(-1, "unhandled")
                return true
            }
        }, true)
        cefClient.addMessageRouter(router)

        val loadLatch = CountDownLatch(1)
        cefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadingStateChange(
                browser: CefBrowser?,
                isLoading: Boolean,
                canGoBack: Boolean,
                canGoForward: Boolean,
            ) {
                if (!isLoading) loadLatch.countDown()
            }
        })

        val tmp = File.createTempFile("melodist_potoken", ".html")
        tmp.writeText(htmlContent)
        tmp.deleteOnExit()

        @Suppress("DEPRECATION")
        val cefBrowser = cefClient.createBrowser(tmp.toURI().toString(), true, false)
        cefBrowser.createImmediately()

        if (!loadLatch.await(30, TimeUnit.SECONDS)) {
            throw PoTokenException("JCEF: timed out loading BotGuard page")
        }

        client = cefClient
        browser = cefBrowser
        initialized = true
        Napier.i("[JCEF] BotGuard executor initialized")
    }

    /** Runs BotGuard; stores webPoSignalOutput in the page and returns the botguardResponse. */
    suspend fun runBotGuard(challengeJson: String): String? {
        val js = { reqId: String ->
            """
            (async function() {
                try {
                    var challengeData = $challengeJson;
                    var res = await runBotGuard(challengeData);
                    window.__wpo = res.webPoSignalOutput;
                    ${query(reqId, """{
                        ok: true,
                        botguardResponse: res.botguardResponse,
                        hasMinter: (res.webPoSignalOutput && res.webPoSignalOutput.length > 0) ? 'true' : 'false'
                    }""")}
                } catch(e) { ${queryError(reqId)} }
            })();
            """.trimIndent()
        }
        val result = exec(js)
        if (!result.ok) throw PoTokenException("BotGuard failed: ${result.error}")
        if (result.data["hasMinter"] != "true") {
            Napier.w("[JCEF] webPoSignalOutput empty after BotGuard")
        }
        return result.data["botguardResponse"]
    }

    /** Creates the poToken minter from webPoSignalOutput + integrity token. Call once per challenge. */
    suspend fun createMinter(integrityTokenJsArray: String) {
        val js = { reqId: String ->
            """
            (async function() {
                try {
                    await createPoTokenMinter(window.__wpo, $integrityTokenJsArray);
                    ${query(reqId, "{ ok: true }")}
                } catch(e) { ${queryError(reqId)} }
            })();
            """.trimIndent()
        }
        val result = exec(js)
        if (!result.ok) throw PoTokenException("createMinter failed: ${result.error}")
    }

    /** Mints a poToken bound to [identifierJsArray]; returns the bytes as a CSV string. */
    suspend fun mintToken(identifierJsArray: String): String? {
        val js = { reqId: String ->
            """
            (async function() {
                try {
                    var tok = await obtainPoToken($identifierJsArray);
                    var arr = [];
                    for (var i = 0; i < tok.length; i++) arr.push(tok[i]);
                    ${query(reqId, "{ ok: true, token: arr.join(',') }")}
                } catch(e) { ${queryError(reqId)} }
            })();
            """.trimIndent()
        }
        val result = exec(js)
        if (!result.ok) {
            Napier.e("[JCEF] mintToken failed: ${result.error}")
            return null
        }
        return result.data["token"]
    }

    private suspend fun exec(jsBuilder: (String) -> String): JsResult {
        val b = browser ?: throw PoTokenException("JCEF browser not initialized")
        val reqId = "q" + reqCounter.incrementAndGet()
        val deferred = CompletableDeferred<JsResult>()
        pending[reqId] = deferred
        try {
            b.executeJavaScript(jsBuilder(reqId), b.url, 0)
            return withTimeout(60_000) { deferred.await() }
        } finally {
            pending.remove(reqId)
        }
    }

    /** Builds a cefQuery call that reports a JS object literal back to Kotlin, tagged with reqId. */
    private fun query(reqId: String, payloadObjectLiteral: String): String {
        // Merge reqId into the payload and stringify. Values are coerced to strings on the JS side.
        return """
            (function(p){
                p.reqId = '$reqId';
                var out = {};
                for (var k in p) { out[k] = (p[k] === undefined || p[k] === null) ? '' : String(p[k]); }
                window.cefQuery({ request: JSON.stringify(out), persistent: false, onSuccess: function(){}, onFailure: function(){} });
            })($payloadObjectLiteral);
        """.trimIndent()
    }

    private fun queryError(reqId: String): String {
        return "window.cefQuery({ request: JSON.stringify({ reqId: '$reqId', ok: 'false', error: String((e && e.message) || e) }), persistent: false, onSuccess: function(){}, onFailure: function(){} });"
    }

    fun dispose() {
        browser?.close(true)
        client?.dispose()
        browser = null
        client = null
        initialized = false
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content
