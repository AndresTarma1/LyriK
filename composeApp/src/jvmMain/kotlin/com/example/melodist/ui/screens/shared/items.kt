package com.example.melodist.ui.screens.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem


@Composable
fun SongItem(
    song: SongItem,
    modifier: Modifier = Modifier,
    onClickSubtitle: (String) -> Unit,
    onClick: (YTItem) -> Unit
){

}