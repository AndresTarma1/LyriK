package com.example.melodist.utils

data class CsvSongRow(
    val title: String,
    val artist: String,
    val album: String?,
    val durationSeconds: Int,
    val explicit: Boolean,
    val genres: String?,
)

object CsvPlaylistParser {

    fun parse(csvContent: String): List<CsvSongRow> {
        val lines = csvContent.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val headerIndex = findHeaderLine(lines)
        val dataLines = if (headerIndex >= 0) lines.drop(headerIndex + 1) else lines

        return dataLines.mapNotNull { line ->
            val fields = parseCsvLine(line)
            if (fields.size < 2) return@mapNotNull null
            val title = fields.getOrNull(1)?.trim()?.removeSurrounding("\"") ?: return@mapNotNull null
            val artist = fields.getOrNull(2)?.trim()?.removeSurrounding("\"") ?: return@mapNotNull null
            val album = fields.getOrNull(10)?.trim()?.removeSurrounding("\"")?.ifBlank { null }
            val durationRaw = fields.getOrNull(7)?.trim()?.removeSurrounding("\"") ?: ""
            val explicitRaw = fields.getOrNull(22)?.trim()?.removeSurrounding("\"") ?: ""
            val genres = fields.getOrNull(9)?.trim()?.removeSurrounding("\"")?.ifBlank { null }

            CsvSongRow(
                title = title,
                artist = artist,
                album = album,
                durationSeconds = parseDuration(durationRaw),
                explicit = explicitRaw.equals("yes", ignoreCase = true) || explicitRaw.equals("true", ignoreCase = true),
                genres = genres,
            )
        }
    }

    private fun findHeaderLine(lines: List<String>): Int {
        for ((i, line) in lines.withIndex()) {
            val fields = parseCsvLine(line)
            if (fields.any { it.trim().equals("Song", ignoreCase = true) } &&
                fields.any { it.trim().equals("Artist", ignoreCase = true) }
            ) return i
        }
        return -1
    }

    internal fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when (c) {
                '"' if !inQuotes -> inQuotes = true
                '"' if true -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                }
                ',' if !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    private fun parseDuration(duration: String): Int {
        if (duration.isBlank()) return -1
        val parts = duration.split(":").map { it.toIntOrNull() ?: 0 }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> -1
        }
    }
}
