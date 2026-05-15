package com.example.melodist.utils

import com.example.melodist.player.YTPlayerutils
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun SongItem.withMissingMetadataResolved(): SongItem {
    val hasDuration = duration != null
    if (hasDuration) return this

    val playbackData = withContext(Dispatchers.IO) {
        YTPlayerutils.playerResponseForMetadata(id).getOrNull()
    }
    val resolvedDuration = playbackData?.videoDetails?.lengthSeconds?.toIntOrNull()

    return if (resolvedDuration != null && resolvedDuration > 0) {
        copy(duration = resolvedDuration)
    } else {
        this
    }
}