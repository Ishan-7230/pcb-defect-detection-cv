package com.industry4.inspection.ai

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Manages the lifecycle, import, switching, and inspection of defect detection models and labels.
 */
class ModelManager(
    private val context: Context,
    val inferenceEngine: InferenceEngine = InferenceEngine(),
    val preprocessor: ImagePreprocessor = ImagePreprocessor(),
    val postProcessor: YoloPostProcessor = YoloPostProcessor()
) {

    private val _metadata = MutableStateFlow<ModelMetadata?>(null)
    val metadata: StateFlow<ModelMetadata?> = _metadata.asStateFlow()

    private val _classLabels = MutableStateFlow<List<String>>(emptyList())
    val classLabels: StateFlow<List<String>> = _classLabels.asStateFlow()

    private val _isModelReady = MutableStateFlow(false)
    val isModelReady: StateFlow<Boolean> = _isModelReady.asStateFlow()

    private val prefs = context.getSharedPreferences("industry4_model_prefs", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_MODEL_ASSET = "models/yolo_detector.onnx"
        const val DEFAULT_LABELS_ASSET = "models/labels.txt"
        private const val PREF_KEY_CUSTOM_MODEL_PATH = "custom_model_path"
        private const val PREF_KEY_CUSTOM_LABELS_PATH = "custom_labels_path"
        private const val PREF_KEY_PROVIDER = "execution_provider"
        private const val PREF_KEY_CONF_THRESHOLD = "conf_threshold"
        private const val PREF_KEY_IOU_THRESHOLD = "iou_threshold"
    }

    init {
        postProcessor.confidenceThreshold = prefs.getFloat(PREF_KEY_CONF_THRESHOLD, 0.25f)
        postProcessor.iouThreshold = prefs.getFloat(PREF_KEY_IOU_THRESHOLD, 0.45f)
    }

    /**
     * Loads the model on startup: restores custom model if present, otherwise loads bundled asset model.
     */
    suspend fun initialize(): Result<ModelMetadata> = withContext(Dispatchers.IO) {
        // Load labels first
        loadLabels()

        val savedCustomModelPath = prefs.getString(PREF_KEY_CUSTOM_MODEL_PATH, null)
        val savedProviderStr = prefs.getString(PREF_KEY_PROVIDER, ExecutionProvider.CPU.name)
        val provider = try {
            ExecutionProvider.valueOf(savedProviderStr ?: ExecutionProvider.CPU.name)
        } catch (_: Exception) {
            ExecutionProvider.CPU
        }

        if (savedCustomModelPath != null && File(savedCustomModelPath).exists()) {
            val result = loadFromCustomFile(File(savedCustomModelPath), provider)
            if (result.isSuccess) {
                return@withContext result
            }
        }

        // Fallback to bundled asset model
        loadFromAssets(DEFAULT_MODEL_ASSET, provider)
    }

    /**
     * Loads bundled default ONNX model from Android assets.
     */
    suspend fun loadFromAssets(
        assetPath: String = DEFAULT_MODEL_ASSET,
        preferredProvider: ExecutionProvider = ExecutionProvider.CPU
    ): Result<ModelMetadata> = withContext(Dispatchers.IO) {
        try {
            val startTime = SystemClock.elapsedRealtime()
            val bytes = context.assets.open(assetPath).use { it.readBytes() }
            val sessionResult = inferenceEngine.loadModel(bytes, preferredProvider)

            sessionResult.fold(
                onSuccess = { session ->
                    val loadDuration = SystemClock.elapsedRealtime() - startTime
                    val meta = inspectSession(
                        session = session,
                        modelName = assetPath.substringAfterLast("/"),
                        source = ModelSource.BUNDLED_ASSET,
                        provider = inferenceEngine.activeProvider,
                        loadDurationMs = loadDuration
                    )
                    _metadata.value = meta
                    _isModelReady.value = meta.isCompatible
                    Result.success(meta)
                },
                onFailure = { error ->
                    _isModelReady.value = false
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            _isModelReady.value = false
            Result.failure(e)
        }
    }

    /**
     * Imports a user-selected ONNX model from SAF URI, persists it to internal storage, and loads it.
     */
    suspend fun importCustomModel(
        uri: Uri,
        fileName: String,
        preferredProvider: ExecutionProvider = ExecutionProvider.CPU
    ): Result<ModelMetadata> = withContext(Dispatchers.IO) {
        try {
            val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
            val destinationFile = File(modelsDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(IllegalStateException("Could not read file from URI"))

            val result = loadFromCustomFile(destinationFile, preferredProvider)
            if (result.isSuccess) {
                prefs.edit().putString(PREF_KEY_CUSTOM_MODEL_PATH, destinationFile.absolutePath).apply()
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Loads ONNX model from internal file storage.
     */
    private suspend fun loadFromCustomFile(
        file: File,
        preferredProvider: ExecutionProvider
    ): Result<ModelMetadata> = withContext(Dispatchers.IO) {
        try {
            val startTime = SystemClock.elapsedRealtime()
            val bytes = file.readBytes()
            val sessionResult = inferenceEngine.loadModel(bytes, preferredProvider)

            sessionResult.fold(
                onSuccess = { session ->
                    val loadDuration = SystemClock.elapsedRealtime() - startTime
                    val meta = inspectSession(
                        session = session,
                        modelName = file.name,
                        source = ModelSource.CUSTOM_IMPORTED,
                        provider = inferenceEngine.activeProvider,
                        loadDurationMs = loadDuration
                    )
                    _metadata.value = meta
                    _isModelReady.value = meta.isCompatible
                    Result.success(meta)
                },
                onFailure = { error ->
                    _isModelReady.value = false
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            _isModelReady.value = false
            Result.failure(e)
        }
    }

    /**
     * Imports custom labels from URI (supporting plain newline .txt or JSON list/dict).
     */
    suspend fun importCustomLabels(uri: Uri): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val content = context.contentResolver.openInputStream(uri)?.use {
                it.bufferedReader().readText()
            } ?: return@withContext Result.failure(IllegalStateException("Cannot open labels URI"))

            val parsedLabels = parseLabelsText(content)
            if (parsedLabels.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("No valid class labels found in file"))
            }

            // Save to internal storage
            val labelsFile = File(context.filesDir, "custom_labels.txt")
            labelsFile.writeText(parsedLabels.joinToString("\n"))
            prefs.edit().putString(PREF_KEY_CUSTOM_LABELS_PATH, labelsFile.absolutePath).apply()

            _classLabels.value = parsedLabels
            Result.success(parsedLabels)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Loads labels: checks custom labels first, then falls back to assets.
     */
    private fun loadLabels() {
        val customLabelsPath = prefs.getString(PREF_KEY_CUSTOM_LABELS_PATH, null)
        if (customLabelsPath != null) {
            val file = File(customLabelsPath)
            if (file.exists()) {
                val labels = parseLabelsText(file.readText())
                if (labels.isNotEmpty()) {
                    _classLabels.value = labels
                    return
                }
            }
        }

        // Bundled labels
        try {
            val assetContent = context.assets.open(DEFAULT_LABELS_ASSET).bufferedReader().readText()
            val labels = parseLabelsText(assetContent)
            _classLabels.value = labels
        } catch (_: Exception) {
            _classLabels.value = listOf("crease", "crescent_gap", "inclusion", "oil_spot", "punching_hole", "rolled_pit", "silk_spot", "waist_folding", "water_spot", "welding_line")
        }
    }

    private fun parseLabelsText(text: String): List<String> {
        val trimmed = text.trim()
        // 1. Check Python/Ultralytics or JSON dictionary format: {0: 'crease', 1: 'crescent_gap', ...}
        val dictPattern = Regex("""(\d+)\s*:\s*['"]([^'"]+)['"]""")
        val matches = dictPattern.findAll(trimmed).toList()
        if (matches.isNotEmpty()) {
            return matches
                .map { it.groupValues[1].toInt() to it.groupValues[2].trim() }
                .sortedBy { it.first }
                .map { it.second }
        }

        // 2. Check JSON array format: ["crease", "inclusion", ...]
        if (trimmed.startsWith("[")) {
            return try {
                val listType = object : TypeToken<List<String>>() {}.type
                Gson().fromJson(trimmed, listType)
            } catch (_: Exception) {
                emptyList()
            }
        }

        // 3. Plain text: one class per line
        return trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * Inspects the ONNX session input/output tensors and checks compatibility for YOLO defect detection.
     */
    private fun inspectSession(
        session: ai.onnxruntime.OrtSession,
        modelName: String,
        source: ModelSource,
        provider: ExecutionProvider,
        loadDurationMs: Long
    ): ModelMetadata {
        val inputNames = session.inputNames.toList()
        val outputNames = session.outputNames.toList()

        val inputName = inputNames.firstOrNull() ?: "images"
        val outputName = outputNames.firstOrNull() ?: "output0"

        val inputInfo = session.inputInfo[inputName]
        val outputInfo = session.outputInfo[outputName]

        val inputShape = (inputInfo?.info as? ai.onnxruntime.TensorInfo)?.shape ?: longArrayOf(1, 3, 640, 640)
        val outputShape = (outputInfo?.info as? ai.onnxruntime.TensorInfo)?.shape ?: longArrayOf(1, 14, 8400)

        // Extract class names directly from ONNX custom metadata if embedded by Ultralytics
        val customLabelsPath = prefs.getString(PREF_KEY_CUSTOM_LABELS_PATH, null)
        if (customLabelsPath == null) {
            try {
                val embeddedNames = session.metadata.customMetadata["names"]
                if (!embeddedNames.isNullOrEmpty()) {
                    val parsed = parseLabelsText(embeddedNames)
                    if (parsed.isNotEmpty()) {
                        _classLabels.value = parsed
                    }
                }
            } catch (_: Exception) {}
        }

        // Determine class count from output shape
        val classCount = when {
            outputShape.size == 3 && outputShape[1] < outputShape[2] && outputShape[1] >= 5 -> {
                (outputShape[1] - 4).toInt() // [1, 4 + C, 8400]
            }
            outputShape.size == 3 && outputShape[1] > outputShape[2] && outputShape[2] >= 5 -> {
                (outputShape[2] - 4).toInt() // [1, 8400, 4 + C]
            }
            outputShape.size == 3 && outputShape[2] == 6L -> {
                _classLabels.value.size // End-to-end [1, N, 6]
            }
            else -> _classLabels.value.size
        }

        // Validate compatibility
        val isInputValid = inputShape.size == 4 && inputShape[1] == 3L
        val isOutputValid = outputShape.isNotEmpty()
        val isCompatible = isInputValid && isOutputValid

        val statusMsg = if (isCompatible) {
            "Model verified: ${classCount} defect classes detected."
        } else {
            "Warning: Tensor layout does not match standard 3-channel input."
        }

        return ModelMetadata(
            name = modelName,
            source = source,
            inputName = inputName,
            inputShape = inputShape,
            outputName = outputName,
            outputShape = outputShape,
            classCount = classCount,
            classNames = _classLabels.value,
            executionProvider = provider,
            loadDurationMs = loadDurationMs,
            isCompatible = isCompatible,
            statusMessage = statusMsg
        )
    }

    /**
     * Resets active model back to bundled default model.
     */
    suspend fun resetToDefaultModel(): Result<ModelMetadata> = withContext(Dispatchers.IO) {
        prefs.edit().remove(PREF_KEY_CUSTOM_MODEL_PATH).remove(PREF_KEY_CUSTOM_LABELS_PATH).apply()
        loadLabels()
        loadFromAssets(DEFAULT_MODEL_ASSET, ExecutionProvider.CPU)
    }

    fun setConfidenceThreshold(threshold: Float) {
        postProcessor.confidenceThreshold = threshold
        prefs.edit().putFloat(PREF_KEY_CONF_THRESHOLD, threshold).apply()
    }

    fun setIouThreshold(threshold: Float) {
        postProcessor.iouThreshold = threshold
        prefs.edit().putFloat(PREF_KEY_IOU_THRESHOLD, threshold).apply()
    }

    suspend fun setExecutionProvider(provider: ExecutionProvider): Result<ModelMetadata> {
        prefs.edit().putString(PREF_KEY_PROVIDER, provider.name).apply()
        return initialize()
    }
}
