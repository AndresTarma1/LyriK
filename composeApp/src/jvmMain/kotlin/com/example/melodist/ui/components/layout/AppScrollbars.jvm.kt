package com.example.melodist.ui.components.layout

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun appScrollbarStyle() = LocalScrollbarStyle.current.copy(
    thickness = 5.dp,
    unhoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f),
    hoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
    shape = RoundedCornerShape(3.dp)
)

@Composable
fun AppVerticalScrollbar(
    state: ScrollState,
    modifier: Modifier = Modifier,
) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(state),
        modifier = modifier
            .width(12.dp)
            .padding(end = 2.dp, top = 4.dp, bottom = 4.dp),
        style = appScrollbarStyle()
    )
}

@Composable
fun AppVerticalScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(state),
        modifier = modifier.padding(vertical = 20.dp).width(12.dp),
        style = appScrollbarStyle()
    )
}

@Composable
fun AppVerticalScrollbar(
    state: LazyGridState,
    modifier: Modifier = Modifier,
) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(state),
        modifier = modifier.padding(vertical = 20.dp),
        style = appScrollbarStyle()
    )
}
