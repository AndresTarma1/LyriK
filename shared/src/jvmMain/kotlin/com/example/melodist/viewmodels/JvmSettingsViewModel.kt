package com.example.melodist.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.melodist.data.repository.JvmConfig
import com.example.melodist.data.repository.JvmConfigRepository
import com.example.melodist.data.repository.JvmRuntimeInfo
import com.example.melodist.data.repository.JvmValidationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JvmSettingsUiState(
    val xmx: String = "512m",
    val xms: String = "256m",
    val useG1GC: Boolean = true,
    val useZGC: Boolean = false,
    val gcLogging: Boolean = false,
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

    fun applyAndRestart() {
        val config = JvmConfig(
            xmx = _uiState.value.xmx,
            xms = _uiState.value.xms,
            useG1GC = _uiState.value.useG1GC,
            useZGC = _uiState.value.useZGC,
            gcLogging = _uiState.value.gcLogging,
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

    fun resetToDefaults() {
        _uiState.update {
            it.copy(
                xmx = "512m",
                xms = "256m",
                useG1GC = true,
                useZGC = false,
                gcLogging = false,
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
