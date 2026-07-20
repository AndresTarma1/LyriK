package com.example.melodist.ui.components.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.melodist.navigation.Route
import com.example.melodist.models.MediaMetadata
import com.example.melodist.ui.components.EqualizerDialog
import com.example.melodist.utils.LocalUserPreferences
import com.example.melodist.viewmodels.PlayerUiState
import com.metrolist.innertube.models.MediaInfo
import lyrik.composeapp.generated.resources.Res
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import com.example.melodist.ui.components.background.NowBackground
import kotlinx.coroutines.launch

enum class NowPlayingTab { LYRICS, QUEUE, INFO }

@Composable
fun NowPlayingLayout(
    state: PlayerUiState,
    song: MediaMetadata,
    onCollapse: () -> Unit,
    onNavigate: ((Route) -> Unit)? = null,
    selectedTab: NowPlayingTab = NowPlayingTab.QUEUE,
    onTabSelected: (NowPlayingTab) -> Unit = {},
    lyrics: String? = null,
    mediaInfo: MediaInfo? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    val preferencesRepo = LocalUserPreferences.current
    val equalizerBands by preferencesRepo.equalizerBands.collectAsState(initial = List(5) { 0f })

    NowBackground(
        imageUrl = song.thumbnailUrl,
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            val isCompact = maxWidth < 900.dp || maxHeight < 560.dp

            Box(modifier = Modifier.fillMaxSize()) {
                if (isCompact) {
                    CompactNowPlayingLayout(
                        song = song,
                        state = state,
                        lyrics = lyrics,
                        mediaInfo = mediaInfo,
                        selectedTab = selectedTab,
                        onTabSelected = onTabSelected,
                        onNavigate = onNavigate,
                        onCollapse = onCollapse,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                } else {
                    ExpandedNowPlayingLayout(
                        song = song,
                        state = state,
                        lyrics = lyrics,
                        mediaInfo = mediaInfo,
                        selectedTab = selectedTab,
                        onTabSelected = onTabSelected,
                        onNavigate = onNavigate,
                        onCollapse = onCollapse,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
            }

            TopActionOverlay(
                showMenu = showMenu,
                onMenuToggle = { showMenu = it },
                onOpenEqualizer = { showEqualizer = true }
            )
        }
    }

    if (showEqualizer) {
        EqualizerDialog(
            bands = equalizerBands,
            onBandsChange = { scope.launch { preferencesRepo.setEqualizerBands(it) } },
            onDismiss = { showEqualizer = false }
        )
    }
}

@Composable
private fun ExpandedNowPlayingLayout(
    song: MediaMetadata,
    state: PlayerUiState,
    lyrics: String?,
    mediaInfo: MediaInfo?,
    selectedTab: NowPlayingTab,
    onTabSelected: (NowPlayingTab) -> Unit,
    onNavigate: ((Route) -> Unit)?,
    onCollapse: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        Column(
            modifier = Modifier.weight(0.42f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CoverArt(
                url = song.thumbnailUrl,
                title = song.title,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .sizeIn(maxHeight = 380.dp, maxWidth = 380.dp)
                    .heroCoverElement(song.id, sharedTransitionScope, animatedVisibilityScope),
            )
            Spacer(Modifier.height(18.dp))
            SongHeader(
                state = state,
                song = song,
                textAlign = TextAlign.Center,
                onNavigate = onNavigate,
                onCollapse = onCollapse,
                compact = false
            )
        }

        Column(modifier = Modifier.weight(0.58f).fillMaxHeight()) {
            NowPlayingTabRow(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                queueCount = state.queue.size,
                modifier = Modifier.padding(top = 52.dp, bottom = 12.dp)
            )
            Box(modifier = Modifier.weight(1f)) {
                NowPlayingTabContent(
                    tab = selectedTab,
                    song = song,
                    state = state,
                    lyrics = lyrics,
                    mediaInfo = mediaInfo,
                    lyricsTextStyle = MaterialTheme.typography.headlineSmall,
                    onNavigate = onNavigate,
                )
            }
        }
    }
}

@Composable
private fun CompactNowPlayingLayout(
    song: MediaMetadata,
    state: PlayerUiState,
    lyrics: String?,
    mediaInfo: MediaInfo?,
    selectedTab: NowPlayingTab,
    onTabSelected: (NowPlayingTab) -> Unit,
    onNavigate: ((Route) -> Unit)?,
    onCollapse: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 124.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverArt(
                url = song.thumbnailUrl,
                title = song.title,
                modifier = Modifier.size(108.dp)
                    .heroCoverElement(song.id, sharedTransitionScope, animatedVisibilityScope),
            )
            SongHeader(
                state = state,
                song = song,
                textAlign = TextAlign.Start,
                onNavigate = onNavigate,
                onCollapse = onCollapse,
                compact = true
            )
        }
        NowPlayingTabRow(selectedTab = selectedTab, onTabSelected = onTabSelected, queueCount = state.queue.size)
        Box(modifier = Modifier.weight(1f)) {
            NowPlayingTabContent(
                tab = selectedTab,
                song = song,
                state = state,
                lyrics = lyrics,
                lyricsTextStyle = MaterialTheme.typography.bodyLarge,
                onNavigate = onNavigate,
                mediaInfo = mediaInfo,
            )
        }
    }
}

private fun tabIcon(tab: NowPlayingTab) = when (tab) {
    NowPlayingTab.LYRICS -> Icons.Rounded.Lyrics
    NowPlayingTab.QUEUE -> Icons.AutoMirrored.Filled.QueueMusic
    NowPlayingTab.INFO -> Icons.Rounded.Info
}

@Composable
private fun NowPlayingTabRow(
    selectedTab: NowPlayingTab,
    onTabSelected: (NowPlayingTab) -> Unit,
    queueCount: Int,
    modifier: Modifier = Modifier,
) {
    val lyricsLabel = stringResource(Res.string.tab_lyrics)
    val queueLabel = stringResource(Res.string.tab_queue) + if (queueCount > 0) " ($queueCount)" else ""
    val infoLabel = stringResource(Res.string.tab_info)
    val tabs = NowPlayingTab.entries
    val selectedIndex = tabs.indexOf(selectedTab)

    PrimaryTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier,
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        tabs = {
            tabs.forEach { tab ->
                val selected = tab == selectedTab
                val label = when (tab) {
                    NowPlayingTab.LYRICS -> lyricsLabel
                    NowPlayingTab.QUEUE -> queueLabel
                    NowPlayingTab.INFO -> infoLabel
                }
                LeadingIconTab(
                    selected = selected,
                    onClick = { onTabSelected(tab) },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    icon = { Icon(tabIcon(tab), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        })
}

@Composable
private fun NowPlayingTabContent(
    tab: NowPlayingTab,
    song: MediaMetadata,
    state: PlayerUiState,
    lyrics: String?,
    mediaInfo: MediaInfo?,
    lyricsTextStyle: androidx.compose.ui.text.TextStyle,
    onNavigate: ((Route) -> Unit)?,
) {
    when (tab) {
        NowPlayingTab.LYRICS -> LyricsContent(lyrics = lyrics, textAlign = TextAlign.Start, style = lyricsTextStyle)
        NowPlayingTab.QUEUE -> PlaybackQueuePanel(
            state = state,
            onDismiss = {},
            modifier = Modifier.fillMaxSize(),
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            showCloseButton = false,
        )
        NowPlayingTab.INFO -> SongInfoContent(song = song, state = state, mediaInfo = mediaInfo, onNavigate = onNavigate)
    }
}
