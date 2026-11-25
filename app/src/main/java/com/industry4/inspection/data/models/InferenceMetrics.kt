package com.industry4.inspection.data.models

/**
 * Performance measurement breakdown for an inspection cycle.
 */
data class InferenceMetrics(
    val preprocessLatencyMs: Float = 0.0f,
    val modelInferenceLatencyMs: Float = 0.0f,
    val postprocessLatencyMs: Float = 0.0f,
    val endToEndLatencyMs: Float = 0.0f,
    val currentFps: Float = 0.0f,
    val activeProvider: String = "CPU",
    val memoryUsageMb: Long = 0L
)
