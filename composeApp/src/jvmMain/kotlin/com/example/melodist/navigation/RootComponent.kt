package com.example.melodist.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.*
import com.arkivanov.decompose.value.Value
import com.example.melodist.viewmodels.*
import kotlinx.serialization.serializer
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class RootComponent(
    componentContext: ComponentContext,
) : ComponentContext by componentContext, KoinComponent {

    private val navigation = StackNavigation<ScreenConfig>()

    val childStack: Value<ChildStack<ScreenConfig, Child>> =
        childStack(
            source = navigation,
            serializer = serializer<ScreenConfig>(),
            initialConfiguration = ScreenConfig.Home,
            handleBackButton = true,
            childFactory = ::createChild
        )

    private fun createChild(config: ScreenConfig, componentContext: ComponentContext): Child {
        return when (config) {
            is ScreenConfig.Home -> Child.Home(HomeComponent(componentContext, get()))
            is ScreenConfig.Search -> Child.Search(SearchComponent(componentContext, get()))
            is ScreenConfig.Library -> Child.Library(LibraryComponent(componentContext, get()))
            is ScreenConfig.Account -> Child.Account(AccountComponent(componentContext, get()))
            is ScreenConfig.Settings -> Child.Settings(SettingsComponent(componentContext, get()))
            is ScreenConfig.Album -> Child.Album(AlbumComponent(componentContext, config.browseId, get()))
            is ScreenConfig.Playlist -> Child.Playlist(PlaylistComponent(componentContext, config.playlistId, get()))
            is ScreenConfig.Artist -> Child.Artist(ArtistComponent(componentContext, config.artistId, get()))
            is ScreenConfig.YouTubeBrowse -> Child.YouTubeBrowse(YouTubeBrowseComponent(componentContext, config.browseId, config.params, get()))
        }
    }

    fun navigateTo(config: ScreenConfig) {
        navigation.navigate { stack ->
            // Remove any existing instance of this config, then push it on top
            stack.filterNot { it == config } + config
        }
    }

    fun onBack() {
        navigation.pop()
    }

    fun switchTab(config: ScreenConfig) {
        navigation.replaceAll(config)
    }


    sealed class Child {
        data class Home(val component: HomeComponent) : Child()
        data class Search(val component: SearchComponent) : Child()
        data class Library(val component: LibraryComponent) : Child()
        data class Account(val component: AccountComponent) : Child()
        data class Settings(val component: SettingsComponent) : Child()
        data class Album(val component: AlbumComponent) : Child()
        data class Playlist(val component: PlaylistComponent) : Child()
        data class Artist(val component: ArtistComponent) : Child()
        data class YouTubeBrowse(val component: YouTubeBrowseComponent) : Child()
    }
}

// Each component owns its ViewModel so it survives back-stack

class HomeComponent(componentContext: ComponentContext, val viewModel: HomeViewModel) :
    ComponentContext by componentContext

class SearchComponent(
    componentContext: ComponentContext,
    val viewModel: SearchViewModel
) : ComponentContext by componentContext

class SettingsComponent(
    componentContext: ComponentContext,
    val viewModel: SettingsViewModel
) : ComponentContext by componentContext

class LibraryComponent(
    componentContext: ComponentContext,
    val viewModel: LibraryViewModel
) : ComponentContext by componentContext

class AlbumComponent(
    componentContext: ComponentContext,
    browseId: String,
    val viewModel: AlbumViewModel
) : ComponentContext by componentContext {
    init {
        viewModel.loadAlbum(browseId)
    }
}

class PlaylistComponent(
    componentContext: ComponentContext,
    playlistId: String,
    val viewModel: PlaylistViewModel
) : ComponentContext by componentContext {
    init {
        viewModel.loadPlaylist(playlistId)
    }
}

class ArtistComponent(
    componentContext: ComponentContext,
    artistId: String,
    val viewModel: ArtistViewModel
) : ComponentContext by componentContext {
    init {
        viewModel.loadArtist(artistId)
    }
}

class AccountComponent(componentContext: ComponentContext, val viewModel: AccountViewModel) :
    ComponentContext by componentContext

class YouTubeBrowseComponent(
    componentContext: ComponentContext,
    browseId: String,
    params: String?,
    val viewModel: YouTubeBrowseViewModel
) : ComponentContext by componentContext {
    init {
        viewModel.load(browseId, params)
    }
}
