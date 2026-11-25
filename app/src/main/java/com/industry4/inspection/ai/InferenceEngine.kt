package com.industry4.inspection.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.os.SystemClock
import java.io.File
import java.nio.FloatBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result of an ONNX model inference step.
 */
data class RawInferenceResult(
    val outputBuffer: FloatBuffer,
    val outputShape: LongArray,
    val inferenceLatencyMs: Float
)

/**
 * Low-level ONNX Runtime wrapper handling session lifecycle, hardware acceleration (CPU/NNAPI),
 * and tensor inference.
 */
class InferenceEngine {

    private val ortEnvironment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var ortSession: OrtSession? = null
    var activeProvider: ExecutionProvider = ExecutionProvider.CPU
        private set

    /**
     * Initializes or updates the ONNX session with model bytes or file.
     *
     * @param modelBytes Raw ONNX model binary
     * @param preferredProvider User's preferred provider (CPU or NNAPI)
     * @return Result containing metadata or error
     */
    @Synchronized
    fun loadModel(
        modelBytes: ByteArray,
        preferredProvider: ExecutionProvider = ExecutionProvider.CPU
    ): Result<OrtSession> {
        close()
        return try {
            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceAtLeast(2))
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }

            if (preferredProvider == ExecutionProvider.NNAPI) {
                try {
                    sessionOptions.addNnapi()
                    activeProvider = ExecutionProvider.NNAPI
                } catch (e: Exception) {
                    // Fallback to CPU if NNAPI is not supported on this device/chipset
                    activeProvider = ExecutionProvider.CPU
                }
            } else {
                activeProvider = ExecutionProvider.CPU
            }

            val session = ortEnvironment.createSession(modelBytes, sessionOptions)
            ortSession = session
            Result.success(session)
        } catch (e: Exception) {
            // If creation failed with NNAPI, retry strictly on CPU
            if (preferredProvider == ExecutionProvider.NNAPI) {
                try {
                    val fallbackOptions = OrtSession.SessionOptions().apply {
                        setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceAtLeast(2))
                        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    }
                    val session = ortEnvironment.createSession(modelBytes, fallbackOptions)
                    ortSession = session
                    activeProvider = ExecutionProvider.CPU
                    Result.success(session)
                } catch (fallbackError: Exception) {
                    Result.failure(fallbackError)
                }
            } else {
                Result.failure(e)
            }
        }
    }

    /**
     * Runs inference on the preprocessed float tensor.
     *
     * @param inputTensorName Model's expected input name (e.g. "images")
     * @param inputBuffer Planar RGB FloatBuffer [1, 3, 640, 640]
     * @param inputShape Input tensor shape [1, 3, 640, 640]
     */
    suspend fun runInference(
        inputTensorName: String,
        inputBuffer: FloatBuffer,
        inputShape: LongArray
    ): Result<RawInferenceResult> = withContext(Dispatchers.Default) {
        val session = ortSession ?: return@withContext Result.failure(IllegalStateException("ONNX Session not initialized"))

        try {
            inputBuffer.rewind()
            val tensor = OnnxTensor.createTensor(ortEnvironment, inputBuffer, inputShape)

            val startTime = SystemClock.elapsedRealtimeNanos()
            val outputs = session.run(mapOf(inputTensorName to tensor))
            val endTime = SystemClock.elapsedRealtimeNanos()

            val latencyMs = (endTime - startTime) / 1_000_000.0f

            val outputEntry = outputs.iterator().next()
            val outputTensor = outputEntry.value as OnnxTensor
            val outputShape = outputTensor.info.shape
            val outputFloatBuffer = outputTensor.floatBuffer

            tensor.close()

            Result.success(
                RawInferenceResult(
                    outputBuffer = outputFloatBuffer,
                    outputShape = outputShape,
                    inferenceLatencyMs = latencyMs
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isLoaded(): Boolean = ortSession != null

    @Synchronized
    fun close() {
        try {
            ortSession?.close()
        } catch (_: Exception) {}
        ortSession = null
    }
}
