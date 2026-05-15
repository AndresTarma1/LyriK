package com.example.melodist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.melodist.ui.components.layout.HorizontalScrollableRow

@Composable
internal fun AlbumScreenSkeletonContent() {
    val brush = shimmerBrush()

    Row(modifier = Modifier.fillMaxSize().padding(start = 48.dp, end = 24.dp)) {
        Column(
            modifier = Modifier.width(320.dp).padding(top = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) { InfoPanelSkeleton(brush, coverSize = 240.dp) }
        Spacer(Modifier.width(32.dp))
        Column(modifier = Modifier.weight(1f).padding(top = 24.dp, end = 12.dp)) {
            SongListSkeleton(brush, count = 8)
        }
    }
}

@Composable
internal fun PlaylistScreenSkeletonContent() {
    val brush = shimmerBrush()

    Row(modifier = Modifier.fillMaxSize().padding(start = 48.dp, end = 24.dp)) {
        Column(
            modifier = Modifier.width(320.dp).padding(top = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) { InfoPanelSkeleton(brush, coverSize = 240.dp) }
        Spacer(Modifier.width(32.dp))
        Column(modifier = Modifier.weight(1f).padding(top = 24.dp, end = 12.dp)) {
            PlaylistSongListSkeleton(brush, count = 8)
        }
    }
}

@Composable
internal fun ArtistScreenSkeletonContent() {
    val brush = shimmerBrush()
    val surface = MaterialTheme.colorScheme.surface

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.28f))
        ) {
            Box(Modifier.fillMaxSize().background(brush))
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.Transparent,
                                0.30f to Color.Transparent,
                                0.58f to surface.copy(alpha = 0.36f),
                                0.78f to surface.copy(alpha = 0.82f),
                                1.00f to surface
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.Black.copy(alpha = 0.36f),
                                0.70f to Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp, vertical = 32.dp)
            ) {
                Box(Modifier.width(320.dp).height(54.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                Spacer(Modifier.height(10.dp))
                Box(Modifier.width(180.dp).height(18.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth(0.48f).height(15.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth(0.34f).height(15.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.width(118.dp).height(42.dp).clip(CircleShape).background(brush))
                    Box(Modifier.width(96.dp).height(42.dp).clip(CircleShape).background(brush))
                    Box(Modifier.width(150.dp).height(42.dp).clip(CircleShape).background(brush))
                    Box(Modifier.size(36.dp).clip(CircleShape).background(brush))
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            repeat(3) {
                ArtistSectionSkeleton(brush)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun SkeletonSongRow(brush: Brush) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Index / PlayArrow box — 36dp wide (matches Box(modifier=Modifier.width(36.dp)))
        Box(Modifier.width(36.dp).height(36.dp).clip(RoundedCornerShape(4.dp)).background(brush))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            // title — bodyLarge SemiBold
            Box(Modifier.fillMaxWidth(0.55f).height(18.dp).clip(RoundedCornerShape(4.dp)).background(brush))
            Spacer(Modifier.height(5.dp))
            // artist — bodySmall
            Box(Modifier.fillMaxWidth(0.3f).height(13.dp).clip(RoundedCornerShape(4.dp)).background(brush))
        }
        Spacer(Modifier.width(12.dp))
        // duration text
        Box(Modifier.width(36.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).background(brush))
    }
}

@Composable
internal fun PlaylistSkeletonSongRow(brush: Brush) {
    // Matches PlaylistSongItem: index(36dp) + thumbnail(44dp) + col(title+artist) + duration
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail — 44x44, RoundedCornerShape(6)
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(brush))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            // title — bodyLarge SemiBold
            Box(Modifier.fillMaxWidth(0.55f).height(18.dp).clip(RoundedCornerShape(4.dp)).background(brush))
            Spacer(Modifier.height(5.dp))
            // artist — bodySmall
            Box(Modifier.fillMaxWidth(0.35f).height(13.dp).clip(RoundedCornerShape(4.dp)).background(brush))
        }
        Spacer(Modifier.width(12.dp))
        // duration text
        Box(Modifier.width(36.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).background(brush))
    }
}

@Composable
internal fun InfoPanelSkeleton(brush: Brush, coverSize: Dp) {

    Spacer(Modifier.height(20.dp))
    // Cover — Card with shadow + RoundedCornerShape(8dp)
    Box(Modifier.size(coverSize).clip(RoundedCornerShape(8.dp)).background(brush))
    Spacer(Modifier.height(24.dp))
    // Title — headlineMedium (up to 2 lines), first line wider
    Box(Modifier.width(200.dp).height(28.dp).clip(RoundedCornerShape(4.dp)).background(brush))
    Spacer(Modifier.height(6.dp))
    // Subtitle — "Álbum • year" (bodyMedium)
    Box(Modifier.width(120.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).background(brush))
    Spacer(Modifier.height(3.dp))
    // Song count — bodySmall
    Box(Modifier.width(150.dp).height(13.dp).clip(RoundedCornerShape(4.dp)).background(brush))
    Spacer(Modifier.height(24.dp))
    // Action buttons: save(44) + play(56) + shuffle(44) + download(44) — all spaced 12dp
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(brush))   // save/bookmark
        Box(Modifier.size(56.dp).clip(CircleShape).background(brush))   // play FAB
        Box(Modifier.size(44.dp).clip(CircleShape).background(brush))   // shuffle
        Box(Modifier.size(44.dp).clip(CircleShape).background(brush))   // download
    }
}

@Composable
internal fun SongListSkeleton(brush: Brush, count: Int) {
    repeat(count) { i ->
        SkeletonSongRow(brush)
        if (i < count - 1) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.08f),
                modifier = Modifier.padding(start = 48.dp)
            )
        }
    }
}

@Composable
internal fun PlaylistSongListSkeleton(brush: Brush, count: Int) {
    // Uses PlaylistSkeletonSongRow that includes the song thumbnail
    repeat(count) { i ->
        PlaylistSkeletonSongRow(brush)
        if (i < count - 1) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.08f),
                modifier = Modifier.padding(start = 104.dp)  // 12 + 36 + 8 + 44 + 4 = start after thumbnail
            )
        }
    }
}

@Composable
internal fun ArtistSectionSkeleton(brush: Brush) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Section title — titleLarge
        Box(
            Modifier
                .fillMaxWidth(0.3f)
                .height(22.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
        Spacer(Modifier.height(12.dp))
        // Cards row — matches ArtistSectionRow HorizontalScrollableRow
        val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
        HorizontalScrollableRow(
            modifier = Modifier.fillMaxWidth(),
            state = rowState,
            contentPadding = PaddingValues(horizontal = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(5) {
                // Matches ArtistSectionCard: Column(width 150dp, padding 8dp) { image(134dp) + title + subtitle }
                Column(
                    modifier = Modifier
                        .width(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Box(Modifier.size(134.dp).clip(RoundedCornerShape(8.dp)).background(brush))
                    Spacer(Modifier.height(8.dp))
                    // Title — bodyMedium SemiBold
                    Box(
                        Modifier.fillMaxWidth(0.85f).height(14.dp)
                            .clip(RoundedCornerShape(4.dp)).background(brush)
                    )
                    Spacer(Modifier.height(4.dp))
                    // Subtitle — bodySmall
                    Box(
                        Modifier.fillMaxWidth(0.55f).height(12.dp)
                            .clip(RoundedCornerShape(4.dp)).background(brush)
                    )
                }
            }
        }
    }
}
