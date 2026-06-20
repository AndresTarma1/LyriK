package com.example.melodist.ui.components.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Hero/shared-element link between the MiniPlayer's thumbnail and the NowPlayingScreen's cover
 * art. Both sides apply this with the same [songId] so [SharedTransitionLayout] animates the
 * image's position/size between the two instead of cross-fading them.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.heroCoverElement(
    songId: String,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
): Modifier {
    if (sharedTransitionScope == null || animatedVisibilityScope == null) return this
    return with(sharedTransitionScope) {
        this@heroCoverElement.sharedElement(
            sharedContentState = rememberSharedContentState(key = "now_playing_cover_$songId"),
            animatedVisibilityScope = animatedVisibilityScope,
        )
    }
}
