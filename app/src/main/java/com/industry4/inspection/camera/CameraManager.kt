package com.industry4.inspection.camera

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Manages CameraX configuration, life-cycle binding, preview, analysis, and torch controls.
 */
class CameraManager(
    private val context: Context
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var imageCapture: ImageCapture? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    var isTorchEnabled: Boolean = false
        private set

    var lensFacing: Int = CameraSelector.LENS_FACING_BACK
        private set

    suspend fun getCameraProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { continuation ->
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                try {
                    val provider = providerFuture.get()
                    cameraProvider = provider
                    continuation.resume(provider)
                } catch (e: Exception) {
                    continuation.cancel(e)
                }
            }, ContextCompat.getMainExecutor(context))
        }

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        analyzer: ImageAnalysis.Analyzer
    ) {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, analyzer)
            }

        try {
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture,
                imageAnalysis
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            camera = null
        } catch (_: Exception) {}
    }

    fun toggleTorch(): Boolean {
        val cam = camera ?: return false
        if (cam.cameraInfo.hasFlashUnit()) {
            isTorchEnabled = !isTorchEnabled
            cam.cameraControl.enableTorch(isTorchEnabled)
            return isTorchEnabled
        }
        return false
    }

    fun shutdown() {
        stopCamera()
        cameraExecutor.shutdown()
    }
}
