package com.example.melodist.viewmodels

import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint

interface PlayerCoordinator {
    fun playFromEndpoint(endpoint: WatchEndpoint, shuffle: Boolean, fallback: () -> Unit)
    fun playAlbum(songs: List<SongItem>, startIndex: Int, browseId: String, title: String)
    fun playPlaylist(songs: List<SongItem>, startIndex: Int, playlistId: String, title: String)
    fun downloadSongs(songs: List<SongItem>)
}
