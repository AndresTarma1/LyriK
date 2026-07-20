package com.example.musicApp.viewmodels

import com.example.musicApp.viewmodels.queues.YouTubePlaylistQueue
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint

interface PlayerCoordinator {
    fun playFromEndpoint(endpoint: WatchEndpoint, shuffle: Boolean, fallback: () -> Unit)
    fun playAlbum(songs: List<SongItem>, startIndex: Int, browseId: String, title: String)
    fun playPlaylist(songs: List<SongItem>, startIndex: Int, playlistId: String, title: String)
    fun playPlaylistWithQueue(queue: YouTubePlaylistQueue, shuffle: Boolean)
    fun downloadSongs(songs: List<SongItem>)
}
