package com.example.melodist.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.example.melodist.ui.components.MiniPlayer
import com.example.melodist.ui.components.player.NowPlayingLayout
import com.example.melodist.ui.components.player.PlaybackQueuePanel
import com.example.melodist.viewmodels.PlayerViewModel
import com.example.melodist.ui.screens.YouTubeBrowseScreenRoute
import com.example.melodist.ui.screens.*
import com.example.melodist.ui.screens.home.HomeScreenRoute
import com.example.melodist.ui.screens.library.LibraryScreenRoute
import com.example.melodist.utils.LocalPlayerViewModel
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.*



data class TabInfo(
    val config: ScreenConfig,
    val icon: ImageVector
)

private val mainTabs = listOf(
    TabInfo(ScreenConfig.Home, Icons.Filled.Home),
    TabInfo(ScreenConfig.Search, Icons.Filled.Search),
    TabInfo(ScreenConfig.Library, Icons.Filled.LibraryMusic),
)

private val bottomTabs = listOf(
    TabInfo(ScreenConfig.Account, Icons.Filled.Person),
    TabInfo(ScreenConfig.Settings, Icons.Filled.Settings),
)


@Composable
fun NavigationDesktop(rootComponent: RootComponent) {
    val childStack by rootComponent.childStack.subscribeAsState()
    val activeConfig = childStack.active.configuration

    val playerViewModel: PlayerViewModel = LocalPlayerViewModel.current

    val playerState by playerViewModel.uiState.collectAsState()
    val progressState by playerViewModel.progressState.collectAsState()
    val currentLyrics by playerViewModel.currentLyrics.collectAsState()
    var isNowPlayingExpanded by remember { mutableStateOf(false) }
    var isQueueVisible by remember { mutableStateOf(false) }
    var isLyricsVisible by remember { mutableStateOf(false) }

    val queueWidth = 420.dp
    val animatedWidth by animateDpAsState(queueWidth)

    Surface(
        modifier = Modifier
            .fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {

                NavigationRail(
                    modifier = Modifier.width(90.dp),
                    containerColor = Color.Transparent,
                ) {
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(20.dp))


                        mainTabs.forEach { tab ->
                            NavigationRailItem(
                                selected = activeConfig == tab.config,
                                onClick = {
                                    isNowPlayingExpanded = false
                                    rootComponent.switchTab(tab.config)
                                },
                                icon = { Icon(tab.icon, null) },
                                label = {
                                    Text(
                                        when (tab.config) {
                                            ScreenConfig.Home -> stringResource(Res.string.nav_home)
                                            ScreenConfig.Search -> stringResource(Res.string.nav_search)
                                            ScreenConfig.Library -> stringResource(Res.string.nav_library)
                                            else -> ""
                                        }
                                    )
                                },
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                alwaysShowLabel = false,
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        bottomTabs.forEach { tab ->
                            NavigationRailItem(
                                selected = activeConfig == tab.config,
                                onClick = {
                                    isNowPlayingExpanded = false
                                    isQueueVisible = false
                                    rootComponent.switchTab(tab.config)
                                },
                                icon = { Icon(tab.icon, null) },
                                label = {
                                    Text(
                                        when (tab.config) {
                                            ScreenConfig.Account -> stringResource(Res.string.nav_account)
                                            ScreenConfig.Settings -> stringResource(Res.string.nav_settings)
                                            else -> ""
                                        }
                                    )
                                },
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                alwaysShowLabel = false,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }

                val currentSong = playerState.currentSong

                val bottomPadding = if(currentSong!= null) 0.dp else 16.dp

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 16.dp, bottom = bottomPadding) // Margen exterior seguro
                ) {

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(16.dp))
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))

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
                                enter = slideInVertically(
                                    initialOffsetY = { it }
                                ) + fadeIn(),
                                exit = slideOutVertically(
                                    targetOffsetY = { it }
                                ) + fadeOut()
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
                                        onToggleLyrics = {
                                            if (!isLyricsVisible) {
                                                playerViewModel.fetchLyrics()
                                            }
                                            isLyricsVisible = !isLyricsVisible
                                        },
                                        showLyrics = isLyricsVisible,
                                        lyrics = currentLyrics
                                    )
                                }
                            }
                        }

                        if (isQueueVisible) {
                            Row(modifier = Modifier.fillMaxHeight()) {
                                Spacer(Modifier.width(12.dp))

                                PlaybackQueuePanel(
                                    state = playerState,
                                    onDismiss = { isQueueVisible = false },
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(animatedWidth)
                                        .clip(RoundedCornerShape(16.dp))
                                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                )
                            }
                        }

                    }
                }
            }
            val currentSong = playerState.currentSong
            AnimatedVisibility(
                visible = currentSong != null,
                enter = slideInVertically(
                    initialOffsetY = { it }
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it }
                ) + fadeOut()
            ) {
                MiniPlayer(
                    progressState = progressState,
                    onClickExpand = { isNowPlayingExpanded = true },
                    onToggleNowPlaying = { isNowPlayingExpanded = !isNowPlayingExpanded },
                    isNowPlayingExpanded = isNowPlayingExpanded,
                    onToggleQueue = { isQueueVisible = !isQueueVisible },
                    isQueueVisible = isQueueVisible,
                    modifier = Modifier.fillMaxWidth()
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

