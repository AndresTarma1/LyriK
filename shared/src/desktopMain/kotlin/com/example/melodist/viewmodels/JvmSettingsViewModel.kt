package com.example.melodist.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.melodist.data.repository.JvmConfig
import com.example.melodist.data.repository.JvmConfigRepository
import com.example.melodist.data.repository.JvmRuntimeInfo
import com.example.melodist.data.repository.JvmValidationResult
import com.example.melodist.data.repository.RenderApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JvmSettingsUiState(
    val xmx: String = "512m",
    val xms: String = "64m",
    val useG1GC: Boolean = true,
    val useZGC: Boolean = false,
    val gcLogging: Boolean = false,
    val renderApi: RenderApi = RenderApi.DIRECTX,
    val validationError: String? = null,
    val isApplying: Boolean = false,
)

class JvmSettingsViewModel(
    private val jvmConfigRepository: JvmConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(JvmSettingsUiState())
    val uiState: StateFlow<JvmSettingsUiState> = _uiState.asStateFlow()

    val runtimeInfo: StateFlow<JvmRuntimeInfo> = MutableStateFlow(JvmRuntimeInfo.current())
        .stateIn(viewModelScope, SharingStarted.Lazily, JvmRuntimeInfo.current())

    init {
        viewModelScope.launch {
            jvmConfigRepository.config.collect { config ->
                _uiState.update {
                    it.copy(
                        xmx = config.xmx,
                        xms = config.xms,
                        useG1GC = config.useG1GC,
                        useZGC = config.useZGC,
                        gcLogging = config.gcLogging,
                        renderApi = config.renderApi,
                        validationError = null,
                    )
                }
            }
        }
    }

    fun setXmx(value: String) {
        _uiState.update { it.copy(xmx = value, validationError = null) }
    }

    fun setXms(value: String) {
        _uiState.update { it.copy(xms = value, validationError = null) }
    }

    fun setG1GC(enabled: Boolean) {
        _uiState.update {
            it.copy(
                useG1GC = enabled,
                useZGC = if (enabled) false else it.useZGC,
                validationError = null,
            )
        }
    }

    fun setZGC(enabled: Boolean) {
        _uiState.update {
            it.copy(
                useZGC = enabled,
                useG1GC = if (enabled) false else it.useG1GC,
                validationError = null,
            )
        }
    }

    fun setGcLogging(enabled: Boolean) {
        _uiState.update { it.copy(gcLogging = enabled, validationError = null) }
    }

    fun setRenderApi(renderApi: RenderApi) {
        _uiState.update { it.copy(renderApi = renderApi, validationError = null) }
    }

    fun applyAndRestart() {
        val config = JvmConfig(
            xmx = _uiState.value.xmx,
            xms = _uiState.value.xms,
            useG1GC = _uiState.value.useG1GC,
            useZGC = _uiState.value.useZGC,
            gcLogging = _uiState.value.gcLogging,
            renderApi = _uiState.value.renderApi,
        )

        val result = config.validate()
        if (result !is JvmValidationResult.Valid) {
            _uiState.update { it.copy(validationError = result.errorMessage) }
            return
        }

        _uiState.update { it.copy(isApplying = true) }
        viewModelScope.launch {
            jvmConfigRepository.updateConfig(config)
        }
    }

    suspend fun saveCurrentConfig(): Boolean {
        val config = JvmConfig(
            xmx = _uiState.value.xmx,
            xms = _uiState.value.xms,
            useG1GC = _uiState.value.useG1GC,
            useZGC = _uiState.value.useZGC,
            gcLogging = _uiState.value.gcLogging,
            renderApi = _uiState.value.renderApi,
        )

        val result = config.validate()
        if (result !is JvmValidationResult.Valid) {
            _uiState.update { it.copy(validationError = result.errorMessage) }
            return false
        }

        jvmConfigRepository.updateConfig(config)
        return true
    }

    suspend fun saveRenderApi(): Boolean {
        jvmConfigRepository.updateRenderApi(_uiState.value.renderApi)
        return true
    }

    fun resetToDefaults() {
        _uiState.update {
            it.copy(
                xmx = "512m",
                xms = "64m",
                useG1GC = true,
                useZGC = false,
                gcLogging = false,
                renderApi = RenderApi.DIRECTX,
                validationError = null,
            )
        }
        viewModelScope.launch {
            jvmConfigRepository.resetToDefaults()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(validationError = null) }
    }
}
