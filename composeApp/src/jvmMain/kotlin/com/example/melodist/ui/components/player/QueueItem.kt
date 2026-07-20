package com.example.melodist.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.melodist.models.MediaMetadata
import com.example.melodist.ui.components.MelodistImage
import com.example.melodist.ui.components.PlaceholderType
import com.example.melodist.ui.components.formatPlayerTimeValue
import com.example.melodist.ui.components.song.DownloadIndicator
import com.example.melodist.ui.components.skeletons.AnimatedEqualizer
import com.example.melodist.utils.LocalDownloadViewModel
import com.example.melodist.utils.isWideThumbnail
import com.example.melodist.ui.components.context.SongContextMenuPopup
import com.example.melodist.ui.helpers.rememberSongDownloadState
import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import lyrik.composeapp.generated.resources.Res
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.onHover

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun QueueItem(
    song: MediaMetadata,
    isCurrent: Boolean,
    isDragging: Boolean = false,
    dragModifier: Modifier = Modifier,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val downloadViewModel = LocalDownloadViewModel.current
    val downloadState by rememberSongDownloadState(song.id, downloadViewModel)

    var isHovered by remember { mutableStateOf(false) }

    val currentBg = if (isCurrent && !isDragging)
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)
    else Color.Transparent

    var showMenu by remember { mutableStateOf(false) }
    Surface(
        color = currentBg,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(dragModifier)
            .clickable(onClick = onClick)
            .onPointerEvent(PointerEventType.Press) { event ->
                when {
                    event.buttons.isSecondaryPressed -> {
                        showMenu = true
                    }
                }
            }
            .onHover { isHovered = it }
            .pointerHoverIcon(if (isDragging) PointerIcon.Crosshair else PointerIcon.Hand),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .then(
                            if (isCurrent) Modifier.border(
                                2.dp,
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(8.dp)
                            ) else Modifier
                        )
                ) {
                    MelodistImage(
                        url = song.thumbnailUrl,
                        contentDescription = song.title,
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(8.dp),
                        placeholderType = PlaceholderType.SONG,
                        iconSize = 22.dp,
                        contentScale = ContentScale.Crop,
                    )

                    if (isCurrent) {
                        Box(
                            modifier = Modifier.matchParentSize().background(
                                Color.Black.copy(alpha = 0.35f), shape = RoundedCornerShape(8.dp)
                            )
                        ) {
                            AnimatedEqualizer(
                                isPlaying = true,
                                modifier = Modifier.size(20.dp).align(Alignment.Center)
                            )
                        }
                    } else if (isHovered && !isDragging) {
                        Box(
                            modifier = Modifier.matchParentSize().background(
                                Color.Black.copy(alpha = 0.3f), shape = RoundedCornerShape(8.dp)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = stringResource(Res.string.play_item),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp).align(Alignment.Center)
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artists.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            DownloadIndicator(state = downloadState)

            if (isHovered && !isDragging) {
                IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Default.PlaylistRemove,
                        stringResource(Res.string.remove_from_queue),
                        modifier = Modifier.size(19.dp)
                    )
                }
            } else {
                if (song.duration > 0) {
                    Text(
                        formatPlayerTimeValue(song.duration * 1000L),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }

        SongContextMenuPopup(
            expanded = showMenu,
            onDismiss = { showMenu = false },
            song = song.toSongItem(),
        )
    }
}

internal fun MediaMetadata.toSongItem(): SongItem = SongItem(
    id = id,
    title = title,
    artists = artists.map { Artist(name = it.name, id = it.id) },
    album = album?.let { Album(name = it.title, id = it.id) },
    duration = duration.takeIf { it > 0 },
    thumbnail = thumbnailUrl.orEmpty(),
    explicit = explicit
)
