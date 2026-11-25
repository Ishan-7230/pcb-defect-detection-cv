package com.industry4.inspection.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import com.industry4.inspection.ai.Detection
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

object BitmapUtils {

    /**
     * Draws detected bounding boxes, class labels, and confidence tags directly onto a copy of the Bitmap.
     */
    fun drawDetectionsOnBitmap(source: Bitmap, detections: List<Detection>): Bitmap {
        val mutableBitmap = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)

        val boxPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = (source.width / 200f).coerceAtLeast(4f)
            isAntiAlias = true
        }

        val textBackgroundPaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = (source.width / 32f).coerceIn(24f, 48f)
            isAntiAlias = true
            isFakeBoldText = true
        }

        val textBounds = Rect()

        for (detection in detections) {
            val color = detection.color.toInt()
            boxPaint.color = color
            textBackgroundPaint.color = color

            // Draw bounding box
            val box = detection.boundingBox
            canvas.drawRoundRect(box, 8f, 8f, boxPaint)

            // Label text
            val text = "${detection.label.uppercase()} ${(detection.confidence * 100).toInt()}%"
            textPaint.getTextBounds(text, 0, text.length, textBounds)

            val textPadding = 8f
            val labelX = box.left
            val labelY = if (box.top - textBounds.height() - textPadding * 2 < 0) {
                box.top + textBounds.height() + textPadding * 2
            } else {
                box.top
            }

            val bgRect = RectF(
                labelX,
                labelY - textBounds.height() - textPadding * 2,
                labelX + textBounds.width() + textPadding * 2,
                labelY
            )
            canvas.drawRoundRect(bgRect, 4f, 4f, textBackgroundPaint)

            canvas.drawText(
                text,
                labelX + textPadding,
                labelY - textPadding,
                textPaint
            )
        }

        return mutableBitmap
    }

    /**
     * Rotates a Bitmap by degrees.
     */
    fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return source
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Converts CameraX ImageProxy (YUV_420_888 or RGBA_8888) to a rotated RGB Bitmap.
     */
    fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val bitmap = when (imageProxy.format) {
                ImageFormat.YUV_420_888 -> yuv420ToBitmap(imageProxy)
                ImageFormat.JPEG -> {
                    val buffer = imageProxy.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                else -> {
                    // Try to use cameraX built-in toBitmap if supported
                    try {
                        imageProxy.toBitmap()
                    } catch (_: Exception) {
                        null
                    }
                }
            }

            if (bitmap != null && rotationDegrees != 0) {
                val rotated = rotateBitmap(bitmap, rotationDegrees.toFloat())
                if (rotated != bitmap && !bitmap.isRecycled) {
                    bitmap.recycle()
                }
                rotated
            } else {
                bitmap
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Converts a YUV_420_888 ImageProxy to RGB Bitmap via YuvImage and JPEG decoding.
     */
    private fun yuv420ToBitmap(image: ImageProxy): Bitmap? {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val numPixels = image.width * image.height
        val nv21 = ByteArray(numPixels + 2 * (numPixels / 4))

        // Copy Y
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        var nv21Pos = 0

        val width = image.width
        val height = image.height

        for (row in 0 until height) {
            val yBufferPos = row * yRowStride
            yBuffer.position(yBufferPos)
            if (yPixelStride == 1) {
                yBuffer.get(nv21, nv21Pos, width)
                nv21Pos += width
            } else {
                for (col in 0 until width) {
                    nv21[nv21Pos++] = yBuffer.get(yBufferPos + col * yPixelStride)
                }
            }
        }

        // Copy UV interleaved
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val uIndex = row * uvRowStride + col * uvPixelStride
                val vIndex = row * vPlane.rowStride + col * vPlane.pixelStride
                nv21[nv21Pos++] = vBuffer.get(vIndex)
                nv21[nv21Pos++] = uBuffer.get(uIndex)
            }
        }

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
}
