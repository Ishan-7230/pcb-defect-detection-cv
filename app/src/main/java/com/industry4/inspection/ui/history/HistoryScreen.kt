package com.industry4.inspection.ui.history

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.industry4.inspection.data.models.InspectionRecord
import com.industry4.inspection.ui.components.IndustrialTopBar
import com.industry4.inspection.ui.components.StatusBadge
import com.industry4.inspection.ui.theme.DefectAlertRed
import com.industry4.inspection.ui.theme.IndustrialBackground
import com.industry4.inspection.ui.theme.IndustrialBorder
import com.industry4.inspection.ui.theme.IndustrialPrimary
import com.industry4.inspection.ui.theme.IndustrialSurface
import com.industry4.inspection.ui.theme.IndustrialTextPrimary
import com.industry4.inspection.ui.theme.IndustrialTextSecondary
import com.industry4.inspection.ui.theme.TechBlue
import com.industry4.inspection.viewmodel.HistoryFilter
import com.industry4.inspection.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onNavigateBack: () -> Unit,
    onSelectInspection: (String) -> Unit
) {
    val context = LocalContext.current
    val currentFilter by viewModel.filter.collectAsState()
    val inspections by viewModel.filteredInspections.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()

    var showClearDialog by remember { mutableStateOf(false) }

    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IndustrialBackground)
    ) {
        IndustrialTopBar(
            title = "Inspection History",
            subtitle = "${inspections.size} records found",
            onBackClick = onNavigateBack,
            actionIcon = Icons.Default.FileDownload,
            onActionClick = { viewModel.exportCsvReport(context) }
        )

        // Filter and Clear Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = currentFilter == HistoryFilter.ALL,
                    onClick = { viewModel.setFilter(HistoryFilter.ALL) },
                    label = { Text("All") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = IndustrialPrimary,
                        selectedLabelColor = androidx.compose.ui.graphics.Color.White
                    )
                )
                FilterChip(
                    selected = currentFilter == HistoryFilter.DEFECTS_ONLY,
                    onClick = { viewModel.setFilter(HistoryFilter.DEFECTS_ONLY) },
                    label = { Text("Defects") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DefectAlertRed,
                        selectedLabelColor = androidx.compose.ui.graphics.Color.White
                    )
                )
                FilterChip(
                    selected = currentFilter == HistoryFilter.NORMAL_ONLY,
                    onClick = { viewModel.setFilter(HistoryFilter.NORMAL_ONLY) },
                    label = { Text("Normal") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = IndustrialPrimary,
                        selectedLabelColor = androidx.compose.ui.graphics.Color.White
                    )
                )
            }

            if (inspections.isNotEmpty()) {
                IconButton(onClick = { showClearDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear History",
                        tint = DefectAlertRed
                    )
                }
            }
        }

        if (inspections.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No inspection records match the current filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IndustrialTextSecondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(inspections, key = { it.id }) { record ->
                    HistoryItemCard(
                        record = record,
                        dateFormat = dateFormat,
                        onClick = { onSelectInspection(record.id) },
                        onDelete = { viewModel.deleteInspection(record.id) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("Clear All Inspections?") },
                text = { Text("This will permanently remove all stored inspection records and saved component images.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.clearAllHistory()
                            showClearDialog = false
                        }
                    ) {
                        Text("Clear All", color = DefectAlertRed)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun HistoryItemCard(
    record: InspectionRecord,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit,
    onDelete: () -> Unit
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
                .padding(14.dp),
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
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                val desc = if (record.defects.isEmpty()) {
                    "No trained defect detected"
                } else {
                    "${record.defects.size} defect(s): " + record.defects.joinToString(", ") { "${it.label} (${(it.confidence * 100).toInt()}%)" }
                }
                Text(
                    text = desc,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = IndustrialTextPrimary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Latency: ${String.format(Locale.US, "%.1f ms", record.inferenceLatencyMs)} • Model: ${record.modelName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TechBlue
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = IndustrialTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
