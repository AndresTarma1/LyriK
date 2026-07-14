package com.metrolist.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Runs(
    val runs: List<Run>?,
)

@Serializable
data class Run(
    val text: String,
    val navigationEndpoint: NavigationEndpoint?,
)

fun List<Run>.splitBySeparator(): List<List<Run>> {
    val res = mutableListOf<List<Run>>()
    var tmp = mutableListOf<Run>()
    forEach { run ->
        if (run.text.trim() == "•") {
            res.add(tmp)
            tmp = mutableListOf()
        } else {
            tmp.add(run)
        }
    }
    res.add(tmp)
    return res
}

fun List<Run>.splitArtistsByConjunction(): List<Run> {
    val result = mutableListOf<Run>()
    val words = ArtistConjunctions.conjunctions
    val conjunctionPattern = Regex(
        if (words.isNotEmpty()) " (${words.joinToString("|") { Regex.escape(it) }}) | & "
        else " & ",
        RegexOption.IGNORE_CASE
    )
    forEach { run ->
        val text = run.text
        if (text.contains(conjunctionPattern)) {
            val parts = text.split(conjunctionPattern)
            parts.forEachIndexed { index, part ->
                if (part.isNotBlank()) {
                    result.add(Run(part.trim(), if (index == 0) run.navigationEndpoint else null))
                }
            }
        } else if (text.trim().equals("&", ignoreCase = true) ||
                text.trim().equals("•") ||
                words.any { text.trim().equals(it, ignoreCase = true) }
        ) {
        } else {
            result.add(run)
        }
    }
    return result
}

object ArtistConjunctions {
    var conjunctions: List<String> = listOf("and")
}

fun List<List<Run>>.clean(): List<List<Run>> {
    val firstGroup = getOrNull(0) ?: return this
    val hasArtistSignals = firstGroup.any { it.navigationEndpoint != null } ||
        firstGroup.any { it.text.contains(" & ") } ||
        ArtistConjunctions.conjunctions.any { conj ->
            firstGroup.any { it.text.trim().equals(conj, ignoreCase = true) }
        }
    return if (hasArtistSignals) this else drop(1)
}

/**
 * Extracts the "entity" runs (artist/album names) from a mixed run list that also contains
 * separators, conjunctions ("y", "&", ...), and trailing metadata (view counts). Used across most
 * innertube artist/album parsing (18+ call sites).
 *
 * Prefers filtering by having a real browseId link rather than by index parity — a prior
 * `index % 2 == 0` implementation assumed artists and separators strictly alternate one-for-one,
 * which breaks as soon as a byline has an irregular separator count (e.g. more than 2 artists, or
 * mixed ", "/" y " separators): the wrong run lands on an "even" index, producing blank/duplicated
 * artist names and even sneaking the trailing view-count run into the result.
 *
 * Falls back to the old positional heuristic only when *no* run in the list has a link at all —
 * label-uploaded albums/tracks (non-YTM-channel uploaders) name the artist as plain, unlinked text,
 * so a strict link filter would wrongly return nothing for those instead of a best-effort guess.
 */
fun List<Run>.oddElements(): List<Run> {
    val linked = filter { run -> run.navigationEndpoint?.browseEndpoint?.browseId != null }
    if (linked.isNotEmpty()) return linked
    return filterIndexed { index, _ -> index % 2 == 0 }
}
