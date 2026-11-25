package com.industry4.inspection.camera

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.industry4.inspection.ai.Detection
import com.industry4.inspection.ai.ModelManager
import com.industry4.inspection.data.models.InferenceMetrics
import com.industry4.inspection.data.models.InspectionStatus
import com.industry4.inspection.utils.BitmapUtils
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Inspection frame outcome containing detections, latencies, and FPS.
 */
data class FrameInspectionResult(
    val detections: List<Detection>,
    val metrics: InferenceMetrics,
    val status: InspectionStatus,
    val frameWidth: Int,
    val frameHeight: Int
)

/**
 * CameraX ImageAnalysis.Analyzer that throttles frames, runs ONNX inference,
 * and emits real-time defect detections.
 */
class ImageAnalyzer(
    private val modelManager: ModelManager,
    private val scope: CoroutineScope,
    var throttleIntervalMs: Long = 100L,
    private val onResult: (FrameInspectionResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val isProcessing = AtomicBoolean(false)
    private var lastFrameTime = 0L

    // FPS calculation window
    private var frameCount = 0
    private var fpsStartTime = SystemClock.elapsedRealtime()
    private var currentFps = 0.0f

    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = SystemClock.elapsedRealtime()

        // Throttle frames to prevent CPU overheating and battery drain
        if (currentTime - lastFrameTime < throttleIntervalMs || !isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        lastFrameTime = currentTime

        // Update FPS calculation
        frameCount++
        val fpsElapsed = currentTime - fpsStartTime
        if (fpsElapsed >= 1000L) {
            currentFps = (frameCount * 1000f) / fpsElapsed
            frameCount = 0
            fpsStartTime = currentTime
        }

        scope.launch(Dispatchers.Default) {
            val totalStart = SystemClock.elapsedRealtimeNanos()
            try {
                if (!modelManager.isModelReady.value) {
                    return@launch
                }

                val metadata = modelManager.metadata.value ?: return@launch

                // 1. Convert ImageProxy to Bitmap
                val prepStart = SystemClock.elapsedRealtimeNanos()
                val bitmap = BitmapUtils.imageProxyToBitmap(imageProxy)
                if (bitmap == null) {
                    return@launch
                }

                // 2. Preprocess to 640x640 planar FloatBuffer
                val letterboxInfo = modelManager.preprocessor.preprocess(bitmap)
                val prepEnd = SystemClock.elapsedRealtimeNanos()
                val prepMs = (prepEnd - prepStart) / 1_000_000.0f

                // 3. Run ONNX Inference
                val infResult = modelManager.inferenceEngine.runInference(
                    inputTensorName = metadata.inputName,
                    inputBuffer = modelManager.preprocessor.floatBuffer,
                    inputShape = metadata.inputShape
                )

                if (infResult.isFailure) {
                    onResult(
                        FrameInspectionResult(
                            detections = emptyList(),
                            metrics = InferenceMetrics(
                                preprocessLatencyMs = prepMs,
                                modelInferenceLatencyMs = 0f,
                                postprocessLatencyMs = 0f,
                                endToEndLatencyMs = prepMs,
                                currentFps = currentFps,
                                activeProvider = modelManager.inferenceEngine.activeProvider.name
                            ),
                            status = InspectionStatus.ERROR,
                            frameWidth = bitmap.width,
                            frameHeight = bitmap.height
                        )
                    )
                    return@launch
                }

                val raw = infResult.getOrThrow()

                // 4. Postprocess YOLO tensor
                val postStart = SystemClock.elapsedRealtimeNanos()
                val detections = modelManager.postProcessor.process(
                    outputBuffer = raw.outputBuffer,
                    shape = raw.outputShape,
                    letterboxInfo = letterboxInfo,
                    classLabels = modelManager.classLabels.value
                )
                val postEnd = SystemClock.elapsedRealtimeNanos()
                val postMs = (postEnd - postStart) / 1_000_000.0f

                val totalEnd = SystemClock.elapsedRealtimeNanos()
                val totalMs = (totalEnd - totalStart) / 1_000_000.0f

                // Determine inspection status
                val status = if (detections.isNotEmpty()) {
                    InspectionStatus.DEFECT_DETECTED
                } else {
                    InspectionStatus.NO_TRAINED_DEFECT_DETECTED
                }

                val runtime = Runtime.getRuntime()
                val memoryUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)

                val metrics = InferenceMetrics(
                    preprocessLatencyMs = prepMs,
                    modelInferenceLatencyMs = raw.inferenceLatencyMs,
                    postprocessLatencyMs = postMs,
                    endToEndLatencyMs = totalMs,
                    currentFps = currentFps,
                    activeProvider = modelManager.inferenceEngine.activeProvider.name,
                    memoryUsageMb = memoryUsedMb
                )

                onResult(
                    FrameInspectionResult(
                        detections = detections,
                        metrics = metrics,
                        status = status,
                        frameWidth = bitmap.width,
                        frameHeight = bitmap.height
                    )
                )

                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                // Ignore transient frame analysis errors
            } finally {
                imageProxy.close()
                isProcessing.set(false)
            }
        }
    }
}
