package com.industry4.inspection.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.industry4.inspection.ai.ExecutionProvider
import com.industry4.inspection.ai.ModelManager
import com.industry4.inspection.ai.ModelMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    val modelManager: ModelManager
) : ViewModel() {

    val modelMetadata: StateFlow<ModelMetadata?> = modelManager.metadata
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val classLabels: StateFlow<List<String>> = modelManager.classLabels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _confidenceThreshold = MutableStateFlow(modelManager.postProcessor.confidenceThreshold)
    val confidenceThreshold: StateFlow<Float> = _confidenceThreshold.asStateFlow()

    private val _iouThreshold = MutableStateFlow(modelManager.postProcessor.iouThreshold)
    val iouThreshold: StateFlow<Float> = _iouThreshold.asStateFlow()

    private val _throttleIntervalMs = MutableStateFlow(100L)
    val throttleIntervalMs: StateFlow<Long> = _throttleIntervalMs.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun updateConfidenceThreshold(value: Float) {
        _confidenceThreshold.value = value
        modelManager.setConfidenceThreshold(value)
    }

    fun updateIouThreshold(value: Float) {
        _iouThreshold.value = value
        modelManager.setIouThreshold(value)
    }

    fun updateThrottleInterval(value: Long) {
        _throttleIntervalMs.value = value
    }

    fun setExecutionProvider(provider: ExecutionProvider) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = modelManager.setExecutionProvider(provider)
            result.fold(
                onSuccess = { meta ->
                    _statusMessage.value = "Engine updated: ${meta.executionProvider.name}"
                },
                onFailure = { error ->
                    _statusMessage.value = "Engine switch failed, reverted: ${error.localizedMessage}"
                }
            )
            _isLoading.value = false
        }
    }

    fun importCustomModel(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = modelManager.importCustomModel(uri, fileName)
            result.fold(
                onSuccess = { meta ->
                    _statusMessage.value = "Imported '${meta.name}' successfully! ${meta.classCount} classes detected."
                },
                onFailure = { error ->
                    _statusMessage.value = "Failed to load custom model: ${error.localizedMessage}"
                }
            )
            _isLoading.value = false
        }
    }

    fun importCustomLabels(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = modelManager.importCustomLabels(uri)
            result.fold(
                onSuccess = { labels ->
                    _statusMessage.value = "Loaded ${labels.size} custom class labels."
                },
                onFailure = { error ->
                    _statusMessage.value = "Failed to load labels: ${error.localizedMessage}"
                }
            )
            _isLoading.value = false
        }
    }

    fun resetToDefault() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = modelManager.resetToDefaultModel()
            result.fold(
                onSuccess = {
                    _statusMessage.value = "Reset to default bundled YOLO11n defect model."
                },
                onFailure = { error ->
                    _statusMessage.value = "Failed to reset: ${error.localizedMessage}"
                }
            )
            _isLoading.value = false
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    companion object {
        fun provideFactory(
            modelManager: ModelManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(modelManager) as T
            }
        }
    }
}
