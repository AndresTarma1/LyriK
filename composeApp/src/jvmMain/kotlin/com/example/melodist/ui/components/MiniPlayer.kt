package com.example.melodist.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.melodist.player.PlaybackState
import com.example.melodist.ui.utils.circleAwareShape
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.utils.isWideThumbnail
import com.example.melodist.viewmodels.PlayerProgressState
import com.example.melodist.viewmodels.RepeatMode
import ir.mahozad.multiplatform.wavyslider.WaveDirection
import ir.mahozad.multiplatform.wavyslider.material3.WavySlider
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.onHover


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalComposeUiApi::class)
@Composable
fun MiniPlayer(
    progressState: PlayerProgressState,
    onClickExpand: () -> Unit,
    onToggleNowPlaying: () -> Unit,
    isNowPlayingExpanded: Boolean,
    onToggleQueue: () -> Unit,
    isQueueVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val playerViewModel = LocalPlayerViewModel.current
    val state by playerViewModel.uiState.collectAsState()
    val song = state.currentSong ?: return

    val computedProgress = remember(progressState.positionMs, progressState.durationMs, song.id) {
        if (progressState.durationMs > 0)
            progressState.positionMs.toFloat() / progressState.durationMs.toFloat()
        else 0f
    }
    var seekValue by remember(song.id) { mutableStateOf<Float?>(null) }
    val sliderProgress = seekValue ?: computedProgress
    val isError = state.playbackState == PlaybackState.ERROR

    val isPlaying = state.playbackState == PlaybackState.PLAYING
    val isLoading = state.playbackState == PlaybackState.LOADING

    val ratio = remember(song.thumbnailUrl) {
        if (isWideThumbnail(song.thumbnailUrl)) 16f / 9f else 1f
    }

    Surface(
        modifier = modifier.fillMaxWidth().height(88.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // —─ IZQUIERDA: Portada e Info —─—─—─—─—─—─—─—─—─—
                BoxWithConstraints(
                    modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    val infoHeight = maxHeight
                    val thumbSize = (infoHeight * 0.85f).coerceAtMost(64.dp)
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        var isHovered by remember { mutableStateOf(false) }
                        Box(modifier = Modifier
                            .sizeIn(maxWidth = thumbSize * ratio, maxHeight = thumbSize)
                            .aspectRatio(ratio)
                            .onHover{ isHovered = it}
                            .clickable(onClick = onToggleNowPlaying)
                            .pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            MelodistImage(
                                url = song.thumbnailUrl,
                                contentDescription = song.title,
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(4.dp),
                                contentScale = ContentScale.Crop,
                                placeholderType = PlaceholderType.SONG,
                                iconSize = 24.dp,
                                isLowRes = true  // ✅ Miniatura pequeña, baja resolución suficiente
                            )

                            if(isHovered){

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .matchParentSize()
                                        .background(
                                            color = Color.Black.copy(alpha = 0.6f),
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if(isNowPlayingExpanded) Icons.Default.ArrowDropDown else Icons.Default.ArrowDropUp,
                                        contentDescription = if(isNowPlayingExpanded) stringResource(Res.string.mp_collapse) else stringResource(Res.string.mp_expand),
                                        tint = Color.White
                                    )
                                }
                            }


                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClick = onToggleNowPlaying)
                                .pointerHoverIcon(PointerIcon.Hand),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isError) state.error ?: stringResource(Res.string.mp_error) else song.artists.joinToString(", ") { it.name },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                            tooltip = { Text(if (state.currentSong?.liked == true) stringResource(Res.string.mp_unlike) else stringResource(Res.string.mp_like)) },
                            state = rememberTooltipState()
                        ) {
                            IconButton(
                                onClick = { playerViewModel.toggleLike() },
                                modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Icon(
                                    imageVector = if (state.currentSong?.liked == true) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = stringResource(Res.string.mp_like),
                                    tint = if (state.currentSong?.liked == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // —— CENTRO: Controles y Progreso ——
                Column(
                    modifier = Modifier.weight(1.6f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
                ) {
                    // ... (Fila de botones anterior) ...

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                    ) {
                        // Botón Aleatorio
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                            tooltip = { Text(stringResource(Res.string.mp_shuffle)) },
                            state = rememberTooltipState()
                        ) {
                            IconButton(
                                onClick = { playerViewModel.toggleShuffle() },
                                modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (state.isShuffled) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else Color.Transparent,
                                )
                            ) {
                                Icon(
                                    Icons.Rounded.Shuffle, stringResource(Res.string.mp_shuffle),
                                    modifier = Modifier.size(20.dp),
                                    tint = if (state.isShuffled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                            tooltip = { Text(stringResource(Res.string.mp_previous)) },
                            state = rememberTooltipState()
                        ) {
                            IconButton(
                                onClick = { playerViewModel.previous() },
                                modifier = Modifier.size(40.dp).pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Icon(
                                    Icons.Rounded.SkipPrevious, stringResource(Res.string.mp_previous),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                            tooltip = { Text(if (isPlaying) stringResource(Res.string.tray_pause) else stringResource(Res.string.tray_play)) },
                            state = rememberTooltipState()
                        ) {
                            FilledIconButton(
                                onClick = { playerViewModel.togglePlayPause() },
                                modifier = Modifier.size(46.dp).pointerHoverIcon(PointerIcon.Hand),
                                shape = circleAwareShape(),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.onSurface,
                                    contentColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (isPlaying) stringResource(Res.string.tray_pause) else stringResource(Res.string.tray_play),
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }

                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                            tooltip = { Text(stringResource(Res.string.mp_next)) },
                            state = rememberTooltipState()
                        ) {
                            IconButton(
                                    onClick = { playerViewModel.next() },
                            modifier = Modifier.size(40.dp).pointerHoverIcon(PointerIcon.Hand)
                            ) {
                            Icon(
                                Icons.Rounded.SkipNext, stringResource(Res.string.mp_next),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        }

                        // Botón Repetir
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                            tooltip = {
                                Text(when (state.repeatMode) {
                                    RepeatMode.ONE -> stringResource(Res.string.mp_repeat_one)
                                    RepeatMode.ALL -> stringResource(Res.string.mp_repeat_all)
                                    else -> stringResource(Res.string.mp_repeat)
                                })
                            },
                            state = rememberTooltipState()
                        ) {
                            IconButton(
                                onClick = { playerViewModel.toggleRepeat() },
                                modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (state.repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary.copy(
                                        alpha = 0.12f
                                    )
                                    else Color.Transparent
                                )
                            ) {
                                val isRepeatOff = state.repeatMode == RepeatMode.OFF
                                val repeatIcon =
                                    if (state.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat
                                Icon(
                                    repeatIcon, stringResource(Res.string.mp_repeat),
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isRepeatOff) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TimeText(progressState.positionMs, seekValue)

                        // MEJORA: Usar un Slider transparente sobre el Wavy para permitir Seek
                        Box(modifier = Modifier.weight(1f).height(24.dp), contentAlignment = Alignment.Center) {
                            // 1. Estado local para saber si el usuario está moviendo el slider
                            var isDragging by remember { mutableStateOf(false) }

                            WavySlider(
                                value = sliderProgress,
                                onValueChange = {
                                    isDragging = true // El usuario empezó a moverlo
                                    seekValue = it
                                },
                                onValueChangeFinished = {
                                    isDragging = false
                                    playerViewModel.seekTo((seekValue?.times(progressState.durationMs))?.toLong() ?: 0L)
                                    seekValue = null },
                                waveLength = 102.dp,
                                waveHeight = if (isPlaying || isDragging) 10.dp else 0.dp,
                                waveVelocity = if (isPlaying && !isDragging) {
                                    28.dp to WaveDirection.TAIL
                                } else {
                                    0.dp to WaveDirection.TAIL
                                },
                                waveThickness = 8.dp,
                                trackThickness = 9.dp,
                                incremental = false,
                            )
                        }

                        TimeText(progressState.durationMs)
                    }
                }

                // —— DERECHA: Botón Extra, Cola y Volumen ———————————————————
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val volumeFloat = (state.volume.coerceIn(0, 100)) / 100f
                        val volumePercent = state.volume.coerceIn(0, 100)

                        Text(
                            text = "$volumePercent",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                fontFeatureSettings = "tnum"
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.width(24.dp),
                            textAlign = TextAlign.End
                        )
                        Spacer(Modifier.width(4.dp))

                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                            Slider(
                                value = volumeFloat,
                                onValueChange = { playerViewModel.setVolume((it * 100).toInt()) },
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(16.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.onSurface,
                                    activeTrackColor = MaterialTheme.colorScheme.onSurface,
                                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                                    activeTickColor = Color.Transparent,
                                    inactiveTickColor = Color.Transparent
                                )
                            )
                        }

                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                            tooltip = { Text(if (volumeFloat == 0f) stringResource(Res.string.mp_unmute) else stringResource(Res.string.mp_mute)) },
                            state = rememberTooltipState()
                        ) {
                            IconButton(
                                onClick = { playerViewModel.setVolume(if (volumeFloat > 0f) 0 else 80) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    when {
                                        volumeFloat == 0f -> Icons.AutoMirrored.Rounded.VolumeOff
                                        volumeFloat < 0.4f -> Icons.AutoMirrored.Rounded.VolumeDown
                                        else -> Icons.AutoMirrored.Rounded.VolumeUp
                                    },
                                    stringResource(Res.string.mp_volume),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above,
                            4.dp
                        ),
                        tooltip = { Text(stringResource(Res.string.mp_queue)) },
                        state = rememberTooltipState()
                    ) {
                        IconButton(
                            onClick = onToggleQueue,
                            modifier = Modifier.size(40.dp).pointerHoverIcon(PointerIcon.Hand),
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = if (isQueueVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                containerColor = if (isQueueVisible) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else Color.Transparent
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, stringResource(Res.string.mp_queue), modifier = Modifier.size(24.dp))
                        }
                    }

                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                        tooltip = { Text(if (isNowPlayingExpanded) stringResource(Res.string.mp_collapse) else stringResource(Res.string.mp_expand)) },
                        state = rememberTooltipState()
                    ) {
                        IconButton(
                            onClick = onToggleNowPlaying,
                            modifier = Modifier.size(40.dp).pointerHoverIcon(PointerIcon.Hand),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (isNowPlayingExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else Color.Transparent
                            )
                        ) {
                            val rotation by animateFloatAsState(
                                targetValue = if (isNowPlayingExpanded) 0f else 180f,
                                animationSpec = tween(
                                    durationMillis = 300,
                                    easing = FastOutSlowInEasing
                                )
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                stringResource(Res.string.play_item),
                                modifier = Modifier.size(32.dp).graphicsLayer { rotationZ = rotation },
                                tint = if (isNowPlayingExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimeText(millis: Long, seekValue: Float? = null) {
    Text(
        text = formatPlayerTimeValue(millis),
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            fontFeatureSettings = "tnum" // Números monoespaciados para que no "bailen"
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(36.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val active = isDragged || isHovered

    val trackColor by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurface,
        tween(150), label = "trackColor"
    )

    // Desactiva el tamaño táctil mínimo de Material 3 para evitar saltos en la UI
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            interactionSource = interactionSource,
            modifier = modifier.height(16.dp),
            colors = SliderDefaults.colors(
                thumbColor = trackColor,
                activeTrackColor = trackColor,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                activeTickColor = trackColor,
                inactiveTickColor = trackColor
            )
        )
    }
}

