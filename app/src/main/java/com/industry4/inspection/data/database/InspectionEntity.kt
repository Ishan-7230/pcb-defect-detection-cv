package com.industry4.inspection.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.industry4.inspection.data.models.DetectedDefectItem
import com.industry4.inspection.data.models.InspectionRecord
import com.industry4.inspection.data.models.InspectionStatus

@Entity(tableName = "inspections")
data class InspectionEntity(
    @PrimaryKey
    val id: String,
    val timestamp: Long,
    val imagePath: String,
    val annotatedImagePath: String?,
    val status: String,
    val defectCount: Int,
    val defects: List<DetectedDefectItem>,
    val inferenceLatencyMs: Float,
    val endToEndLatencyMs: Float,
    val fps: Float,
    val modelName: String,
    val notes: String? = null
) {
    fun toDomain(): InspectionRecord {
        val inspectionStatus = try {
            InspectionStatus.valueOf(status)
        } catch (_: Exception) {
            InspectionStatus.ERROR
        }
        return InspectionRecord(
            id = id,
            timestamp = timestamp,
            imagePath = imagePath,
            annotatedImagePath = annotatedImagePath,
            status = inspectionStatus,
            defects = defects,
            inferenceLatencyMs = inferenceLatencyMs,
            endToEndLatencyMs = endToEndLatencyMs,
            fps = fps,
            modelName = modelName,
            notes = notes
        )
    }

    companion object {
        fun fromDomain(record: InspectionRecord): InspectionEntity {
            return InspectionEntity(
                id = record.id,
                timestamp = record.timestamp,
                imagePath = record.imagePath,
                annotatedImagePath = record.annotatedImagePath,
                status = record.status.name,
                defectCount = record.defects.size,
                defects = record.defects,
                inferenceLatencyMs = record.inferenceLatencyMs,
                endToEndLatencyMs = record.endToEndLatencyMs,
                fps = record.fps,
                modelName = record.modelName,
                notes = record.notes
            )
        }
    }
}
