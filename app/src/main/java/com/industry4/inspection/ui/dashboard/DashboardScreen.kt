package com.industry4.inspection.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.industry4.inspection.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToLive: () -> Unit,
    onNavigateToUpload: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToPerformance: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onSelectInspection: (String) -> Unit
) {
    val modelMeta by viewModel.modelMetadata.collectAsState()
    val isModelReady by viewModel.isModelReady.collectAsState()
    val totalInspections by viewModel.totalInspections.collectAsState()
    val totalDefects by viewModel.totalDefects.collectAsState()
    val recentInspections by viewModel.recentInspections.collectAsState()

    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.US)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IndustrialBackground)
    ) {
        IndustrialTopBar(
            title = "Industry 4.0 AI Inspection",
            subtitle = "AI-powered manufacturing quality inspection",
            actionIcon = Icons.Default.Settings,
            onActionClick = onNavigateToSettings
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // System Status Header Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = IndustrialSurface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(IndustrialBorder))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (isModelReady) PassNormalGreen else DefectAlertRed)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isModelReady) "MODEL ACTIVE" else "MODEL NOT LOADED",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (isModelReady) PassNormalGreen else DefectAlertRed
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = modelMeta?.name ?: "YOLO11n Baseline",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Engine: ${modelMeta?.executionProvider?.name ?: "CPU"} • ${modelMeta?.classCount ?: 10} Defect Classes",
                                style = MaterialTheme.typography.bodyMedium,
                                color = IndustrialTextSecondary
                            )
                        }

                        Button(
                            onClick = onNavigateToLive,
                            colors = ButtonDefaults.buttonColors(containerColor = IndustrialPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Inspect")
                        }
                    }
                }
            }

            // Metrics KPI Grid
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        title = "Inspections",
                        value = totalInspections.toString(),
                        subtitle = "Total logged",
                        icon = Icons.Default.Assessment,
                        iconTint = TechBlue,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Defects Found",
                        value = totalDefects.toString(),
                        subtitle = "Flagged alerts",
                        icon = Icons.Default.Warning,
                        iconTint = if (totalDefects > 0) DefectAlertRed else PassNormalGreen,
                        valueColor = if (totalDefects > 0) DefectAlertRed else IndustrialTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Quick Navigation Hub
            item {
                Text(
                    text = "WORKFLOW MODULES",
                    style = MaterialTheme.typography.labelSmall,
                    color = IndustrialTextSecondary,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NavigationActionCard(
                        title = "Live Camera",
                        desc = "Real-time edge inspection",
                        icon = Icons.Default.PlayArrow,
                        onClick = onNavigateToLive,
                        modifier = Modifier.weight(1f)
                    )
                    NavigationActionCard(
                        title = "Image Testing",
                        desc = "Inspect gallery files",
                        icon = Icons.Default.Image,
                        onClick = onNavigateToUpload,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NavigationActionCard(
                        title = "History Log",
                        desc = "Records and CSV reports",
                        icon = Icons.Default.History,
                        onClick = onNavigateToHistory,
                        modifier = Modifier.weight(1f)
                    )
                    NavigationActionCard(
                        title = "Edge Monitor",
                        desc = "FPS, latency, hardware",
                        icon = Icons.Default.Speed,
                        onClick = onNavigateToPerformance,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Recent Inspections Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT INSPECTIONS",
                        style = MaterialTheme.typography.labelSmall,
                        color = IndustrialTextSecondary,
                        letterSpacing = 0.5.sp
                    )
                    if (recentInspections.isNotEmpty()) {
                        Text(
                            text = "View All",
                            style = MaterialTheme.typography.labelSmall,
                            color = IndustrialPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onNavigateToHistory() }
                        )
                    }
                }
            }

            if (recentInspections.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No inspections recorded yet.\nStart a live camera inspection or upload a test image.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = IndustrialTextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                items(recentInspections) { inspection ->
                    InspectionSummaryItem(
                        record = inspection,
                        onClick = { onSelectInspection(inspection.id) },
                        dateFormat = dateFormat
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun NavigationActionCard(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = IndustrialSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(IndustrialBorder))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = IndustrialPrimary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = IndustrialTextPrimary
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = IndustrialTextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun InspectionSummaryItem(
    record: InspectionRecord,
    onClick: () -> Unit,
    dateFormat: SimpleDateFormat
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = IndustrialSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(IndustrialBorder))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(status = record.status)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = dateFormat.format(Date(record.timestamp)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = IndustrialTextSecondary,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                val summary = if (record.defects.isEmpty()) {
                    "No trained defect detected above threshold"
                } else {
                    record.defects.joinToString(", ") { "${it.label} (${(it.confidence * 100).toInt()}%)" }
                }
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyLarge,
                    color = IndustrialTextPrimary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = String.format(Locale.US, "%.0f ms", record.inferenceLatencyMs),
                style = MaterialTheme.typography.labelSmall,
                color = TechBlue,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
