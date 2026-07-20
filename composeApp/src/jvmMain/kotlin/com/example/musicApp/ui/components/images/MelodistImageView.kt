package com.example.musicApp.ui.components.images

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.musicApp.utils.LocalUserPreferences

enum class PlaceholderType {
    SONG, ALBUM, ARTIST, PLAYLIST, DOWNLOADS
}

@Composable
fun MelodistImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    placeholderType: PlaceholderType = PlaceholderType.SONG,
    iconSize: Dp = 32.dp,
    contentScale: ContentScale = ContentScale.Fit,
    alignment: Alignment = Alignment.Center,
    isLowRes: Boolean = false,
) {
    val placeholderIcon: ImageVector = when (placeholderType) {
        PlaceholderType.SONG -> Icons.Default.MusicNote
        PlaceholderType.ALBUM -> Icons.Default.Album
        PlaceholderType.ARTIST -> Icons.Default.Person
        PlaceholderType.PLAYLIST -> Icons.Default.MusicNote
        PlaceholderType.DOWNLOADS -> Icons.Default.Download
    }


    val placeholderTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val placeholderBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val imagesEnabled by LocalUserPreferences.current.imagesEnabled.collectAsState(true)
    val highResEnabled by LocalUserPreferences.current.highResCoverArt.collectAsState(true)

    val finalIconSize = if (placeholderType == PlaceholderType.DOWNLOADS) 64.dp else iconSize

    if (url.isNullOrBlank() || !imagesEnabled) {
        Box(
            modifier = modifier.clip(shape).background(placeholderBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = placeholderIcon,
                contentDescription = contentDescription,
                modifier = Modifier.size(finalIconSize),
                tint = placeholderTint
            )
        }
    } else {

        val effectiveLowRes = isLowRes || !highResEnabled
        val coilSize = if (effectiveLowRes) 320 else 512

        AsyncImage(
            model = ImageRequest.Builder(LocalPlatformContext.current)
                .data(url)
                .crossfade(true)
                .size(coilSize)
                .build(),
            contentDescription = contentDescription,
            modifier = modifier
                .clip(shape)
                .clipToBounds()
                .background(placeholderBg),
            contentScale = contentScale,
            alignment = alignment,
            placeholder = ColorPainter(Color.DarkGray),
            error = ColorPainter(Color.DarkGray)
        )
    }
}
