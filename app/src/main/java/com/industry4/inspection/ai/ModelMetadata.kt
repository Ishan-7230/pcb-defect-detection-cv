package com.industry4.inspection.ai

enum class ModelSource {
    BUNDLED_ASSET,
    CUSTOM_IMPORTED
}

enum class ExecutionProvider {
    CPU,
    NNAPI
}

/**
 * Metadata and structure information for the loaded ONNX defect detection model.
 */
data class ModelMetadata(
    val name: String,
    val source: ModelSource,
    val inputName: String,
    val inputShape: LongArray,
    val outputName: String,
    val outputShape: LongArray,
    val classCount: Int,
    val classNames: List<String>,
    val executionProvider: ExecutionProvider,
    val loadDurationMs: Long,
    val isCompatible: Boolean,
    val statusMessage: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ModelMetadata
        if (name != other.name) return false
        if (source != other.source) return false
        if (inputName != other.inputName) return false
        if (!inputShape.contentEquals(other.inputShape)) return false
        if (outputName != other.outputName) return false
        if (!outputShape.contentEquals(other.outputShape)) return false
        if (classCount != other.classCount) return false
        if (classNames != other.classNames) return false
        if (executionProvider != other.executionProvider) return false
        if (loadDurationMs != other.loadDurationMs) return false
        if (isCompatible != other.isCompatible) return false
        if (statusMessage != other.statusMessage) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + inputName.hashCode()
        result = 31 * result + inputShape.contentHashCode()
        result = 31 * result + outputName.hashCode()
        result = 31 * result + outputShape.contentHashCode()
        result = 31 * result + classCount
        result = 31 * result + classNames.hashCode()
        result = 31 * result + executionProvider.hashCode()
        result = 31 * result + loadDurationMs.hashCode()
        result = 31 * result + isCompatible.hashCode()
        result = 31 * result + statusMessage.hashCode()
        return result
    }
}
