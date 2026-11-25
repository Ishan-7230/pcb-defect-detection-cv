package com.industry4.inspection.ui.performance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.industry4.inspection.ui.components.IndustrialTopBar
import com.industry4.inspection.ui.components.MetricCard
import com.industry4.inspection.ui.theme.IndustrialBackground
import com.industry4.inspection.ui.theme.IndustrialBorder
import com.industry4.inspection.ui.theme.IndustrialPrimary
import com.industry4.inspection.ui.theme.IndustrialSurface
import com.industry4.inspection.ui.theme.IndustrialTextPrimary
import com.industry4.inspection.ui.theme.IndustrialTextSecondary
import com.industry4.inspection.ui.theme.PassNormalGreen
import com.industry4.inspection.ui.theme.TechBlue
import com.industry4.inspection.viewmodel.PerformanceViewModel
import java.util.Locale

@Composable
fun PerformanceScreen(
    viewModel: PerformanceViewModel,
    onNavigateBack: () -> Unit
) {
    val modelMeta by viewModel.modelMetadata.collectAsState()
    val stats by viewModel.performanceStats.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IndustrialBackground)
    ) {
        IndustrialTopBar(
            title = "Edge AI Performance",
            subtitle = "On-device hardware execution benchmarks",
            onBackClick = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hardware Accelerator Status
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = IndustrialSurface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(IndustrialBorder))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "EXECUTION PROVIDER STATUS",
                        style = MaterialTheme.typography.labelSmall,
                        color = IndustrialTextSecondary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = modelMeta?.executionProvider?.name ?: "CPU (XNNPACK)",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = IndustrialPrimary
                        )
                        Text(
                            text = "Model Init: ${modelMeta?.loadDurationMs ?: 0} ms",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TechBlue
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ONNX Runtime with SIMD vectorization & XNNPACK thread pooling.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IndustrialTextSecondary
                    )
                }
            }

            // Latency Benchmark Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    title = "Avg Inference",
                    value = String.format(Locale.US, "%.1f ms", stats.avgInferenceLatencyMs),
                    subtitle = "Model-only latency",
                    icon = Icons.Default.Timer,
                    iconTint = TechBlue,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Avg Total Pipeline",
                    value = String.format(Locale.US, "%.1f ms", stats.avgEndToEndLatencyMs),
                    subtitle = "End-to-end latency",
                    icon = Icons.Default.Speed,
                    iconTint = IndustrialPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    title = "Min Latency",
                    value = String.format(Locale.US, "%.1f ms", stats.minInferenceLatencyMs),
                    subtitle = "Fastest cycle",
                    iconTint = PassNormalGreen,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Max Latency",
                    value = String.format(Locale.US, "%.1f ms", stats.maxInferenceLatencyMs),
                    subtitle = "Worst-case peak",
                    iconTint = IndustrialTextSecondary,
                    modifier = Modifier.weight(1f)
                )
            }

            // Memory Utilization
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = IndustrialSurface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(IndustrialBorder))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "SYSTEM MEMORY FOOTPRINT",
                        style = MaterialTheme.typography.labelSmall,
                        color = IndustrialTextSecondary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    PerformanceRow("Used JVM Heap", "${stats.usedMemoryMb} MB")
                    Divider(color = IndustrialBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
                    PerformanceRow("Free JVM Heap", "${stats.freeMemoryMb} MB")
                    Divider(color = IndustrialBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
                    PerformanceRow("Total Allocated Heap", "${stats.totalMemoryMb} MB")
                }
            }

            // Pipeline Breakdown
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = IndustrialSurface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(IndustrialBorder))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "EDGE ARCHITECTURE SPECIFICATIONS",
                        style = MaterialTheme.typography.labelSmall,
                        color = IndustrialTextSecondary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    PerformanceRow("Input Tensor Resolution", "640 × 640 × 3 (RGB Float32)")
                    Divider(color = IndustrialBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
                    PerformanceRow("Output Anchor Count", "8,400 candidate anchors")
                    Divider(color = IndustrialBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
                    PerformanceRow("NMS Strategy", "Class-wise IoU Suppression")
                    Divider(color = IndustrialBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
                    PerformanceRow("Execution Isolation", "100% On-Device Offline")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PerformanceRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = IndustrialTextPrimary)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = IndustrialTextPrimary)
    }
}
