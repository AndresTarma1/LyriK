package com.example.melodist.bootstrap

import com.example.melodist.data.repository.JvmConfig
import com.example.melodist.data.repository.JvmConfigRepository
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
        if (!customRenderApiRequested) {
            System.setProperty("skiko.renderApi", config.renderApi.name)
        }
    }
}

