package com.example.melodist.ui.screens.shared

import com.metrolist.innertube.models.SongItem

fun calculateTotalDuration(songs: List<SongItem>): String {
    val totalSeconds = songs.sumOf { it.duration ?: 0 }
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "${hours} h ${minutes} min"
        minutes > 0 -> "${minutes} min"
        else -> "< 1 min"
    }
}

fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "$minutes:${remainingSeconds.toString().padStart(2, '0')}"
}

