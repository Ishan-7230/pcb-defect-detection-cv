package com.industry4.inspection.ui.history

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.industry4.inspection.data.models.InspectionRecord
import com.industry4.inspection.ui.components.IndustrialTopBar
import com.industry4.inspection.ui.components.MetricCard
import com.industry4.inspection.ui.components.StatusBadge
import com.industry4.inspection.ui.theme.DefectAlertRed
import com.industry4.inspection.ui.theme.IndustrialBackground
import com.industry4.inspection.ui.theme.IndustrialBorder
import com.industry4.inspection.ui.theme.IndustrialPrimary
import com.industry4.inspection.ui.theme.IndustrialSurface
import com.industry4.inspection.ui.theme.IndustrialTextPrimary
import com.industry4.inspection.ui.theme.IndustrialTextSecondary
import com.industry4.inspection.ui.theme.PassNormalGreen
import com.industry4.inspection.ui.theme.TechBlue
import com.industry4.inspection.utils.ExportManager
import com.industry4.inspection.viewmodel.HistoryViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InspectionDetailScreen(
    inspectionId: String,
    viewModel: HistoryViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var record by remember { mutableStateOf<InspectionRecord?>(null) }

    LaunchedEffect(inspectionId) {
        record = viewModel.getInspectionById(inspectionId)
    }

    val currentRecord = record
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IndustrialBackground)
    ) {
        IndustrialTopBar(
            title = "Inspection Report",
            subtitle = "ID: ${inspectionId.take(8)}...",
            onBackClick = onNavigateBack,
            actionIcon = Icons.Default.Share,
            onActionClick = {
                currentRecord?.let {
                    ExportManager.shareTextSummary(context, viewModel.formatSummary(it))
                }
            }
        )

        if (currentRecord == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading inspection record...", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Image Card
                val imagePathToDisplay = currentRecord.annotatedImagePath ?: currentRecord.imagePath
                if (imagePathToDisplay.isNotEmpty() && File(imagePathToDisplay).exists()) {
                    val bitmap = remember(imagePathToDisplay) {
                        BitmapFactory.decodeFile(imagePathToDisplay)
                    }
                    if (bitmap != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = IndustrialSurface),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(IndustrialBorder))
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Inspection Result",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp)
                                    .background(Color.Black),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }

                // Status & Timestamp Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = IndustrialSurface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(IndustrialBorder))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusBadge(status = currentRecord.status)
                            Text(
                                text = dateFormat.format(Date(currentRecord.timestamp)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = IndustrialTextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Model: ${currentRecord.modelName}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Performance Metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        title = "Inference Latency",
                        value = String.format(Locale.US, "%.1f ms", currentRecord.inferenceLatencyMs),
                        subtitle = "Hardware execution",
                        iconTint = TechBlue,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "End-to-End Latency",
                        value = String.format(Locale.US, "%.1f ms", currentRecord.endToEndLatencyMs),
                        subtitle = "Total pipeline",
                        iconTint = TechBlue,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Detected Defects List
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = IndustrialSurface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(IndustrialBorder))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "DEFECTS IDENTIFIED (${currentRecord.defects.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = IndustrialTextSecondary,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (currentRecord.defects.isEmpty()) {
                            Text(
                                text = "No trained defect classes detected above confidence threshold.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PassNormalGreen
                            )
                        } else {
                            currentRecord.defects.forEachIndexed { i, d ->
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${i + 1}. ${d.label.uppercase()}",
                                            fontWeight = FontWeight.Bold,
                                            color = DefectAlertRed
                                        )
                                        Text(
                                            text = "${(d.confidence * 100).toInt()}% Confidence",
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    val b = d.boundingBox
                                    Text(
                                        text = "Bounding Box: [L:${b.left.toInt()}, T:${b.top.toInt()}, R:${b.right.toInt()}, B:${b.bottom.toInt()}]",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = IndustrialTextSecondary
                                    )
                                    if (i < currentRecord.defects.lastIndex) {
                                        Divider(modifier = Modifier.padding(vertical = 6.dp), color = IndustrialBorder.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                    }
                }

                // Action: Share annotated image
                if (currentRecord.annotatedImagePath != null && File(currentRecord.annotatedImagePath).exists()) {
                    Button(
                        onClick = {
                            ExportManager.shareAnnotatedImage(
                                context,
                                currentRecord.annotatedImagePath,
                                "Defect Inspection ${currentRecord.id.take(8)}"
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IndustrialPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Annotated Inspection Image")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
