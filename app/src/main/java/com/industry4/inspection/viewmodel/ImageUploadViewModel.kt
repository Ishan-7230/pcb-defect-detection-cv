package com.industry4.inspection.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.industry4.inspection.ai.Detection
import com.industry4.inspection.ai.ModelManager
import com.industry4.inspection.data.models.DetectedDefectItem
import com.industry4.inspection.data.models.InferenceMetrics
import com.industry4.inspection.data.models.InspectionRecord
import com.industry4.inspection.data.models.InspectionStatus
import com.industry4.inspection.data.repository.InspectionRepository
import com.industry4.inspection.utils.BitmapUtils
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImageUploadViewModel(
    private val modelManager: ModelManager,
    private val repository: InspectionRepository
) : ViewModel() {

    private val _selectedBitmap = MutableStateFlow<Bitmap?>(null)
    val selectedBitmap: StateFlow<Bitmap?> = _selectedBitmap.asStateFlow()

    private val _annotatedBitmap = MutableStateFlow<Bitmap?>(null)
    val annotatedBitmap: StateFlow<Bitmap?> = _annotatedBitmap.asStateFlow()

    private val _detections = MutableStateFlow<List<Detection>>(emptyList())
    val detections: StateFlow<List<Detection>> = _detections.asStateFlow()

    private val _metrics = MutableStateFlow(InferenceMetrics())
    val metrics: StateFlow<InferenceMetrics> = _metrics.asStateFlow()

    private val _status = MutableStateFlow<InspectionStatus?>(null)
    val status: StateFlow<InspectionStatus?> = _status.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _saveStatus = MutableStateFlow<String?>(null)
    val saveStatus: StateFlow<String?> = _saveStatus.asStateFlow()

    fun processImageUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isAnalyzing.value = true
            _saveStatus.value = null
            _detections.value = emptyList()
            _annotatedBitmap.value = null

            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap == null) {
                    _status.value = InspectionStatus.ERROR
                    _isAnalyzing.value = false
                    return@launch
                }

                _selectedBitmap.value = bitmap

                val totalStart = SystemClock.elapsedRealtimeNanos()
                val prepStart = SystemClock.elapsedRealtimeNanos()
                val letterboxInfo = modelManager.preprocessor.preprocess(bitmap)
                val prepEnd = SystemClock.elapsedRealtimeNanos()
                val prepMs = (prepEnd - prepStart) / 1_000_000.0f

                val metadata = modelManager.metadata.value
                val inputName = metadata?.inputName ?: "images"
                val inputShape = metadata?.inputShape ?: longArrayOf(1, 3, 640, 640)

                val infResult = modelManager.inferenceEngine.runInference(
                    inputTensorName = inputName,
                    inputBuffer = modelManager.preprocessor.floatBuffer,
                    inputShape = inputShape
                )

                if (infResult.isFailure) {
                    _status.value = InspectionStatus.ERROR
                    _isAnalyzing.value = false
                    return@launch
                }

                val raw = infResult.getOrThrow()
                val postStart = SystemClock.elapsedRealtimeNanos()
                val detectedList = modelManager.postProcessor.process(
                    outputBuffer = raw.outputBuffer,
                    shape = raw.outputShape,
                    letterboxInfo = letterboxInfo,
                    classLabels = modelManager.classLabels.value
                )
                val postEnd = SystemClock.elapsedRealtimeNanos()
                val postMs = (postEnd - postStart) / 1_000_000.0f

                val totalEnd = SystemClock.elapsedRealtimeNanos()
                val totalMs = (totalEnd - totalStart) / 1_000_000.0f

                val annotated = BitmapUtils.drawDetectionsOnBitmap(bitmap, detectedList)

                _detections.value = detectedList
                _annotatedBitmap.value = annotated
                _status.value = if (detectedList.isNotEmpty()) {
                    InspectionStatus.DEFECT_DETECTED
                } else {
                    InspectionStatus.NO_TRAINED_DEFECT_DETECTED
                }

                _metrics.value = InferenceMetrics(
                    preprocessLatencyMs = prepMs,
                    modelInferenceLatencyMs = raw.inferenceLatencyMs,
                    postprocessLatencyMs = postMs,
                    endToEndLatencyMs = totalMs,
                    activeProvider = modelManager.inferenceEngine.activeProvider.name
                )
            } catch (e: Exception) {
                _status.value = InspectionStatus.ERROR
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun saveInspection() {
        val orig = _selectedBitmap.value ?: return
        val annot = _annotatedBitmap.value
        val det = _detections.value
        val st = _status.value ?: InspectionStatus.NO_TRAINED_DEFECT_DETECTED

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val record = InspectionRecord(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    imagePath = "",
                    annotatedImagePath = null,
                    status = st,
                    defects = det.map {
                        DetectedDefectItem(it.classId, it.label, it.confidence, it.boundingBox)
                    },
                    inferenceLatencyMs = _metrics.value.modelInferenceLatencyMs,
                    endToEndLatencyMs = _metrics.value.endToEndLatencyMs,
                    fps = 0f,
                    modelName = modelManager.metadata.value?.name ?: "YOLO11n"
                )

                repository.saveInspection(record, orig, annot)
                _saveStatus.value = "Inspection successfully recorded!"
            } catch (e: Exception) {
                _saveStatus.value = "Failed to save: ${e.localizedMessage}"
            }
        }
    }

    fun reset() {
        _selectedBitmap.value = null
        _annotatedBitmap.value = null
        _detections.value = emptyList()
        _status.value = null
        _saveStatus.value = null
    }

    companion object {
        fun provideFactory(
            modelManager: ModelManager,
            repository: InspectionRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ImageUploadViewModel(modelManager, repository) as T
            }
        }
    }
}
