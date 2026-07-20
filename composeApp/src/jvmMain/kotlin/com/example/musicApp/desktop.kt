package com.example.musicApp

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.FrameWindowScope
import com.example.musicApp.ui.components.images.MelodistImage
import com.example.musicApp.ui.components.images.PlaceholderType
import com.example.musicApp.ui.components.skeletons.AnimatedEqualizer
import com.example.musicApp.viewmodels.PlayerViewModel
import com.kdroid.composetray.tray.api.Tray
import com.metrolist.innertube.models.AccountInfo
import lyrik.composeapp.generated.resources.Music_note_circle
import lyrik.composeapp.generated.resources.Res
import lyrik.composeapp.generated.resources.app_name
import lyrik.composeapp.generated.resources.offline_mode
import lyrik.composeapp.generated.resources.sync_now
import lyrik.composeapp.generated.resources.sync_now_syncing
import lyrik.composeapp.generated.resources.tray_exit
import lyrik.composeapp.generated.resources.tray_next
import lyrik.composeapp.generated.resources.tray_open
import lyrik.composeapp.generated.resources.tray_pause
import lyrik.composeapp.generated.resources.tray_play
import lyrik.composeapp.generated.resources.tray_previous
import lyrik.composeapp.generated.resources.ytm_sync
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.window.TitleBarScope
import org.jetbrains.skiko.FPSCounter

@Composable
fun TitleBarScope.DesktopTitleBar(
    currentSong: String?,
    isPlaying: Boolean,
    accountInfo: AccountInfo?,
    ytmSyncEnabled: Boolean,
    isSyncing: Boolean,
    isOfflineMode: Boolean,
    onToggleOfflineMode: (Boolean) -> Unit,
    onToggleSync: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
) {
    Row(
        modifier = Modifier.align(Alignment.Start).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(Res.string.app_name),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }

    Box(modifier = Modifier.align(Alignment.End).padding(end = 4.dp)) {
        var showMenu by remember { mutableStateOf(false) }
        IconButton(
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            onClick = { showMenu = true }) {
            if (isSyncing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else if (accountInfo?.thumbnailUrl != null) {
                MelodistImage(
                    url = accountInfo.thumbnailUrl,
                    contentDescription = accountInfo.name,
                    modifier = Modifier.size(24.dp),
                    shape = CircleShape,
                    placeholderType = PlaceholderType.ARTIST,
                )
            } else {
                Icon(
                    if (ytmSyncEnabled) Icons.Rounded.CloudSync else Icons.Rounded.CloudOff,
                    contentDescription = stringResource(Res.string.ytm_sync),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            if (accountInfo != null) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MelodistImage(
                        url = accountInfo.thumbnailUrl,
                        contentDescription = accountInfo.name,
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        placeholderType = PlaceholderType.ARTIST,
                    )
                    Column {
                        Text(accountInfo.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        accountInfo.email?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.ytm_sync)) },
                trailingIcon = { Switch(checked = ytmSyncEnabled, onCheckedChange = null) },
                onClick = { onToggleSync(!ytmSyncEnabled) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            DropdownMenuItem(
                text = { Text(stringResource(if (isSyncing) Res.string.sync_now_syncing else Res.string.sync_now)) },
                leadingIcon = {
                    if (isSyncing) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Sync, contentDescription = null)
                },
                enabled = !isSyncing,
                onClick = { onSyncNow(); showMenu = false },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.offline_mode)) },
                trailingIcon = { Switch(checked = isOfflineMode, onCheckedChange = null) },
                onClick = { onToggleOfflineMode(!isOfflineMode) },
            )
        }
    }

    AnimatedContent(
        targetState = currentSong,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    ) { song ->
        if (song != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (isPlaying) {
                    AnimatedEqualizer(
                        isPlaying = true,
                        modifier = Modifier.size(width = 20.dp, height = 14.dp)
                    )
                }
                Text(
                    text = song,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(max = 450.dp)
                        .basicMarquee(
                            velocity = if(isPlaying) 25.dp else 0.dp,
                        ),
                )
            }
        }
    }
}

@Composable
fun ApplicationScope.TrayCustom(
    playerViewModel: PlayerViewModel,
    onToggleVisibility: () -> Unit,
    onShow: () -> Unit,
    handleExit: () -> Unit
) {

    val tooltipText = stringResource(Res.string.app_name)
    val pauseLabel = stringResource(Res.string.tray_pause)
    val playLabel = stringResource(Res.string.tray_play)
    val nextLabel = stringResource(Res.string.tray_next)
    val previousLabel = stringResource(Res.string.tray_previous)
    val openLabel = stringResource(Res.string.tray_open)
    val exitLabel = stringResource(Res.string.tray_exit)

    Tray(
        icon = Res.drawable.Music_note_circle,
        tooltip = tooltipText,
        primaryAction = { onToggleVisibility() },
    ) {
        Item(
            label = "$pauseLabel/$playLabel",
            onClick = { playerViewModel.togglePlayPause() }
        )
        Item(
            label = nextLabel,
            onClick = { playerViewModel.next() }
        )
        Item(
            label = previousLabel,
            onClick = { playerViewModel.previous() }
        )
        Divider()
        Item(
            label = openLabel,
            onClick = { onShow() }
        )
        Divider()
        Item(
            label = exitLabel,
            onClick = { handleExit() }
        )
    }
}

@Composable
fun FrameWindowScope.windowBackgroundFlashingWorkaround(
    themeBg: androidx.compose.ui.graphics.Color
) {
    val awtColor = java.awt.Color(themeBg.toArgb())
    LaunchedEffect(window, themeBg) {
        window.background = awtColor
        window.contentPane.background = awtColor
    }
}

@Composable
fun FpsCounter(
    modifier: Modifier = Modifier
) {
    val fpsCounter = remember {
        FPSCounter(periodSeconds = 1.0)
    }

    var fps by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos {
                fpsCounter.tick()

                // Solo cambia una vez por segundo
                fps = fpsCounter.average
            }
        }
    }

    Text(
        text = "FPS: $fps",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(4.dp)
    )
}