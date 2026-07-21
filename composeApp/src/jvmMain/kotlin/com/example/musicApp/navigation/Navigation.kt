package com.example.musicApp.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.example.musicApp.FpsCounter
import com.example.musicApp.data.repository.LayoutMode
import com.example.musicApp.data.repository.NavigationRailStyle
import com.example.musicApp.data.repository.UserPreferencesRepository
import com.example.musicApp.ui.components.MiniPlayer
import com.example.musicApp.ui.components.dialogs.SnackBar
import com.example.musicApp.ui.components.player.NowPlayingLayout
import com.example.musicApp.ui.components.player.NowPlayingTab
import com.example.musicApp.ui.components.player.PlaybackQueuePanel
import com.example.musicApp.ui.screens.library.CsvImportProgressOverlay
import com.example.musicApp.viewmodels.LibraryPlaylistsViewModel
import com.example.musicApp.viewmodels.PlayerViewModel
import org.koin.compose.koinInject
import com.example.musicApp.ui.screens.YouTubeBrowseScreenRoute
import com.example.musicApp.ui.screens.*
import com.example.musicApp.ui.screens.album.AlbumScreenRoute
import com.example.musicApp.ui.screens.home.HomeScreenRoute
import com.example.musicApp.ui.screens.library.LibraryScreenRoute
import com.example.musicApp.ui.themes.LocalDimens
import com.example.musicApp.ui.themes.LocalLayoutMode
import com.example.musicApp.utils.LocalPlayerViewModel
import com.example.musicApp.utils.LocalSnackbarHostState
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource


data class TabInfo(
    val config: ScreenConfig,
    val icon: ImageVector
)

val mainTabs = listOf(
    TabInfo(ScreenConfig.Home, Icons.Filled.Home),
    TabInfo(ScreenConfig.Search, Icons.Filled.Search),
    TabInfo(ScreenConfig.Library, Icons.Filled.LibraryMusic),
)

val bottomTabs = listOf(
    TabInfo(ScreenConfig.ListenTogether, Icons.Filled.Groups),
    TabInfo(ScreenConfig.Account, Icons.Filled.Person),
    TabInfo(ScreenConfig.Settings, Icons.Filled.Settings),
)


@Composable
fun NavigationDesktop(rootComponent: RootComponent, userPreferences: UserPreferencesRepository) {
    val childStack by rootComponent.childStack.subscribeAsState()
    val activeConfig = childStack.active.configuration

    val playerViewModel: PlayerViewModel = LocalPlayerViewModel.current
    val snackbarHostState = LocalSnackbarHostState.current
    val playlistsViewModel = koinInject<LibraryPlaylistsViewModel>()
    val csvImportState by playlistsViewModel.csvImportState.collectAsState()

    val navigationRailStyle by userPreferences.navigationRailStyle.collectAsState(NavigationRailStyle.DEFAULT)

    val playerState by playerViewModel.uiState.collectAsState()
    val progressState by playerViewModel.progressState.collectAsState()
    val currentLyrics by playerViewModel.currentLyrics.collectAsState()
    val currentSongMediaInfo by playerViewModel.currentMediaInfo.collectAsState()
    var isNowPlayingExpanded by remember { mutableStateOf(false) }
    var isQueueVisible by remember { mutableStateOf(false) }
    var nowPlayingTab by remember { mutableStateOf(NowPlayingTab.QUEUE) }

    val queueWidth = 420.dp

    // Nos permite entender o mostrar los errores de reproducción en un Snackbar, sin bloquear la UI principal.
    LaunchedEffect(playerViewModel) {
        playerViewModel.playbackMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
    val sharedTransitionScope = this
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        // El Box nos permite superponer elementos (como el Snackbar) sin alterar el layout principal
        Box(Modifier.fillMaxSize()) {

            // CONTENIDO PRINCIPAL
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {

                    when(navigationRailStyle){
                        NavigationRailStyle.DEFAULT -> {
                            NavigationRailDefault(
                                activeConfig = activeConfig,
                                changeQueueVisible = { isQueueVisible = it },
                                rootComponent = rootComponent,
                                changeNowPlayingExpanded = { isNowPlayingExpanded = it }
                            )
                        }

                        NavigationRailStyle.WIDE -> {
                            WideNavigationRail(
                                activeConfig = activeConfig,
                                changeQueueVisible = { isQueueVisible = it },
                                rootComponent = rootComponent,
                                changeNowPlayingExpanded = { isNowPlayingExpanded = it }
                            )
                        }
                    }

                    val currentSong = playerState.currentSong
                    val islands = LocalLayoutMode.current == LayoutMode.ISLANDS
                    val dimens = LocalDimens.current
                    val contentShape = RoundedCornerShape(dimens.surfaceCorner)
                    val bottomPadding = if (currentSong != null) 0.dp else dimens.windowPadding

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = dimens.windowPadding, bottom = bottomPadding)
                    ) {

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1F)
                                    .fillMaxHeight()
                                    .then(if (islands) Modifier.shadow(dimens.surfaceElevation, contentShape) else Modifier)
                                    .clip(contentShape)
                                    .then(
                                        if (islands) Modifier.border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, contentShape)
                                        else Modifier
                                    )
                                    .background(MaterialTheme.colorScheme.background)
                            ) {
                                Children(
                                    stack = rootComponent.childStack,
                                    animation = stackAnimation(fade())
                                ) { child ->
                                    ScreenRouter(
                                        instance = child.instance,
                                        rootComponent = rootComponent,
                                    )
                                }

                                androidx.compose.animation.AnimatedVisibility(
                                    visible = isNowPlayingExpanded && currentSong != null,
                                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                                ) {
                                    if (currentSong != null) {
                                        NowPlayingLayout(
                                            state = playerState,
                                            song = currentSong,
                                            onCollapse = { isNowPlayingExpanded = false },
                                            onNavigate = { route ->
                                                isNowPlayingExpanded = false
                                                rootComponent.navigateTo(route.toConfig())
                                            },
                                            selectedTab = nowPlayingTab,
                                            onTabSelected = { nowPlayingTab = it },
                                            lyrics = currentLyrics,
                                            mediaInfo = currentSongMediaInfo,
                                            sharedTransitionScope = sharedTransitionScope,
                                            animatedVisibilityScope = this,
                                        )
                                    }
                                }
                            }

                            // Solo se muestra si el nowPlaying NO está expandido, para evitar que se superponga con el NowPlayingLayout
                            AnimatedVisibility(
                                visible = isQueueVisible && !isNowPlayingExpanded
                            ) {
                                Row(modifier = Modifier.fillMaxHeight()) {
                                    Spacer(Modifier.width(dimens.surfaceGap))

                                    PlaybackQueuePanel(
                                        state = playerState,
                                        onDismiss = { isQueueVisible = false },
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(queueWidth)
                                            .then(if (islands) Modifier.shadow(dimens.surfaceElevation, contentShape) else Modifier)
                                            .clip(contentShape)
                                            .then(
                                                if (islands) Modifier.border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, contentShape)
                                                else Modifier
                                            )
                                    )
                                }
                            }
                        }
                    }
                }

                // El MiniPlayer se queda al final del Column principal ocupando su espacio correspondiente
                val currentSong = playerState.currentSong
                AnimatedVisibility(
                    visible = currentSong != null,
                ) {
                    MiniPlayer(
                        progressState = progressState,
                        onToggleNowPlaying = {
                            isNowPlayingExpanded = !isNowPlayingExpanded
                            if (isNowPlayingExpanded) isQueueVisible = false
                        },
                        isNowPlayingExpanded = isNowPlayingExpanded,
                        onToggleQueue = {
                            if (isNowPlayingExpanded) {
                                nowPlayingTab = NowPlayingTab.QUEUE
                            } else {
                                isQueueVisible = !isQueueVisible
                            }
                        },
                        isQueueVisible = isQueueVisible || (isNowPlayingExpanded && nowPlayingTab == NowPlayingTab.QUEUE),
                        modifier = Modifier.fillMaxWidth(),
                        sharedTransitionScope = sharedTransitionScope,
                    )
                }
            }

            FpsCounter(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            )

            val currentSong = playerState.currentSong
            SnackBar(
                currentSong = currentSong,
                snackbarHostState = snackbarHostState,
            )

            // Que funcione como una importacion en segundo plano, sin bloquear la UI principal.
            // Se muestra un overlay con el progreso de la importación.
            CsvImportProgressOverlay(
                state = csvImportState,
                onCancel = { playlistsViewModel.cancelCsvImport() },
                onDismiss = { playlistsViewModel.dismissCsvImportResult() },
            )
        }
    }
    }
}


fun Route.toConfig(): ScreenConfig = when (this) {
    Route.Home -> ScreenConfig.Home
    Route.Search -> ScreenConfig.Search
    Route.Library -> ScreenConfig.Library
    Route.Account -> ScreenConfig.Account
    Route.Settings -> ScreenConfig.Settings
    Route.ListenTogether -> ScreenConfig.ListenTogether
    is Route.Album -> ScreenConfig.Album(browseId)
    is Route.Playlist -> ScreenConfig.Playlist(playlistId)
    is Route.Artist -> ScreenConfig.Artist(artistId)
    is Route.YouTubeBrowse -> ScreenConfig.YouTubeBrowse(browseId, params)
}

@Composable
fun ScreenRouter(
    instance: RootComponent.Child,
    rootComponent: RootComponent,
) {
    val navigator = createNavigator(rootComponent)
    when (instance) {
        is RootComponent.Child.Home -> {
            HomeScreenRoute(
                viewModel = instance.component.viewModel,
                onNavigate = navigator,
            )
        }

        is RootComponent.Child.Search -> {
            SearchScreenRoute(
                viewModel = instance.component.viewModel,
                onNavigate = navigator,
            )
        }

        is RootComponent.Child.Album -> {
            AlbumScreenRoute(
                viewModel = instance.component.viewModel,
                onNavigate = navigator,
                onBack = { rootComponent.onBack() },
            )
        }

        is RootComponent.Child.Playlist -> {
            PlaylistScreenRoute(
                viewModel = instance.component.viewModel,
                onNavigate = navigator,
                onBack = { rootComponent.onBack() },
            )
        }

        is RootComponent.Child.Artist -> {
            ArtistScreenRoute(
                onNavigate = navigator,
                onBack = { rootComponent.onBack() },
                viewModel = instance.component.viewModel,
            )
        }

        is RootComponent.Child.Library -> {
            LibraryScreenRoute(
                viewModel = instance.component.viewModel,
                onNavigate = navigator,
            )
        }

        is RootComponent.Child.Account -> {
            AccountScreenRoute(
                viewModel = instance.component.viewModel,
                onNavigate = navigator,
            )
        }

        is RootComponent.Child.Settings -> {
            SettingsScreen(viewModel = instance.component.viewModel)
        }

        is RootComponent.Child.ListenTogether -> {
            ListenTogetherScreen()
        }

        is RootComponent.Child.YouTubeBrowse -> {
            YouTubeBrowseScreenRoute(
                viewModel = instance.component.viewModel,
                onNavigate = navigator,
                onBack = { rootComponent.onBack() },
            )
        }
    }
}


// Helper para simplificar las llamadas
@Composable
fun createNavigator(rootComponent: RootComponent): (Route) -> Unit = { route ->
    rootComponent.navigateTo(route.toConfig())
}

