package com.example.melodist.ui.utils

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.melodist.platform.Platform

fun circleAwareShape(corner: Dp = 12.dp): Shape {
    return if (isOpenGlRenderer()) RoundedCornerShape(corner) else CircleShape
}

fun isCircleLikeShape(shape: Shape, corner: Dp = 12.dp): Boolean {
    return shape == CircleShape || shape == RoundedCornerShape(corner)
}

private fun isOpenGlRenderer(): Boolean {
    // When we pin the renderApi, honor it. When unset (DIRECTX/"auto"), skiko's default is OpenGL
    // on Linux — so treat Linux-without-an-explicit-choice as OpenGL too.
    val prop = System.getProperty("skiko.renderApi")
    return if (prop != null) prop.equals("OPENGL", ignoreCase = true) else Platform.isLinux
}
