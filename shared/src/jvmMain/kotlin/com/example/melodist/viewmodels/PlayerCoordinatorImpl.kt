package com.example.melodist.viewmodels

import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint

class PlayerCoordinatorImpl(
    private val playerViewModel: PlayerViewModel,
    private val downloadViewModel: DownloadViewModel,
) : PlayerCoordinator {

    override fun playFromEndpoint(endpoint: WatchEndpoint, shuffle: Boolean, fallback: () -> Unit) {
        playerViewModel.playEndpoint(endpoint, shuffle = shuffle)
    }

    override fun playAlbum(songs: List<SongItem>, startIndex: Int, browseId: String, title: String) {
        playerViewModel.playAlbum(songs, startIndex, browseId, title)
    }

    override fun playPlaylist(songs: List<SongItem>, startIndex: Int, playlistId: String, title: String) {
        playerViewModel.playPlaylist(songs, startIndex, playlistId, title)
    }

    override fun downloadSongs(songs: List<SongItem>) {
        downloadViewModel.downloadAll(songs)
    }
}
