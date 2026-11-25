package com.industry4.inspection.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.industry4.inspection.ai.Detection
import com.industry4.inspection.ai.ModelManager
import com.industry4.inspection.camera.FrameInspectionResult
import com.industry4.inspection.data.models.DetectedDefectItem
import com.industry4.inspection.data.models.InferenceMetrics
import com.industry4.inspection.data.models.InspectionRecord
import com.industry4.inspection.data.models.InspectionStatus
import com.industry4.inspection.data.repository.InspectionRepository
import com.industry4.inspection.utils.BitmapUtils
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InspectionViewModel(
    val modelManager: ModelManager,
    private val repository: InspectionRepository
) : ViewModel() {

    private val _isInspecting = MutableStateFlow(true)
    val isInspecting: StateFlow<Boolean> = _isInspecting.asStateFlow()

    private val _currentDetections = MutableStateFlow<List<Detection>>(emptyList())
    val currentDetections: StateFlow<List<Detection>> = _currentDetections.asStateFlow()

    private val _latestMetrics = MutableStateFlow(InferenceMetrics())
    val latestMetrics: StateFlow<InferenceMetrics> = _latestMetrics.asStateFlow()

    private val _latestStatus = MutableStateFlow(InspectionStatus.NO_TRAINED_DEFECT_DETECTED)
    val latestStatus: StateFlow<InspectionStatus> = _latestStatus.asStateFlow()

    private val _isSavingSnapshot = MutableStateFlow(false)
    val isSavingSnapshot: StateFlow<Boolean> = _isSavingSnapshot.asStateFlow()

    private val _saveSuccessMessage = MutableStateFlow<String?>(null)
    val saveSuccessMessage: StateFlow<String?> = _saveSuccessMessage.asStateFlow()

    fun toggleInspection() {
        _isInspecting.value = !_isInspecting.value
        if (!_isInspecting.value) {
            _currentDetections.value = emptyList()
        }
    }

    fun onFrameAnalyzed(result: FrameInspectionResult) {
        if (!_isInspecting.value) return
        _currentDetections.value = result.detections
        _latestMetrics.value = result.metrics
        _latestStatus.value = result.status
    }

    /**
     * Saves the current inspection frame and annotations to the Room database.
     */
    fun saveSnapshot(frameBitmap: Bitmap?) {
        if (frameBitmap == null) return
        viewModelScope.launch(Dispatchers.IO) {
            _isSavingSnapshot.value = true
            try {
                val detections = _currentDetections.value
                val annotatedBitmap = BitmapUtils.drawDetectionsOnBitmap(frameBitmap, detections)
                val id = UUID.randomUUID().toString()

                val defectItems = detections.map { d ->
                    DetectedDefectItem(
                        classId = d.classId,
                        label = d.label,
                        confidence = d.confidence,
                        boundingBox = d.boundingBox
                    )
                }

                val record = InspectionRecord(
                    id = id,
                    timestamp = System.currentTimeMillis(),
                    imagePath = "",
                    annotatedImagePath = null,
                    status = _latestStatus.value,
                    defects = defectItems,
                    inferenceLatencyMs = _latestMetrics.value.modelInferenceLatencyMs,
                    endToEndLatencyMs = _latestMetrics.value.endToEndLatencyMs,
                    fps = _latestMetrics.value.currentFps,
                    modelName = modelManager.metadata.value?.name ?: "YOLO11n"
                )

                repository.saveInspection(record, frameBitmap, annotatedBitmap)
                _saveSuccessMessage.value = "Inspection snapshot saved: ${defectItems.size} defect(s)"
            } catch (e: Exception) {
                _saveSuccessMessage.value = "Failed to save: ${e.localizedMessage}"
            } finally {
                _isSavingSnapshot.value = false
            }
        }
    }

    fun clearSaveMessage() {
        _saveSuccessMessage.value = null
    }

    companion object {
        fun provideFactory(
            modelManager: ModelManager,
            repository: InspectionRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return InspectionViewModel(modelManager, repository) as T
            }
        }
    }
}
