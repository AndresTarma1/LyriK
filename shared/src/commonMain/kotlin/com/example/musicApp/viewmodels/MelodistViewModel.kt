package com.example.musicApp.viewmodels

import androidx.lifecycle.ViewModel

open class MelodistViewModel : ViewModel() {
    private var disposed = false

    fun dispose() {
        if (disposed) return
        disposed = true
        onCleared()
    }
}
