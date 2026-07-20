package com.example.musicApp.di

import app.cash.sqldelight.db.SqlDriver
import com.example.musicApp.data.account.AccountManager
import com.example.musicApp.data.local.DatabaseDriverFactory
import com.example.musicApp.data.remote.ApiService
import com.example.musicApp.data.repository.AlbumRepository
import com.example.musicApp.data.repository.ArtistRepository
import com.example.musicApp.data.repository.PlaylistRepository
import com.example.musicApp.data.repository.SearchRepository
import com.example.musicApp.data.repository.SongRepository
import com.example.musicApp.bootstrap.JvmConfigLauncher
import com.example.musicApp.lifecycle.AppLifecycleManager
import com.example.musicApp.viewmodels.AccountViewModel
import com.example.musicApp.viewmodels.AlbumViewModel
import com.example.musicApp.viewmodels.ArtistViewModel
import com.example.musicApp.viewmodels.AppViewModel
import com.example.musicApp.viewmodels.DownloadViewModel
import com.example.musicApp.viewmodels.HomeViewModel
import com.example.musicApp.viewmodels.LibraryAlbumsViewModel
import com.example.musicApp.viewmodels.LibraryArtistsViewModel
import com.example.musicApp.viewmodels.LibraryMixedViewModel
import com.example.musicApp.viewmodels.LibraryPlaylistsViewModel
import com.example.musicApp.viewmodels.LibrarySongsViewModel
import com.example.musicApp.viewmodels.LibraryViewModel
import com.example.musicApp.viewmodels.PlayerViewModel
import com.example.musicApp.viewmodels.PlaylistViewModel
import com.example.musicApp.viewmodels.SearchViewModel
import com.example.musicApp.viewmodels.SettingsViewModel
import com.example.musicApp.viewmodels.JvmSettingsViewModel
import com.example.musicApp.viewmodels.PlayerCoordinator
import com.example.musicApp.viewmodels.PlayerCoordinatorImpl
import com.example.musicApp.viewmodels.YouTubeBrowseViewModel
import com.example.musicApp.db.DatabaseDao
import com.example.musicApp.db.MelodistDatabase
import com.example.musicApp.db.MusicDatabase
import com.example.musicApp.player.AudioStreamResolver
import com.example.musicApp.download.DownloadService
import com.example.musicApp.player.PlayerService
import com.example.musicApp.player.QueueManager
import com.example.musicApp.player.WindowsMediaSession
import com.example.musicApp.listentogether.ListenTogetherClient
import com.example.musicApp.listentogether.ListenTogetherManager
import com.example.musicApp.utils.OfflineModeController
import com.example.musicApp.utils.PendingSyncQueue
import com.example.musicApp.utils.SyncUtils
import com.example.musicApp.overlay.GlobalHotkeyManager
import com.example.musicApp.overlay.OverlayController
import org.koin.dsl.module

val appModule = module {

    // Base de datos
    single<SqlDriver> { DatabaseDriverFactory.createDriver() }
    single<MelodistDatabase> { MelodistDatabase(get<SqlDriver>()) }

    single<MusicDatabase> { MusicDatabase(get<MelodistDatabase>()) }
    single<DatabaseDao> { get<MusicDatabase>().dao }

    // Capa de datos
    single<ApiService> { ApiService() }
    single<AlbumRepository> { AlbumRepository(get()) }
    single<ArtistRepository> { ArtistRepository(get()) }
    single<SongRepository> { SongRepository(get()) }
    single<PlaylistRepository> { PlaylistRepository(get(), get()) }
    single<SearchRepository> { SearchRepository(get()) }
    single<SyncUtils> { SyncUtils(get(), get(), get(), get(), get()) }

    // Reproductor (singletons — compartidos en toda la app)
    // ✅ PlayerService se inicializa perezosamente — solo al primero play()
    single<PlayerService> { PlayerService(get()) }
    single<AudioStreamResolver> { AudioStreamResolver(get()) }
    single<WindowsMediaSession> { WindowsMediaSession() }
    single<DownloadService> { DownloadService(get(), get()) }
    single<QueueManager> { QueueManager() }
    single<AppViewModel> { AppViewModel() }
    // Cola de sincronización remota sin conexión (likes/suscripciones que fallaron al enviar sin conexión).
    single<PendingSyncQueue> { PendingSyncQueue(get()) }
    // Interruptor de red global — debe resolverse eager al inicio (ver main()).
    single<OfflineModeController> { OfflineModeController(get()) }
    // ✅ DownloadViewModel singleton — mantiene estado de descargas compartido
    single<DownloadViewModel> { DownloadViewModel(get(), get(), get()) }
    // ✅ PlayerViewModel singleton, pero inicialización pesada diferida al init{} interno
    single<PlayerViewModel> { PlayerViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    single<PlayerCoordinator> { PlayerCoordinatorImpl(get<PlayerViewModel>(), get<DownloadViewModel>()) }

    // Escuchar Juntos (sincronización WebSocket con el servidor relay meowery)
    single<ListenTogetherClient> { ListenTogetherClient() }
    single<ListenTogetherManager> { ListenTogetherManager(get()) }
    single<AppLifecycleManager> { AppLifecycleManager(get(), get(), get(), get(), get()) }
    single<JvmConfigLauncher> { JvmConfigLauncher(get()) }

    // Overlay de juego — el atajo global de teclado activa/desactiva una ventana de música siempre visible.
    single { GlobalHotkeyManager(onTrigger = { OverlayController.toggle() }) }

    // ViewModels — loginState de AccountManager para reaccionar a cambios de sesión
    factory { AccountViewModel(get(), get(), get()) }
    factory { YouTubeBrowseViewModel() }
    single { HomeViewModel(databaseDao = get(), loginState = AccountManager.loginState, preferencesRepository = get()) }
    single { SearchViewModel(get()) }
    single { LibraryViewModel(get(), get(), get(), get(), get(), loginState = AccountManager.loginState) }
    single { LibrarySongsViewModel(get(), get(), get(), get()) }
    single { LibraryAlbumsViewModel(get()) }
    single { LibraryArtistsViewModel(get()) }
    single { LibraryPlaylistsViewModel(get(), get()) }
    single { LibraryMixedViewModel(get()) }
    factory { AlbumViewModel(get(), get(), get()) }
    factory { PlaylistViewModel(get(), get(), get(), get(), get()) }
    factory { ArtistViewModel(get(), get(), get(), get()) }
    single { SettingsViewModel(get(), get()) }
    single { JvmSettingsViewModel(get()) }
}
