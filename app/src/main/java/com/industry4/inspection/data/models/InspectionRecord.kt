package com.industry4.inspection.data.models

import android.graphics.RectF

data class DetectedDefectItem(
    val classId: Int,
    val label: String,
    val confidence: Float,
    val boundingBox: RectF
)

/**
 * Domain model representing a completed inspection record.
 */
data class InspectionRecord(
    val id: String,
    val timestamp: Long,
    val imagePath: String,
    val annotatedImagePath: String?,
    val status: InspectionStatus,
    val defects: List<DetectedDefectItem>,
    val inferenceLatencyMs: Float,
    val endToEndLatencyMs: Float,
    val fps: Float,
    val modelName: String,
    val notes: String? = null
)
