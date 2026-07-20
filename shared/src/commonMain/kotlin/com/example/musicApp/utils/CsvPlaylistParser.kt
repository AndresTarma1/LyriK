package com.example.musicApp.utils

data class CsvSongRow(
    val title: String,
    val artist: String,
    val album: String?,
    val isrc: String?,
)

object CsvPlaylistParser {

    fun parse(csvContent: String): List<CsvSongRow> {
        val cleaned = csvContent.trim().removePrefix("\uFEFF").removePrefix("\uFFFE")
        val lines = cleaned.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val headerIndex = findHeaderLine(lines)
        if (headerIndex < 0) return emptyList()

        val headers = parseCsvLine(lines[headerIndex])
        val colIndex = mapHeaders(headers)

        val dataLines = lines.drop(headerIndex + 1)
        return dataLines.mapNotNull { line ->
            val fields = parseCsvLine(line)
            val title = fields.getOrNull(colIndex.title)?.trim().orEmpty()
            val artist = fields.getOrNull(colIndex.artist)?.trim().orEmpty()
            if (title.isBlank() || artist.isBlank()) null
            else CsvSongRow(
                title = title,
                artist = artist,
                album = fields.getOrNull(colIndex.album)?.trim()?.ifBlank { null },
                isrc = fields.getOrNull(colIndex.isrc)?.trim()?.ifBlank { null },
            )
        }
    }

    private data class ColumnIndex(
        val title: Int,
        val artist: Int,
        val album: Int,
        val isrc: Int,
    )

    private fun mapHeaders(headers: List<String>): ColumnIndex {
        val normalized = headers.map { it.lowercase().trim().removeSurrounding("\"") }
        val title = normalized.indexOfFirst { it in listOf("track name", "song", "title", "name") }
        val artist = normalized.indexOfFirst { it in listOf("artist name", "artist", "artists") }
        val album = normalized.indexOfFirst { it in listOf("album", "album name") }
        val isrc = normalized.indexOfFirst { it == "isrc" }
        return ColumnIndex(
            title = if (title >= 0) title else 0,
            artist = if (artist >= 0) artist else 1,
            album = album,
            isrc = isrc,
        )
    }

    private fun findHeaderLine(lines: List<String>): Int {
        for ((i, line) in lines.withIndex()) {
            val fields = parseCsvLine(line)
            val lower = fields.map { it.lowercase().trim().removeSurrounding("\"") }
            val hasTrackHeader = lower.any { it in listOf("track name", "song", "title", "name") }
            val hasArtistHeader = lower.any { it in listOf("artist name", "artist", "artists") }
            if (hasTrackHeader && hasArtistHeader) return i
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
            when {
                c == '"' && !inQuotes -> inQuotes = true
                c == '"' && inQuotes -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                }
                c == ',' && !inQuotes -> {
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
}
