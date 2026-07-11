package com.example.melodist.bootstrap

import com.example.melodist.data.repository.JvmConfig
import com.example.melodist.data.repository.JvmConfigRepository
import com.example.melodist.data.repository.RenderApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class JvmConfigLauncher(
    private val repository: JvmConfigRepository,
) {

    fun applySync() {
        runBlocking(Dispatchers.IO) {
            applyConfig(repository.config.first())
        }
    }

    private fun applyConfig(config: JvmConfig) {
        val customRenderApiRequested =
            System.getenv("SKIKO_RENDER_API") != null ||
                System.getProperty("skiko.renderApi") != null
        if (customRenderApiRequested) return

        // DIRECTX means "let skiko auto-pick the platform default" — Direct3D on Windows, OpenGL on
        // Linux, Metal on macOS. Pinning "skiko.renderApi=DIRECTX" would be an invalid value off
        // Windows (skiko has no such API), so only set the property for an explicit non-default
        // choice; otherwise leave it unset so skiko chooses correctly per-OS.
        if (config.renderApi != RenderApi.DIRECTX) {
            System.setProperty("skiko.renderApi", config.renderApi.name)
        }
    }
}

