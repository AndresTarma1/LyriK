package com.example.musicApp.ui.screens

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.musicApp.navigation.Route
import com.example.musicApp.ui.components.SectionSkeleton
import com.example.musicApp.ui.components.layout.AppVerticalScrollbar
import com.example.musicApp.ui.components.layout.HorizontalScrollableRow
import com.example.musicApp.ui.screens.shared.SectionGridItem
import com.example.musicApp.utils.LocalPlayerViewModel
import com.example.musicApp.viewmodels.YouTubeBrowseState
import com.example.musicApp.viewmodels.YouTubeBrowseViewModel
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

    YouTubeBrowseScreen(
        uiState = uiState,
        title = title,
        onBack = onBack,
        onNavigate = onNavigate,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeBrowseScreen(
    uiState: YouTubeBrowseState,
    title: String,
    onBack: () -> Unit,
    onNavigate: (Route) -> Unit,
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
                    containerColor = Color.Transparent
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
                        onNavigate = onNavigate,
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
    onNavigate: (Route) -> Unit,
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
                        onNavigate = onNavigate,
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
    onNavigate: (Route) -> Unit,
) {
    if (section.items.isEmpty()) return

    val playerViewModel = LocalPlayerViewModel.current

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
                SectionGridItem(
                    item = section.items[index],
                    onNavigate = onNavigate,
                    playerViewModel = playerViewModel,
                    modifier = Modifier.widthIn(min = 200.dp),
                )
            }
        }
    }
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
