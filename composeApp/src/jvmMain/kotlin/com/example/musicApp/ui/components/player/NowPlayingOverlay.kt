package com.example.musicApp.ui.components.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.musicApp.utils.LocalPlayerViewModel
import lyrik.composeapp.generated.resources.Res
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import java.util.Locale
import kotlin.math.abs

internal fun formatSpeed(speed: Float): String = String.format(Locale.US, "%.1fx", speed)

@Composable
fun BoxScope.TopActionOverlay(
    showMenu: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    onOpenEqualizer: () -> Unit
) {
    val playerViewModel = LocalPlayerViewModel.current
    val speed by playerViewModel.playbackSpeed.collectAsState(1f)
    var showSpeedMenu by remember { mutableStateOf(false) }
    val speeds = listOf(0.5f, 1.0f, 1.5f, 2.0f)

    Row(
        modifier = Modifier.align(Alignment.TopEnd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box {
            Surface(
                onClick = { showSpeedMenu = true },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Rounded.Speed, null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        formatSpeed(speed),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            DropdownMenu(
                expanded = showSpeedMenu,
                onDismissRequest = { showSpeedMenu = false },
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
            ) {
                speeds.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(formatSpeed(s)) },
                        onClick = { playerViewModel.setPlaybackSpeed(s); showSpeedMenu = false },
                        trailingIcon = {
                            if (abs(s - speed) < 0.01f) {
                                Icon(
                                    Icons.Rounded.Check, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    )
                }
            }
        }

        Box(modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) {
            IconButton(
                onClick = { onMenuToggle(true) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = stringResource(Res.string.more_options),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { onMenuToggle(false) },
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.equalizer_menu)) },
                    onClick = { onMenuToggle(false); onOpenEqualizer() },
                    leadingIcon = { Icon(Icons.Rounded.GraphicEq, null) }
                )
            }
        }
    }
}
