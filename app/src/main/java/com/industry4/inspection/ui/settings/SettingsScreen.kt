package com.industry4.inspection.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.industry4.inspection.ai.ExecutionProvider
import com.industry4.inspection.ui.components.IndustrialTopBar
import com.industry4.inspection.ui.theme.DefectAlertRed
import com.industry4.inspection.ui.theme.IndustrialBackground
import com.industry4.inspection.ui.theme.IndustrialBorder
import com.industry4.inspection.ui.theme.IndustrialPrimary
import com.industry4.inspection.ui.theme.IndustrialSurface
import com.industry4.inspection.ui.theme.IndustrialTextPrimary
import com.industry4.inspection.ui.theme.IndustrialTextSecondary
import com.industry4.inspection.ui.theme.PassNormalGreen
import com.industry4.inspection.viewmodel.SettingsViewModel
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    val modelMeta by viewModel.modelMetadata.collectAsState()
    val classLabels by viewModel.classLabels.collectAsState()
    val confidenceThreshold by viewModel.confidenceThreshold.collectAsState()
    val iouThreshold by viewModel.iouThreshold.collectAsState()
    val throttleIntervalMs by viewModel.throttleIntervalMs.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.importCustomModel(it, "custom_defect_detector.onnx")
        }
    }

    val labelsPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.importCustomLabels(it)
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IndustrialBackground)
    ) {
        IndustrialTopBar(
            title = "Model Configuration",
            subtitle = "Thresholds, hardware providers & custom model import",
            onBackClick = onNavigateBack
        )

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Confidence Threshold Card
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
                            Text(
                                text = "CONFIDENCE THRESHOLD",
                                style = MaterialTheme.typography.labelSmall,
                                color = IndustrialTextSecondary,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "${(confidenceThreshold * 100).toInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndustrialPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Filter defect candidates below this score. Default is 25%.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = IndustrialTextSecondary
                        )
                        Slider(
                            value = confidenceThreshold,
                            onValueChange = { viewModel.updateConfidenceThreshold(it) },
                            valueRange = 0.05f..0.95f,
                            steps = 90,
                            colors = SliderDefaults.colors(
                                thumbColor = IndustrialPrimary,
                                activeTrackColor = IndustrialPrimary
                            )
                        )
                    }
                }

                // NMS IoU Threshold Card
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
                            Text(
                                text = "NMS IoU THRESHOLD",
                                style = MaterialTheme.typography.labelSmall,
                                color = IndustrialTextSecondary,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "${(iouThreshold * 100).toInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndustrialPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Non-Maximum Suppression overlap threshold for deduplicating boxes. Default is 45%.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = IndustrialTextSecondary
                        )
                        Slider(
                            value = iouThreshold,
                            onValueChange = { viewModel.updateIouThreshold(it) },
                            valueRange = 0.10f..0.90f,
                            steps = 80,
                            colors = SliderDefaults.colors(
                                thumbColor = IndustrialPrimary,
                                activeTrackColor = IndustrialPrimary
                            )
                        )
                    }
                }

                // Execution Provider Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = IndustrialSurface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(IndustrialBorder))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "INFERENCE ACCELERATION PROVIDER",
                            style = MaterialTheme.typography.labelSmall,
                            color = IndustrialTextSecondary,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val activeProvider = modelMeta?.executionProvider ?: ExecutionProvider.CPU

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = activeProvider == ExecutionProvider.CPU,
                                onClick = { viewModel.setExecutionProvider(ExecutionProvider.CPU) },
                                colors = RadioButtonDefaults.colors(selectedColor = IndustrialPrimary)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text("CPU (XNNPACK Optimized)", fontWeight = FontWeight.SemiBold)
                                Text("Universal compatibility, highly stable on all Android chipsets", style = MaterialTheme.typography.bodyMedium, color = IndustrialTextSecondary)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = activeProvider == ExecutionProvider.NNAPI,
                                onClick = { viewModel.setExecutionProvider(ExecutionProvider.NNAPI) },
                                colors = RadioButtonDefaults.colors(selectedColor = IndustrialPrimary)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text("NNAPI (Hardware NPU / DSP)", fontWeight = FontWeight.SemiBold)
                                Text("Hardware accelerator where supported; automatically falls back to CPU if unavailable", style = MaterialTheme.typography.bodyMedium, color = IndustrialTextSecondary)
                            }
                        }
                    }
                }

                // Frame Throttling Interval
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
                            Text(
                                text = "CAMERA FRAME THROTTLING",
                                style = MaterialTheme.typography.labelSmall,
                                color = IndustrialTextSecondary,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "${throttleIntervalMs} ms",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndustrialPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Limits continuous frame analysis rate to prevent overheating and conserve battery.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = IndustrialTextSecondary
                        )
                        Slider(
                            value = throttleIntervalMs.toFloat(),
                            onValueChange = { viewModel.updateThrottleInterval(it.toLong()) },
                            valueRange = 50f..500f,
                            steps = 9,
                            colors = SliderDefaults.colors(
                                thumbColor = IndustrialPrimary,
                                activeTrackColor = IndustrialPrimary
                            )
                        )
                    }
                }

                // Model Structure & Replacement Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = IndustrialSurface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(IndustrialBorder))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "ACTIVE MODEL INFORMATION",
                            style = MaterialTheme.typography.labelSmall,
                            color = IndustrialTextSecondary,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        DetailRow("Model Name", modelMeta?.name ?: "Unknown")
                        Divider(color = IndustrialBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 6.dp))
                        DetailRow("Input Tensor", "${modelMeta?.inputName ?: "images"} [${modelMeta?.inputShape?.joinToString(", ")}]")
                        Divider(color = IndustrialBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 6.dp))
                        DetailRow("Output Tensor", "${modelMeta?.outputName ?: "output0"} [${modelMeta?.outputShape?.joinToString(", ")}]")
                        Divider(color = IndustrialBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 6.dp))
                        DetailRow("Detected Classes", "${modelMeta?.classCount ?: classLabels.size}")
                        Divider(color = IndustrialBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 6.dp))
                        DetailRow("Labels Source", if (modelMeta?.source == com.industry4.inspection.ai.ModelSource.CUSTOM_IMPORTED) "Custom File" else "Bundled Assets")

                        Spacer(modifier = Modifier.height(14.dp))

                        // Custom Model Import Action
                        Button(
                            onClick = { modelPickerLauncher.launch("*/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = IndustrialPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Import Custom ONNX Model (.onnx)")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Custom Labels Import Action
                        OutlinedButton(
                            onClick = { labelsPickerLauncher.launch("*/*") },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Label, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Import Custom Labels (.txt / .json)")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Reset to Default Action
                        OutlinedButton(
                            onClick = { viewModel.resetToDefault() },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reset to Bundled Baseline Model")
                        }
                    }
                }

                // Class Names List
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = IndustrialSurface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(IndustrialBorder))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "CONFIGURED DEFECT CLASSES (${classLabels.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = IndustrialTextSecondary,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        classLabels.forEachIndexed { idx, label ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "$idx. $label",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (idx < classLabels.lastIndex) {
                                Divider(color = IndustrialBorder.copy(alpha = 0.4f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = IndustrialPrimary)
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = IndustrialTextSecondary)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = IndustrialTextPrimary)
    }
}
