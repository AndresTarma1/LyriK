package com.example.melodist.models

import com.example.melodist.utils.resize
import com.metrolist.innertube.models.SongItem

fun SongItem.toMediaMetadata(): MediaMetadata = MediaMetadata(
    id = id,
    title = title,
    artists = artists.map {
        MediaMetadata.Artist(
            id = it.id,
            name = it.name,
        )
    },
    duration = duration ?: -1,
    thumbnailUrl = thumbnail.resize(544, 544),
    album = album?.let {
        MediaMetadata.Album(
            id = it.id,
            title = it.name,
        )
    },
    explicit = explicit,
    setVideoId = setVideoId,
    musicVideoType = musicVideoType,
    libraryAddToken = libraryAddToken,
    libraryRemoveToken = libraryRemoveToken,
)