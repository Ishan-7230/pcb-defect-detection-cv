package com.industry4.inspection.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.industry4.inspection.data.database.AppDatabase
import com.industry4.inspection.data.database.Converters
import com.industry4.inspection.data.database.InspectionEntity
import com.industry4.inspection.data.models.InspectionRecord
import com.industry4.inspection.data.models.InspectionStatus
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Single source of truth for saving, retrieving, deleting, and exporting inspection records and images.
 */
class InspectionRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context)
) {
    private val dao = database.inspectionDao()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    val allInspections: Flow<List<InspectionRecord>> = dao.getAllInspections().map { entities ->
        entities.map { it.toDomain() }
    }

    val recentInspections: Flow<List<InspectionRecord>> = dao.getRecentInspections(5).map { entities ->
        entities.map { it.toDomain() }
    }

    val totalInspectionsCount: Flow<Int> = dao.getTotalInspectionsCount()

    val totalDefectsDetected: Flow<Int> = dao.getTotalDefectsDetected()

    suspend fun getInspectionById(id: String): InspectionRecord? = withContext(Dispatchers.IO) {
        dao.getInspectionById(id)?.toDomain()
    }

    /**
     * Saves original bitmap, annotated bitmap (if present), and records inspection in Room.
     */
    suspend fun saveInspection(
        record: InspectionRecord,
        originalBitmap: Bitmap?,
        annotatedBitmap: Bitmap?
    ): Result<InspectionRecord> = withContext(Dispatchers.IO) {
        try {
            val inspectionsDir = File(context.filesDir, "inspections").apply { mkdirs() }

            var origPath = record.imagePath
            if (originalBitmap != null) {
                val origFile = File(inspectionsDir, "${record.id}_orig.jpg")
                FileOutputStream(origFile).use { out ->
                    originalBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                origPath = origFile.absolutePath
            }

            var annotatedPath = record.annotatedImagePath
            if (annotatedBitmap != null) {
                val annotatedFile = File(inspectionsDir, "${record.id}_annotated.jpg")
                FileOutputStream(annotatedFile).use { out ->
                    annotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                annotatedPath = annotatedFile.absolutePath
            }

            val finalRecord = record.copy(
                imagePath = origPath,
                annotatedImagePath = annotatedPath
            )

            dao.insertInspection(InspectionEntity.fromDomain(finalRecord))
            Result.success(finalRecord)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteInspection(id: String) = withContext(Dispatchers.IO) {
        val entity = dao.getInspectionById(id)
        if (entity != null) {
            // Delete image files
            try {
                File(entity.imagePath).delete()
                entity.annotatedImagePath?.let { File(it).delete() }
            } catch (_: Exception) {}
            dao.deleteById(id)
        }
    }

    suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
        // Clear inspection images directory
        try {
            val dir = File(context.filesDir, "inspections")
            dir.listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {}
        dao.clearAll()
    }

    /**
     * Generates a CSV report file of all inspection records.
     * @return Content URI suitable for sharing via Android Intent
     */
    suspend fun exportHistoryToCsv(): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val csvFile = File(exportsDir, "Inspection_Report_$timeStamp.csv")

            val entities = dao.getAllInspections()
            // Pull first emission from flow synchronously
            var records: List<InspectionRecord> = emptyList()
            // Query database directly
            val cursor = database.query("SELECT * FROM inspections ORDER BY timestamp DESC", null)
            val list = mutableListOf<InspectionRecord>()
            while (cursor.moveToNext()) {
                val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                val ts = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"))
                val img = cursor.getString(cursor.getColumnIndexOrThrow("imagePath"))
                val annot = cursor.getString(cursor.getColumnIndexOrThrow("annotatedImagePath"))
                val statusStr = cursor.getString(cursor.getColumnIndexOrThrow("status"))
                val infMs = cursor.getFloat(cursor.getColumnIndexOrThrow("inferenceLatencyMs"))
                val e2eMs = cursor.getFloat(cursor.getColumnIndexOrThrow("endToEndLatencyMs"))
                val fps = cursor.getFloat(cursor.getColumnIndexOrThrow("fps"))
                val model = cursor.getString(cursor.getColumnIndexOrThrow("modelName"))
                val notes = cursor.getString(cursor.getColumnIndexOrThrow("notes"))
                val defectsJson = cursor.getString(cursor.getColumnIndexOrThrow("defects"))

                val status = try { InspectionStatus.valueOf(statusStr) } catch (_: Exception) { InspectionStatus.ERROR }
                val defects = Converters().toDefectList(defectsJson)

                list.add(InspectionRecord(id, ts, img, annot, status, defects, infMs, e2eMs, fps, model, notes))
            }
            cursor.close()
            records = list

            csvFile.bufferedWriter().use { writer ->
                // Header
                writer.write("Inspection ID,Date Time (UTC),Overall Status,Defect Count,Defects Found,Inference Latency (ms),End-to-End (ms),FPS,Model Name\n")
                for (rec in records) {
                    val dateStr = dateFormat.format(Date(rec.timestamp))
                    val defectsSummary = rec.defects.joinToString("; ") { "${it.label} (${(it.confidence * 100).toInt()}%)" }
                    val escapedDefects = "\"$defectsSummary\""
                    writer.write("${rec.id},$dateStr,${rec.status.name},${rec.defects.size},$escapedDefects,${String.format(Locale.US, "%.1f", rec.inferenceLatencyMs)},${String.format(Locale.US, "%.1f", rec.endToEndLatencyMs)},${String.format(Locale.US, "%.1f", rec.fps)},${rec.modelName}\n")
                }
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                csvFile
            )
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Formats an individual inspection summary string for sharing or printing.
     */
    fun formatInspectionSummary(record: InspectionRecord): String {
        val dateStr = dateFormat.format(Date(record.timestamp))
        val sb = StringBuilder()
        sb.append("=== INDUSTRY 4.0 INSPECTION REPORT ===\n")
        sb.append("Inspection ID: ${record.id}\n")
        sb.append("Timestamp: $dateStr\n")
        sb.append("Status: ${record.status.displayName}\n")
        sb.append("Model: ${record.modelName}\n")
        sb.append("Inference Latency: ${String.format(Locale.US, "%.1f ms", record.inferenceLatencyMs)}\n")
        sb.append("End-to-End Latency: ${String.format(Locale.US, "%.1f ms", record.endToEndLatencyMs)}\n")
        sb.append("Defects Detected (${record.defects.size}):\n")
        if (record.defects.isEmpty()) {
            sb.append("  - No trained defect classes above threshold.\n")
        } else {
            record.defects.forEachIndexed { i, d ->
                val box = d.boundingBox
                sb.append("  ${i + 1}. ${d.label.uppercase()} [${(d.confidence * 100).toInt()}%] at [${box.left.toInt()}, ${box.top.toInt()}, ${box.right.toInt()}, ${box.bottom.toInt()}]\n")
            }
        }
        sb.append("======================================\n")
        return sb.toString()
    }
}
