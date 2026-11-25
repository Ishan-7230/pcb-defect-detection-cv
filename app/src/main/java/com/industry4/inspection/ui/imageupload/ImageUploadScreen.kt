package com.industry4.inspection.ui.imageupload

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.industry4.inspection.viewmodel.ImageUploadViewModel
import java.util.Locale

@Composable
fun ImageUploadScreen(
    viewModel: ImageUploadViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val selectedBitmap by viewModel.selectedBitmap.collectAsState()
    val annotatedBitmap by viewModel.annotatedBitmap.collectAsState()
    val detections by viewModel.detections.collectAsState()
    val metrics by viewModel.metrics.collectAsState()
    val status by viewModel.status.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val saveStatus by viewModel.saveStatus.collectAsState()

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.processImageUri(context, it) }
    }

    LaunchedEffect(saveStatus) {
        saveStatus?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IndustrialBackground)
    ) {
        IndustrialTopBar(
            title = "Image Testing",
            subtitle = "Inspect high-resolution component images",
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
                if (selectedBitmap == null) {
                    // Empty state: Select image card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = IndustrialSurface),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(IndustrialBorder))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                tint = IndustrialPrimary,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Select Component Image",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Supported formats: JPG, JPEG, PNG, WebP",
                                style = MaterialTheme.typography.bodyMedium,
                                color = IndustrialTextSecondary
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { galleryLauncher.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = IndustrialPrimary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Choose from Gallery")
                            }
                        }
                    }
                } else {
                    // Annotated Image Display
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = IndustrialSurface),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(IndustrialBorder))
                    ) {
                        Column {
                            val displayBitmap = annotatedBitmap ?: selectedBitmap
                            if (displayBitmap != null) {
                                Image(
                                    bitmap = displayBitmap.asImageBitmap(),
                                    contentDescription = "Inspected Component",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp)
                                        .background(Color.Black),
                                    contentScale = ContentScale.Fit
                                )
                            }

                            // Image details bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                status?.let { StatusBadge(status = it) }
                                Text(
                                    text = "${selectedBitmap?.width} × ${selectedBitmap?.height} px",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = IndustrialTextSecondary
                                )
                            }
                        }
                    }

                    // Metrics Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricCard(
                            title = "Inference Latency",
                            value = String.format(Locale.US, "%.1f ms", metrics.modelInferenceLatencyMs),
                            subtitle = "Local model execution",
                            icon = Icons.Default.Speed,
                            iconTint = TechBlue,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Defects Detected",
                            value = detections.size.toString(),
                            subtitle = if (detections.isEmpty()) "Within tolerance" else "Action required",
                            valueColor = if (detections.isNotEmpty()) DefectAlertRed else PassNormalGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Detected Defects Table
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = IndustrialSurface),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(IndustrialBorder))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "DETECTION BREAKDOWN",
                                style = MaterialTheme.typography.labelSmall,
                                color = IndustrialTextSecondary,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            if (detections.isEmpty()) {
                                Text(
                                    text = "No trained defect classes detected above confidence threshold.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PassNormalGreen,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                detections.forEachIndexed { idx, det ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(Color(det.color))
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${idx + 1}. ${det.label.uppercase()}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        Text(
                                            text = "${(det.confidence * 100).toInt()}% conf",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = IndustrialTextPrimary
                                        )
                                    }
                                    if (idx < detections.lastIndex) {
                                        Divider(color = IndustrialBorder.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                    }

                    // Actions Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.reset()
                                galleryLauncher.launch("image/*")
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Image")
                        }

                        Button(
                            onClick = { viewModel.saveInspection() },
                            colors = ButtonDefaults.buttonColors(containerColor = IndustrialPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Record")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (isAnalyzing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = IndustrialSurface)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = IndustrialPrimary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Running ONNX Inference...", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            )
        }
    }
}
