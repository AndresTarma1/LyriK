//package com.example.melodist.utils.cipher
// TODO: In progress...
//import com.jetbrains.cef.JBCefApp
//import com.jetbrains.cef.JBCefBrowser
//import io.github.aakira.napier.Napier
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.suspendCancellableCoroutine
//import kotlinx.coroutines.withContext
//import org.cef.browser.CefBrowser
//import org.cef.browser.CefFrame
//import org.cef.browser.CefMessageRouter
//import org.cef.handler.CefMessageRouterHandler
//import java.util.concurrent.CompletableFuture
//import java.util.concurrent.TimeUnit
//import java.util.concurrent.atomic.AtomicInteger
//import kotlin.coroutines.resume
//import kotlin.coroutines.resumeWithException
//
//class JcefBotGuardExecutor {
//    private var browser: JBCefBrowser? = null
//    private var initialized = false
//    private val queryCounter = AtomicInteger(0)
//    private val pendingQueries = mutableMapOf<String, CompletableFuture<String>>()
//
//    @Synchronized
//    fun ensureInitialized() {
//        if (initialized) return
//
//        Napier.i("[JCEF] Initializing JCEF BotGuard executor...")
//
//        JBCefApp.getInstance()
//        val b = JBCefBrowser.create()
//        val router = CefMessageRouter.create()
//
//        router.addHandler(object : CefMessageRouterHandler {
//            override fun onQuery(
//                browser: CefBrowser?,
//                frame: CefFrame?,
//                queryId: Long,
//                request: String,
//                persistent: Boolean,
//                callback: CefMessageRouter.CefQueryCallback?,
//            ): Boolean {
//                Napier.d("[JCEF] Query received: ${request?.take(80)}")
//                val future = pendingQueries.remove(request)
//                if (future != null) {
//                    future.complete(request)
//                    callback?.success("")
//                } else {
//                    Napier.w("[JCEF] No pending future for query")
//                    callback?.success("")
//                }
//                return true
//            }
//
//            override fun onQueryCanceled(
//                browser: CefBrowser?,
//                frame: CefFrame?,
//                queryId: Long,
//            ) {
//                Napier.d("[JCEF] Query cancelled: id=$queryId")
//            }
//        }, true)
//
//        b.cefBrowser.client?.addMessageRouter(router)
//        b.loadURL("about:blank")
//        browser = b
//        initialized = true
//        Napier.i("[JCEF] JCEF BotGuard executor initialized")
//    }
//
//    suspend fun executeBotGuard(challengeJson: String): String = withContext(Dispatchers.IO) {
//        ensureInitialized()
//        val b = browser ?: throw IllegalStateException("JCEF browser not initialized")
//
//        val queryId = queryCounter.incrementAndGet()
//        val future = CompletableFuture<String>()
//
//        val js = """
//            (async function() {
//                try {
//                    var challengeData = $challengeJson;
//                    var interpreterJs = challengeData.interpreterJavascript.privateDoNotAccessOrElseSafeScriptWrappedValue;
//                    if (interpreterJs) { new Function(interpreterJs)(); }
//                    var bgVm = this[challengeData.globalName];
//                    if (!bgVm) throw new Error('VM not found: ' + challengeData.globalName);
//
//                    var bgResult = bgVm.a(challengeData.program, function(a,b,c,d) {
//                        window.__asyncSnapshot = a;
//                        window.__shutdown = b;
//                        window.__passEvent = c;
//                        window.__checkCamera = d;
//                    }, true, undefined, function() {}, [ [], [] ]);
//
//                    var syncResult = (bgResult && bgResult.length > 0) ? bgResult[0] : null;
//                    window.__webPoSignalOutput = [];
//
//                    await new Promise(function(resolve, reject) {
//                        window.__asyncSnapshot(function(r) {
//                            window.__botguardResponse = r;
//                            resolve(r);
//                        }, [null, null, window.__webPoSignalOutput, false]);
//                    });
//
//                    if (window.__webPoSignalOutput.length === 0 && syncResult && typeof syncResult === 'function') {
//                        var syncWpo = [];
//                        try {
//                            var syncR = syncResult([null, null, syncWpo, false]);
//                            if (syncWpo.length > 0) {
//                                window.__webPoSignalOutput = syncWpo;
//                                if (!window.__botguardResponse) window.__botguardResponse = syncR;
//                            }
//                        } catch(e) {}
//                    }
//
//                    var result = {
//                        botguardResponse: window.__botguardResponse,
//                        hasMinter: window.__webPoSignalOutput && window.__webPoSignalOutput.length > 0
//                    };
//                    cefQuery({request: JSON.stringify(result)});
//                } catch(e) {
//                    cefQuery({request: JSON.stringify({error: e.message, stack: e.stack})});
//                }
//            })();
//        """.trimIndent()
//
//        val requestId = "bg_$queryId"
//        pendingQueries[requestId] = future
//
//        b.cefBrowser.mainFrame.executeJavaScript(js, "about:blank", 0)
//
//        val result = try {
//            future.get(120, TimeUnit.SECONDS)
//        } catch (e: Exception) {
//            pendingQueries.remove(requestId)
//            throw PoTokenException("JCEF BotGuard execution timed out: ${e.message}")
//        }
//
//        result
//    }
//
//    suspend fun mintPoToken(integrityTokenJson: String, identifier: String): String = withContext(Dispatchers.IO) {
//        ensureInitialized()
//        val b = browser ?: throw IllegalStateException("JCEF browser not initialized")
//
//        val queryId = queryCounter.incrementAndGet()
//        val future = CompletableFuture<String>()
//
//        val js = """
//            (function() {
//                try {
//                    var it = $integrityTokenJson;
//                    var id = '$identifier';
//                    var wpo = window.__webPoSignalOutput;
//                    if (!wpo || wpo.length === 0) {
//                        cefQuery({request: JSON.stringify({error: 'webPoSignalOutput empty'})});
//                        return;
//                    }
//                    var getMinter = wpo[0];
//                    if (typeof getMinter !== 'function') {
//                        cefQuery({request: JSON.stringify({error: 'PMD:NotFunction'})});
//                        return;
//                    }
//                    var mintCallback = getMinter(it);
//                    if (mintCallback && typeof mintCallback.then === 'function') {
//                        cefQuery({request: JSON.stringify({error: 'Async getMinter'})});
//                        return;
//                    }
//                    if (typeof mintCallback !== 'function') {
//                        cefQuery({request: JSON.stringify({error: 'APF:NotFunction'})});
//                        return;
//                    }
//                    var poTokenBytes = mintCallback(id);
//                    if (poTokenBytes && typeof poTokenBytes.then === 'function') {
//                        cefQuery({request: JSON.stringify({error: 'Async mintCallback'})});
//                        return;
//                    }
//                    if (!(poTokenBytes instanceof Uint8Array)) {
//                        cefQuery({request: JSON.stringify({error: 'ODM:Invalid'})});
//                        return;
//                    }
//                    var arr = [];
//                    for (var i = 0; i < poTokenBytes.length; i++) arr.push(poTokenBytes[i]);
//                    cefQuery({request: JSON.stringify({token: arr.join(',')})});
//                } catch(e) {
//                    cefQuery({request: JSON.stringify({error: e.message})});
//                }
//            })();
//        """.trimIndent()
//
//        val requestId = "mint_$queryId"
//        pendingQueries[requestId] = future
//
//        b.cefBrowser.mainFrame.executeJavaScript(js, "about:blank", 0)
//
//        try {
//            future.get(30, TimeUnit.SECONDS)
//        } catch (e: Exception) {
//            pendingQueries.remove(requestId)
//            throw PoTokenException("JCEF minting timed out: ${e.message}")
//        }
//    }
//
//    fun dispose() {
//        browser?.let {
//            it.cefBrowser.client?.dispose()
//            it.dispose()
//        }
//        browser = null
//        initialized = false
//        Napier.i("[JCEF] JCEF BotGuard executor disposed")
//    }
//}
