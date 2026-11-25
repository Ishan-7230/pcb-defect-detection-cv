package com.industry4.inspection.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object ExportManager {

    /**
     * Triggers the Android system share sheet for the generated CSV inspection report.
     */
    fun shareCsvReport(context: Context, csvUri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, csvUri)
            putExtra(Intent.EXTRA_SUBJECT, "Industry 4.0 Defect Inspection Report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Export Inspection Report"))
    }

    /**
     * Shares an annotated defect image via system share sheet.
     */
    fun shareAnnotatedImage(context: Context, imagePath: String, title: String) {
        val imageFile = File(imagePath)
        if (!imageFile.exists()) return

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Inspection Image"))
    }

    /**
     * Shares textual summary of an inspection report.
     */
    fun shareTextSummary(context: Context, summaryText: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, summaryText)
            putExtra(Intent.EXTRA_SUBJECT, "Inspection Summary")
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Summary"))
    }
}
