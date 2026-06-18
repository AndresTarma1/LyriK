package com.example.melodist.utils.cipher

import io.github.aakira.napier.Napier
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager

/**
 * Signature ("sig") and throttling ("n") deobfuscation backed by the yt-dlp/ejs solver
 * (`assets/solver/yt.solver.core.js`, built from https://github.com/yt-dlp/ejs).
 *
 * Unlike the regex-based [FunctionNameExtractor], this parses player.js with meriyah/astring
 * and extracts the transform functions structurally, so it survives YouTube's frequent
 * player obfuscation changes without hardcoded function names or per-hash configs.
 *
 * Protocol (validated against player ae0b654c):
 *   main({type:'player', player:<js>, output_preprocessed:true, requests:[...]})
 *     -> {responses:[{type:'result', data:{<challenge>:<solved>}}], preprocessed_player:<string>}
 * We preprocess once per player hash and cache `preprocessed_player` in the JS global,
 * then reuse it for each sig/n solve to avoid re-parsing the ~2.7 MB player.js.
 */
object EjsCipherSolver {
    private val engine: ScriptEngine by lazy {
        ScriptEngineManager().getEngineByName("graal.js")
            ?: throw CipherException("GraalJS engine not available")
    }

    private var loaded = false
    private var preparedHash: String? = null

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        Napier.i("[EJS] Loading solver (meriyah + astring + yt.solver.core)...")
        // The UMD bundles fall back to `globalThis.<name>` when module/define are absent.
        engine.eval("var globalThis = this; var window = this; var self = this;")
        engine.eval(readAsset("solver/meriyah.js"))
        engine.eval(readAsset("solver/astring.js"))
        engine.eval(readAsset("solver/yt.solver.core.js")) // defines `var jsc = main`
        engine.eval("this.__jsc = jsc;")
        loaded = true
        Napier.i("[EJS] Solver loaded")
    }

    /** Parse player.js and cache its preprocessed form (no-op if the same hash is already prepared). */
    @Synchronized
    fun prepare(playerJs: String, hash: String) {
        ensureLoaded()
        if (preparedHash == hash) return
        engine.put("__playerJs", playerJs)
        val status = engine.eval(
            """
            (function() {
                try {
                    var out = this.__jsc({ type: 'player', player: __playerJs, output_preprocessed: true, requests: [] });
                    this.__pp = out.preprocessed_player;
                    return (typeof this.__pp === 'string') ? 'OK' : 'ERR:no preprocessed_player';
                } catch (e) { return 'ERR:' + ((e && e.message) || e); }
            })();
            """.trimIndent()
        ).toString()
        if (status != "OK") throw CipherException("EJS prepare failed: $status")
        preparedHash = hash
        Napier.i("[EJS] Prepared player hash=$hash")
    }

    /** @param type "sig" or "n". Returns the solved value, or null on failure. */
    @Synchronized
    fun solve(type: String, challenge: String): String? {
        if (preparedHash == null) {
            Napier.e("[EJS] solve called before prepare()")
            return null
        }
        engine.put("__type", type)
        engine.put("__challenge", challenge)
        val raw = engine.eval(
            """
            (function() {
                try {
                    var out = this.__jsc({ preprocessed_player: this.__pp, requests: [ { type: __type, challenges: [ __challenge ] } ] });
                    var r = out.responses[0];
                    if (!r || r.type !== 'result') return 'ERR:' + (r ? r.error : 'no response');
                    return 'OK:' + r.data[__challenge];
                } catch (e) { return 'ERR:' + ((e && e.message) || e); }
            })();
            """.trimIndent()
        ).toString()
        return if (raw.startsWith("OK:")) {
            raw.substring(3)
        } else {
            Napier.e("[EJS] solve $type failed: $raw")
            null
        }
    }

    private fun readAsset(path: String): String {
        val fullPath = "com/example/melodist/utils/cipher/assets/$path"
        val resource = javaClass.classLoader?.getResource(fullPath)
            ?: throw CipherException("Asset not found: $fullPath")
        return resource.readText()
    }
}
