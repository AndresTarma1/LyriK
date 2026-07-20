package com.example.musicApp.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the visibility of the always-on-top music overlay. The global hotkey toggles it from a
 * native thread; the App composable observes [visible] to show/hide the overlay Window.
 */
object OverlayController {
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    fun toggle() { _visible.value = !_visible.value }
    fun show() { _visible.value = true }
    fun hide() { _visible.value = false }
}
