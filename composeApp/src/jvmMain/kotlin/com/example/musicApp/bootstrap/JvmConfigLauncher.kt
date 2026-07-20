package com.example.musicApp.bootstrap

import com.example.musicApp.data.repository.JvmConfig
import com.example.musicApp.data.repository.JvmConfigRepository
import com.example.musicApp.data.repository.RenderApi
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

        // DIRECTX significa "dejar que skiko elija automáticamente la plataforma por defecto" — Direct3D en Windows,
        // OpenGL en Linux, Metal en macOS. Fijar "skiko.renderApi=DIRECTX" sería un valor inválido fuera de
        // Windows (skiko no tiene dicha API), así que solo se establece la propiedad para una elección
        // no por defecto explícita; de lo contrario se deja sin establecer para que skiko elija correctamente por SO.
        if (config.renderApi != RenderApi.DIRECTX) {
            System.setProperty("skiko.renderApi", config.renderApi.name)
        }
    }
}

