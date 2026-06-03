package com.example.melodist.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.melodist.navigation.Route
import com.example.melodist.ui.components.ItemContentSource
import com.example.melodist.ui.components.MediaGridItem
import com.example.melodist.ui.components.PlaceholderType
import com.example.melodist.ui.components.SectionSkeleton
import com.example.melodist.ui.components.layout.AppVerticalScrollbar
import com.example.melodist.ui.components.layout.HorizontalScrollableRow
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.viewmodels.PlayerViewModel
import com.example.melodist.viewmodels.YouTubeBrowseState
import com.example.melodist.viewmodels.YouTubeBrowseViewModel
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.pages.BrowseResult
import lyrik.composeapp.generated.resources.Res
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun YouTubeBrowseScreenRoute(
    viewModel: YouTubeBrowseViewModel,
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val title by viewModel.title.collectAsState()
    val playerViewModel = LocalPlayerViewModel.current

    YouTubeBrowseScreen(
        uiState = uiState,
        title = title,
        onBack = onBack,
        onItemClick = { item ->
            when (item) {
                is SongItem -> {
                    playerViewModel.playSingle(item)
                }
                is AlbumItem -> { onNavigate(Route.Album(item.id)) }
                is PlaylistItem -> { onNavigate(Route.Playlist(item.id)) }
                is ArtistItem -> { onNavigate(Route.Artist(item.id)) }
                else -> {}
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun YouTubeBrowseScreen(
    uiState: YouTubeBrowseState,
    title: String,
    onBack: () -> Unit,
    onItemClick: (YTItem) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title.ifEmpty { stringResource(Res.string.title_fallback) },
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 28.sp),
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back_label),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.81f),
                ),
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent {
                    if (it.key == Key.Escape && it.type == KeyEventType.KeyUp) {
                        onBack()
                        true
                    } else {
                        false
                    }
                }
        ) {
            when (uiState) {
                is YouTubeBrowseState.Loading -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        repeat(3) { SectionSkeleton() }
                    }
                }

                is YouTubeBrowseState.Success -> {
                    BrowseContent(
                        result = uiState.result,
                        onItemClick = onItemClick,
                    )
                }

                is YouTubeBrowseState.Error -> {
                    YouTubeBrowseError(
                        message = uiState.message,
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowseContent(
    result: BrowseResult,
    onItemClick: (YTItem) -> Unit,
) {
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            result.items.forEach { section ->
                item {
                    BrowseSection(
                        section = section,
                        onItemClick = onItemClick,
                    )
                }
            }

            if (result.items.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(Res.string.no_content_available),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        AppVerticalScrollbar(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun BrowseSection(
    section: BrowseResult.Item,
    onItemClick: (YTItem) -> Unit,
) {
    if (section.items.isEmpty()) return

    Column(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
        section.title?.let { title ->
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }

        val scrollState = rememberLazyListState()
        HorizontalScrollableRow(
            modifier = Modifier.fillMaxWidth(),
            state = scrollState,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(
                count = section.items.size,
                key = { index -> "browse_${section.title}_${section.items[index].id}" }
            ) { index ->
                BrowseItemCard(
                    item = section.items[index],
                    onClick = { onItemClick(section.items[index]) },
                )
            }
        }
    }
}

@Composable
private fun BrowseItemCard(
    item: YTItem,
    onClick: () -> Unit,
) {
    MediaGridItem(
        item = item,
        source = ItemContentSource.YOUTUBE,
        onClick = onClick,
        modifier = Modifier
            .width(200.dp)
    )
}

@Composable
private fun YouTubeBrowseError(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.could_not_load_content),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
