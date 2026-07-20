package com.example.melodist.ui.components.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.melodist.ui.components.layout.AppVerticalScrollbar
import com.example.melodist.utils.LocalPlayerViewModel
import lyrik.composeapp.generated.resources.Res
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricsContent(
    lyrics: String?,
    textAlign: TextAlign = TextAlign.Center,
    style: TextStyle
) {
    val playerViewModel = LocalPlayerViewModel.current
    val synced by playerViewModel.syncedLyrics.collectAsState()
    val progress by playerViewModel.progressState.collectAsState()
    val syncedLines = synced

    val bigLyricsStyle = style.copy(
        fontSize = style.fontSize * 1.7f,
        lineHeight = style.lineHeight * 1.8f,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.1.sp
    )

    when {
        !syncedLines.isNullOrEmpty() -> {
            SyncedLyricsView(
                lines = syncedLines,
                positionMs = progress.positionMs,
                onSeek = { playerViewModel.seekTo(it) },
                modifier = Modifier.fillMaxSize(),
                textAlign = textAlign == TextAlign.Start,
            )
        }
        lyrics == null -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                LoadingIndicator()
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(Res.string.lyrics_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
        lyrics.isBlank() -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Rounded.Lyrics,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(Res.string.lyrics_not_found),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        else -> {
            val scrollState = rememberScrollState()
            val fadeHeight = 48.dp

            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 24.dp)
                        .fadingEdges(fadeHeight)
                ) {
                    Spacer(Modifier.height(fadeHeight))
                    Text(
                        text = lyrics,
                        modifier = Modifier.fillMaxWidth(),
                        style = bigLyricsStyle,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
                        textAlign = textAlign,
                    )
                    Spacer(Modifier.height(96.dp))
                }
                AppVerticalScrollbar(
                    state = scrollState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(8.dp)
                )
            }
        }
    }
}

internal fun Modifier.fadingEdges(fadeHeight: Dp): Modifier = this
    .alpha(0.99f)
    .drawWithContent {
        drawContent()
        val fadePx = fadeHeight.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startY = 0f,
                endY = fadePx
            ),
            blendMode = BlendMode.DstIn
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startY = size.height - fadePx,
                endY = size.height
            ),
            blendMode = BlendMode.DstIn
        )
    }
