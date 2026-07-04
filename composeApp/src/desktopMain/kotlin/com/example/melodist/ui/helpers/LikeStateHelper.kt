package com.example.melodist.ui.helpers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.melodist.viewmodels.PlayerViewModel

@Composable
fun rememberSongLikedState(songId: String, playerViewModel: PlayerViewModel): Boolean {
    val likedIds by playerViewModel.likedSongIds.collectAsState()
    return songId in likedIds
}
