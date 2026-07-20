package com.example.musicApp.utils.cipher

import io.github.aakira.napier.Napier
import javax.script.ScriptEngineManager
import javax.script.SimpleBindings

class JsExecutor(
    private val playerJs: String,
    private val sigInfo: FunctionNameExtractor.SigFunctionInfo?,
    private val nFuncInfo: FunctionNameExtractor.NFunctionInfo?,
) {
    var nFunctionAvailable: Boolean = false
        private set
    var sigFunctionAvailable: Boolean = false
        private set
    var discoveredNFuncName: String? = null
        private set
    var usingHardcodedMode: Boolean = false
        private set

    private val engine = ScriptEngineManager().getEngineByName("graal.js")
        ?: throw CipherException("GraalJS engine not available")

    init {
        Napier.i("[JsExecutor] Initializing with GraalJS, engine=${engine::class.simpleName}")
        evaluate()
    }

    private fun evaluate() {
        val sigFuncName = sigInfo?.name
        val nFuncName = nFuncInfo?.name
        val nArrayIdx = nFuncInfo?.arrayIndex
        val isHardcoded = sigInfo?.isHardcoded == true || nFuncInfo?.isHardcoded == true
        usingHardcodedMode = isHardcoded

        val exports = buildList {
            if (sigFuncName != null) {
                val sigConstArgs = sigInfo.constantArgs
                val preprocessFunc = sigInfo.preprocessFunc
                val preprocessArgs = sigInfo.preprocessArgs

                if (!sigConstArgs.isNullOrEmpty() && preprocessFunc != null && !preprocessArgs.isNullOrEmpty()) {
                    val mainArgsStr = sigConstArgs.joinToString(", ")
                    val prepArgsStr = preprocessArgs.joinToString(", ")
                    add("window._cipherSigFunc = function(sig) { return $sigFuncName($mainArgsStr, $preprocessFunc($prepArgsStr, sig)); };")
                } else if (!sigConstArgs.isNullOrEmpty()) {
                    val argsStr = sigConstArgs.joinToString(", ")
                    add("window._cipherSigFunc = function(sig) { return $sigFuncName($argsStr, sig); };")
                } else if (isHardcoded) {
                    add("window._cipherSigFunc = typeof $sigFuncName !== 'undefined' ? $sigFuncName : null;")
                } else {
                    add("window._cipherSigFunc = typeof $sigFuncName !== 'undefined' ? $sigFuncName : null;")
                }
            }
            if (nFuncName != null) {
                val nConstArgs = nFuncInfo.constantArgs
                if (!nConstArgs.isNullOrEmpty()) {
                    val argsStr = nConstArgs.joinToString(", ")
                    add("window._nTransformFunc = function(n) { return $nFuncName($argsStr, n); };")
                } else {
                    val nExpr = if (nArrayIdx != null) "$nFuncName[$nArrayIdx]" else nFuncName
                    add("window._nTransformFunc = typeof $nFuncName !== 'undefined' ? $nExpr : null;")
                }
            }
        }

        val exportCode = if (exports.isNotEmpty()) exports.joinToString(" ") else ""

        val modifiedJs = if (exportCode.isNotEmpty()) {
            val injected = playerJs.replace("})(_yt_player);", "$exportCode })(_yt_player);")
            if (injected == playerJs) playerJs + "\n" + exportCode else injected
        } else {
            playerJs
        }

        val wrapperJs = """
            var window = this;
            $modifiedJs
            $exportCode
            function discoverAndInit() {
                var sigFuncName = "";
                var nFuncName = "";
                var info = "";

                if (typeof window._cipherSigFunc === 'function') {
                    sigFuncName = "exported_sig_func";
                }

                if (typeof window._nTransformFunc === 'function') {
                    try {
                        var testInput = "KdrqFlzJXl9EcCwlmEy";
                        var testResult = window._nTransformFunc(testInput);
                        if (typeof testResult === 'string' && testResult !== testInput && testResult.length >= 5 && /^[a-zA-Z0-9_-]+$/.test(testResult)) {
                            nFuncName = "exported_n_func";
                            info = "export_valid";
                        } else {
                            window._nTransformFunc = null;
                        }
                    } catch(e) {
                        window._nTransformFunc = null;
                    }
                }

                if (!nFuncName) {
                    try {
                        var testInput = "T2Xw3pWQ_Wk0xbOg";
                        var keys = Object.getOwnPropertyNames(window);
                        for (var i = 0; i < keys.length; i++) {
                            try {
                                var key = keys[i];
                                if (key.startsWith("webkit") || key.startsWith("on") || key === "window" || key === "self") continue;
                                var fn = window[key];
                                if (typeof fn !== 'function' || fn.length !== 1) continue;
                                var result = fn(testInput);
                                if (typeof result === 'string' && result !== testInput && result.length >= 5 && /^[a-zA-Z0-9_-]+$/.test(result)) {
                                    window._nTransformFunc = fn;
                                    nFuncName = key;
                                    break;
                                }
                            } catch(e) {}
                        }
                    } catch(e) {}
                }

                return JSON.stringify({sigFuncName: sigFuncName, nFuncName: nFuncName, info: info});
            }
            discoverAndInit();
        """.trimIndent()

        val result = engine.eval(wrapperJs, SimpleBindings()).toString()
        val parsed = parseJsonResult(result)

        sigFunctionAvailable = parsed["sigFuncName"]?.isNotEmpty() == true
        val nName = parsed["nFuncName"]
        if (!nName.isNullOrEmpty()) {
            discoveredNFuncName = nName
            nFunctionAvailable = true
        }
        Napier.i("[JsExecutor] sig=$sigFunctionAvailable n=$nFunctionAvailable nFuncName=$discoveredNFuncName")
    }

    fun deobfuscateSignature(obfuscatedSig: String): String {
        if (sigInfo == null) throw CipherException("Signature function info not available")
        val constArgJs = if (sigInfo.constantArg != null) sigInfo.constantArg.toString() else "null"

        val js = """
            var window = this;
            (function() {
                var func = window._cipherSigFunc;
                if (typeof func !== 'function') throw new Error('Sig func not found');
                var result;
                if (func.length === 1) {
                    result = func("$obfuscatedSig");
                } else if ($constArgJs !== null) {
                    result = func($constArgJs, "$obfuscatedSig");
                } else {
                    result = func("$obfuscatedSig");
                }
                if (result === undefined || result === null) throw new Error('Sig func returned null');
                return String(result);
            })();
        """.trimIndent()

        return engine.eval(js, SimpleBindings()).toString()
    }

    fun transformN(nValue: String): String {
        if (!nFunctionAvailable) throw CipherException("N-transform function not discovered")

        val js = """
            var window = this;
            (function() {
                var func = window._nTransformFunc;
                if (typeof func !== 'function') throw new Error('N-transform func not found');
                var result = func("$nValue");
                if (result === undefined || result === null) throw new Error('N-transform returned null');
                return String(result);
            })();
        """.trimIndent()

        return engine.eval(js, SimpleBindings()).toString()
    }

    fun close() {
        // GraalJS engine is cached globally, nothing to clean up per instance
    }

    private fun parseJsonResult(json: String): Map<String, String> {
        return try {
            val cleaned = json.removeSurrounding("\"").replace("\\\"", "\"")
            val map = mutableMapOf<String, String>()
            val pairs = cleaned.removeSurrounding("{", "}").split(",")
            for (pair in pairs) {
                val parts = pair.split(":", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim().removeSurrounding("\"")
                    val value = parts[1].trim().removeSurrounding("\"")
                    map[key] = value
                }
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    companion object {
        suspend fun create(
            playerJs: String,
            sigInfo: FunctionNameExtractor.SigFunctionInfo?,
            nFuncInfo: FunctionNameExtractor.NFunctionInfo? = null,
        ): JsExecutor {
            Napier.i("[JsExecutor] Creating with GraalJS, playerJs=${playerJs.length} chars")
            return JsExecutor(playerJs, sigInfo, nFuncInfo)
        }
    }
}
