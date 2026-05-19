package com.example.melodist.ui.utils

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun circleAwareShape(corner: Dp = 12.dp): Shape {
    return if (isOpenGlRenderer()) RoundedCornerShape(corner) else CircleShape
}

fun isCircleLikeShape(shape: Shape, corner: Dp = 12.dp): Boolean {
    return shape == CircleShape || shape == RoundedCornerShape(corner)
}

private fun isOpenGlRenderer(): Boolean {
    return System.getProperty("skiko.renderApi")?.equals("OPENGL", ignoreCase = true) == true
}
