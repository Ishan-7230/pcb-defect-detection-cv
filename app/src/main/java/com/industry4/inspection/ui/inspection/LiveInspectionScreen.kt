package com.industry4.inspection.ui.inspection

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.industry4.inspection.camera.CameraManager
import com.industry4.inspection.camera.ImageAnalyzer
import com.industry4.inspection.ui.components.BoundingBoxOverlay
import com.industry4.inspection.ui.components.StatusBadge
import com.industry4.inspection.ui.theme.DefectAlertRed
import com.industry4.inspection.ui.theme.IndustrialPrimary
import com.industry4.inspection.ui.theme.IndustrialSurface
import com.industry4.inspection.ui.theme.IndustrialTextPrimary
import com.industry4.inspection.ui.theme.IndustrialTextSecondary
import com.industry4.inspection.ui.theme.PassNormalGreen
import com.industry4.inspection.viewmodel.InspectionViewModel
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LiveInspectionScreen(
    viewModel: InspectionViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    val isInspecting by viewModel.isInspecting.collectAsState()
    val currentDetections by viewModel.currentDetections.collectAsState()
    val metrics by viewModel.latestMetrics.collectAsState()
    val status by viewModel.latestStatus.collectAsState()
    val saveMessage by viewModel.saveSuccessMessage.collectAsState()
    val isSaving by viewModel.isSavingSnapshot.collectAsState()

    var sourceWidth by remember { mutableIntStateOf(640) }
    var sourceHeight by remember { mutableIntStateOf(640) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }

    val cameraManager = remember { CameraManager(context) }

    LaunchedEffect(saveMessage) {
        saveMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSaveMessage()
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            cameraManager.stopCamera()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!cameraPermissionState.status.isGranted) {
            // Permission Denied View
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Camera Permission Required",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Real-time edge visual inspection requires rear camera access to detect defects.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { cameraPermissionState.launchPermissionRequest() },
                        colors = ButtonDefaults.buttonColors(containerColor = IndustrialPrimary)
                    ) {
                        Text("Grant Permission")
                    }
                }
            }
        } else {
            // Camera Preview & Real-Time AI Canvas
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        previewViewRef = this
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        scope.launch {
                            cameraManager.getCameraProvider()
                            val analyzer = ImageAnalyzer(
                                modelManager = viewModel.modelManager,
                                scope = scope,
                                throttleIntervalMs = 100L
                            ) { result ->
                                sourceWidth = result.frameWidth
                                sourceHeight = result.frameHeight
                                viewModel.onFrameAnalyzed(result)
                            }
                            cameraManager.startCamera(lifecycleOwner, this@apply, analyzer)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Bounding Box Real-Time Overlay
            if (isInspecting) {
                BoundingBoxOverlay(
                    detections = currentDetections,
                    sourceWidth = sourceWidth,
                    sourceHeight = sourceHeight,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Top HUD Bar with Telemetry
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                    .align(Alignment.TopCenter),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = IndustrialSurface.copy(alpha = 0.94f)),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color.LightGray))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onNavigateBack, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = IndustrialTextPrimary)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "EDGE INSPECTION",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndustrialTextPrimary
                            )
                        }

                        StatusBadge(status = if (isInspecting) status else com.industry4.inspection.data.models.InspectionStatus.NO_TRAINED_DEFECT_DETECTED)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Real-Time Telemetry Metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        HudMetricItem("Inference", String.format(Locale.US, "%.1f ms", metrics.modelInferenceLatencyMs))
                        HudMetricItem("E2E Latency", String.format(Locale.US, "%.1f ms", metrics.endToEndLatencyMs))
                        HudMetricItem("FPS", String.format(Locale.US, "%.1f", metrics.currentFps))
                        HudMetricItem("Defects", currentDetections.size.toString(), highlightColor = if (currentDetections.isNotEmpty()) DefectAlertRed else PassNormalGreen)
                        HudMetricItem("Engine", metrics.activeProvider)
                    }
                }
            }

            // Detected Defect Pills Bar (Horizontal scroll if multiple defects)
            if (isInspecting && currentDetections.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 104.dp)
                        .align(Alignment.TopCenter),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = DefectAlertRed.copy(alpha = 0.9f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DETECTED: ${currentDetections.joinToString(", ") { "${it.label.uppercase()} (${(it.confidence * 100).toInt()}%)" }}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Bottom Controls Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp, start = 20.dp, end = 20.dp)
                    .align(Alignment.BottomCenter)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Flash / Torch Toggle
                    FloatingActionButton(
                        onClick = {
                            isTorchOn = cameraManager.toggleTorch()
                        },
                        containerColor = if (isTorchOn) Color(0xFFFFD54F) else IndustrialSurface,
                        contentColor = IndustrialTextPrimary,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = "Torch")
                    }

                    // Start / Stop Inspection Button
                    Button(
                        onClick = { viewModel.toggleInspection() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isInspecting) DefectAlertRed else PassNormalGreen
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isInspecting) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isInspecting) "PAUSE INSPECTION" else "RESUME INSPECTION",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Snapshot / Save Inspection Button
                    FloatingActionButton(
                        onClick = {
                            val bitmap = previewViewRef?.bitmap
                            viewModel.saveSnapshot(bitmap)
                        },
                        containerColor = IndustrialPrimary,
                        contentColor = Color.White,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Save Snapshot")
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
        )
    }
}

@Composable
private fun HudMetricItem(
    label: String,
    value: String,
    highlightColor: Color = IndustrialTextPrimary
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 10.sp, color = IndustrialTextSecondary, fontWeight = FontWeight.Medium)
        Text(text = value, fontSize = 12.sp, color = highlightColor, fontWeight = FontWeight.Bold)
    }
}
