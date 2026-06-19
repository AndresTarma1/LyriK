package com.example.melodist.di

import app.cash.sqldelight.db.SqlDriver
import com.example.melodist.data.account.AccountManager
import com.example.melodist.data.local.DatabaseDriverFactory
import com.example.melodist.data.remote.ApiService
import com.example.melodist.data.repository.AlbumRepository
import com.example.melodist.data.repository.ArtistRepository
import com.example.melodist.data.repository.PlaylistRepository
import com.example.melodist.data.repository.SearchRepository
import com.example.melodist.data.repository.SongRepository
import com.example.melodist.bootstrap.JvmConfigLauncher
import com.example.melodist.lifecycle.AppLifecycleManager
import com.example.melodist.viewmodels.AccountViewModel
import com.example.melodist.viewmodels.AlbumViewModel
import com.example.melodist.viewmodels.ArtistViewModel
import com.example.melodist.viewmodels.AppViewModel
import com.example.melodist.viewmodels.DownloadViewModel
import com.example.melodist.viewmodels.HomeViewModel
import com.example.melodist.viewmodels.LibraryAlbumsViewModel
import com.example.melodist.viewmodels.LibraryArtistsViewModel
import com.example.melodist.viewmodels.LibraryMixedViewModel
import com.example.melodist.viewmodels.LibraryPlaylistsViewModel
import com.example.melodist.viewmodels.LibrarySongsViewModel
import com.example.melodist.viewmodels.LibraryViewModel
import com.example.melodist.viewmodels.PlayerViewModel
import com.example.melodist.viewmodels.PlaylistViewModel
import com.example.melodist.viewmodels.SearchViewModel
import com.example.melodist.viewmodels.SettingsViewModel
import com.example.melodist.viewmodels.JvmSettingsViewModel
import com.example.melodist.viewmodels.PlayerCoordinator
import com.example.melodist.viewmodels.PlayerCoordinatorImpl
import com.example.melodist.viewmodels.YouTubeBrowseViewModel
import com.example.melodist.db.DatabaseDao
import com.example.melodist.db.MelodistDatabase
import com.example.melodist.db.MusicDatabase
import com.example.melodist.player.AudioStreamResolver
import com.example.melodist.download.DownloadService
import com.example.melodist.player.PlayerService
import com.example.melodist.player.QueueManager
import com.example.melodist.player.WindowsMediaSession
import com.example.melodist.listentogether.ListenTogetherClient
import com.example.melodist.listentogether.ListenTogetherManager
import com.example.melodist.utils.SyncUtils
import org.koin.dsl.module

val appModule = module {

    // Database
    single<SqlDriver> { DatabaseDriverFactory.createDriver() }
    single<MelodistDatabase> { MelodistDatabase(get<SqlDriver>()) }

    single<MusicDatabase> { MusicDatabase(get<MelodistDatabase>()) }
    single<DatabaseDao> { get<MusicDatabase>().dao }

    // Data layer
    single<ApiService> { ApiService() }
    single<AlbumRepository> { AlbumRepository(get()) }
    single<ArtistRepository> { ArtistRepository(get()) }
    single<SongRepository> { SongRepository(get()) }
    single<PlaylistRepository> { PlaylistRepository(get()) }
    single<SearchRepository> { SearchRepository(get()) }
    single<SyncUtils> { SyncUtils(get(), get(), get(), get(), get()) }

    // Player (singletons — shared across entire app)
    // ✅ PlayerService se inicializa perezosamente — solo al primero play()
    single<PlayerService> { PlayerService() }
    single<AudioStreamResolver> { AudioStreamResolver(get()) }
    single<WindowsMediaSession> { WindowsMediaSession() }
    single<DownloadService> { DownloadService(get(), get()) }
    single<QueueManager> { QueueManager() }
    single<AppViewModel> { AppViewModel() }
    // ✅ DownloadViewModel singleton — mantiene estado de descargas compartido
    single<DownloadViewModel> { DownloadViewModel(get(), get(), get()) }
    // ✅ PlayerViewModel singleton, pero inicialización pesada diferida al init{} interno
    single<PlayerViewModel> { PlayerViewModel(get(), get(), get(), get(), get(), get(), get()) }
    single<PlayerCoordinator> { PlayerCoordinatorImpl(get<PlayerViewModel>(), get<DownloadViewModel>()) }

    // Listen Together (WebSocket sync with the meowery relay server)
    single<ListenTogetherClient> { ListenTogetherClient() }
    single<ListenTogetherManager> { ListenTogetherManager(get()) }
    single<AppLifecycleManager> { AppLifecycleManager(get(), get(), get(), get()) }
    single<JvmConfigLauncher> { JvmConfigLauncher(get()) }

    // ViewModels — loginState de AccountManager para reaccionar a cambios de sesión
    factory { AccountViewModel() }
    factory { YouTubeBrowseViewModel() }
    single { HomeViewModel(databaseDao = get(), loginState = AccountManager.loginState) }
    single { SearchViewModel(get()) }
    single { LibraryViewModel(get(), get(), get(), get(), get(), loginState = AccountManager.loginState) }
    single { LibrarySongsViewModel(get(), get(), get(), get()) }
    single { LibraryAlbumsViewModel(get()) }
    single { LibraryArtistsViewModel(get()) }
    single { LibraryPlaylistsViewModel(get()) }
    single { LibraryMixedViewModel(get()) }
    factory { AlbumViewModel(get(), get(), get()) }
    factory { PlaylistViewModel(get(), get(), get(), get()) }
    factory { ArtistViewModel(get(), get()) }
    single { SettingsViewModel(get()) }
    single { JvmSettingsViewModel(get()) }
}
