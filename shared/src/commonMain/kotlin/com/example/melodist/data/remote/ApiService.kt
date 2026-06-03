package com.example.melodist.data.remote

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.pages.AlbumPage
import com.metrolist.innertube.pages.ArtistPage
import com.metrolist.innertube.pages.HomePage
import com.metrolist.innertube.pages.LibraryPage
import com.metrolist.innertube.pages.PlaylistPage
import com.metrolist.innertube.pages.SearchResult
import com.metrolist.innertube.pages.SearchSummaryPage

/**
 * Wrapper around the YouTube (innertube) API.
 * Acts as the single remote data source for the app.
 */
class ApiService {

    suspend fun getHome(params: String? = null, continuation: String? = null): Result<HomePage> {
        return if (continuation != null) {
            YouTube.home(continuation = continuation)
        } else {
            YouTube.home(params = params)
        }
    }

    suspend fun getAlbum(browseId: String): Result<AlbumPage> {
        return YouTube.album(browseId)
    }

    suspend fun getArtist(browseId: String): Result<ArtistPage> {
        return YouTube.artist(browseId)
    }

    suspend fun getPlaylist(playlistId: String): Result<PlaylistPage> {
        return YouTube.playlist(playlistId)
    }

    suspend fun search(query: String, filter: YouTube.SearchFilter): Result<SearchResult> {
        return YouTube.search(query, filter)
    }

    suspend fun searchSummary(query: String): Result<SearchSummaryPage> {
        return YouTube.searchSummary(query)
    }

    suspend fun searchSuggestions(query: String): Result<com.metrolist.innertube.models.SearchSuggestions> {
        return YouTube.searchSuggestions(query)
    }

    suspend fun searchContinuation(token: String): Result<SearchResult> {
        return YouTube.searchContinuation(token)
    }

    suspend fun getLibraryWithFilter(filter: YouTube.LibraryFilter): Result<LibraryPage> {
        return YouTube.libraryRecentActivity()
    }

    suspend fun getLibraryByBrowseId(browseId: String, tabIndex: Int = 0): Result<LibraryPage> {
        return YouTube.library(browseId, tabIndex = tabIndex)
    }
}


