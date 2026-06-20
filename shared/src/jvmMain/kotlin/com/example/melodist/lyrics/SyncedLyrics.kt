package com.example.melodist.lyrics

/** A single word with its sung time window (karaoke fill). */
data class LyricWord(val text: String, val startMs: Long, val endMs: Long)

/** A timestamped lyric line, optionally with per-word timings for karaoke highlighting. */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
)

/**
 * Parser for (enriched) LRC produced by [TTMLParser.toLRC] or plain `[mm:ss.xx]` LRC.
 * Word timings appear on a `<word:startSec:endSec|…>` line right after their timed line.
 */
object SyncedLyrics {
    private val timeRegex = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val tagRegex = Regex("""\{[^}]*}""")

    fun isSynced(lrc: String): Boolean = timeRegex.containsMatchIn(lrc)

    fun parse(lrc: String): List<LyricLine> {
        val out = mutableListOf<LyricLine>()
        val rawLines = lrc.split('\n')
        var i = 0
        while (i < rawLines.size) {
            val raw = rawLines[i].trimEnd()
            i++
            if (raw.isBlank() || raw.trimStart().startsWith("<")) continue

            val matches = timeRegex.findAll(raw).toList()
            if (matches.isEmpty()) continue

            var text = raw.substring(matches.last().range.last + 1)
            text = tagRegex.replace(text, "").trim()

            // An optional word-timing line may follow this timed line.
            var words = emptyList<LyricWord>()
            if (i < rawLines.size && rawLines[i].trimStart().startsWith("<")) {
                words = parseWordLine(rawLines[i].trim())
                i++
            }

            if (text.isEmpty() && words.isEmpty()) continue
            for (m in matches) {
                out.add(LyricLine(timeMsOf(m), text, words))
            }
        }
        return out.sortedBy { it.timeMs }
    }

    private fun timeMsOf(m: MatchResult): Long {
        val min = m.groupValues[1].toLongOrNull() ?: 0L
        val sec = m.groupValues[2].toLongOrNull() ?: 0L
        val frac = m.groupValues[3]
        val fracMs = when (frac.length) {
            0 -> 0L
            1 -> frac.toLong() * 100
            2 -> frac.toLong() * 10
            else -> frac.take(3).toLong()
        }
        return (min * 60 + sec) * 1000 + fracMs
    }

    private fun parseWordLine(line: String): List<LyricWord> {
        val inner = line.removePrefix("<").removeSuffix(">")
        if (inner.isBlank()) return emptyList()
        return inner.split('|').mapNotNull { tok ->
            // text may itself contain ':', so split on the LAST two colons (start:end).
            val endSep = tok.lastIndexOf(':')
            if (endSep <= 0) return@mapNotNull null
            val startSep = tok.lastIndexOf(':', endSep - 1)
            if (startSep <= 0) return@mapNotNull null
            val text = tok.substring(0, startSep)
            val start = tok.substring(startSep + 1, endSep).toDoubleOrNull() ?: return@mapNotNull null
            val end = tok.substring(endSep + 1).toDoubleOrNull() ?: return@mapNotNull null
            LyricWord(text, (start * 1000).toLong(), (end * 1000).toLong())
        }
    }
}
