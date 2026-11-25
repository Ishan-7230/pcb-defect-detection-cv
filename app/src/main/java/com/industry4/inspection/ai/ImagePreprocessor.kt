package com.industry4.inspection.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.min

/**
 * Letterbox transformation parameters for scaling predictions back to original dimensions.
 */
data class LetterboxInfo(
    val scale: Float,
    val padX: Float,
    val padY: Float,
    val originalWidth: Int,
    val originalHeight: Int,
    val targetWidth: Int = 640,
    val targetHeight: Int = 640
)

/**
 * Prepares input bitmaps into planar FloatBuffer tensors [1, 3, 640, 640] for ONNX Runtime.
 */
class ImagePreprocessor(
    val targetWidth: Int = 640,
    val targetHeight: Int = 640
) {
    // 1 * 3 * 640 * 640 = 1,228,800 floats = 4,915,200 bytes
    private val bufferSize = 1 * 3 * targetWidth * targetHeight
    private val byteBuffer: ByteBuffer = ByteBuffer.allocateDirect(bufferSize * 4).apply {
        order(ByteOrder.nativeOrder())
    }
    val floatBuffer: FloatBuffer = byteBuffer.asFloatBuffer()

    private val letterboxBitmap: Bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    private val letterboxCanvas: Canvas = Canvas(letterboxBitmap)
    private val backgroundPaint: Paint = Paint().apply {
        color = Color.rgb(114, 114, 114) // Standard YOLO padding gray (114)
    }
    private val drawPaint: Paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pixelArray: IntArray = IntArray(targetWidth * targetHeight)

    /**
     * Resizes the source image preserving aspect ratio with letterboxing and populates the FloatBuffer.
     *
     * @param source Source RGB/ARGB Bitmap
     * @return LetterboxInfo to map postprocessed boxes back to source dimensions
     */
    @Synchronized
    fun preprocess(source: Bitmap): LetterboxInfo {
        val origW = source.width
        val origH = source.height

        val scale = min(targetWidth.toFloat() / origW, targetHeight.toFloat() / origH)
        val scaledW = (origW * scale).toInt()
        val scaledH = (origH * scale).toInt()
        val padX = (targetWidth - scaledW) / 2f
        val padY = (targetHeight - scaledH) / 2f

        // Clear canvas with padding color
        letterboxCanvas.drawRect(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat(), backgroundPaint)

        // Draw scaled bitmap centered
        val scaledBitmap = Bitmap.createScaledBitmap(source, scaledW, scaledH, true)
        letterboxCanvas.drawBitmap(scaledBitmap, padX, padY, drawPaint)
        if (scaledBitmap != source && !scaledBitmap.isRecycled) {
            scaledBitmap.recycle()
        }

        // Extract ARGB pixels
        letterboxBitmap.getPixels(pixelArray, 0, targetWidth, 0, 0, targetWidth, targetHeight)

        // Populate planar FloatBuffer [1, 3, targetHeight, targetWidth] (RGB format, normalized 0..1)
        floatBuffer.rewind()
        val area = targetWidth * targetHeight

        val rOffset = 0
        val gOffset = area
        val bOffset = 2 * area

        for (i in 0 until area) {
            val pixel = pixelArray[i]
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            floatBuffer.put(rOffset + i, r)
            floatBuffer.put(gOffset + i, g)
            floatBuffer.put(bOffset + i, b)
        }

        floatBuffer.position(0)

        return LetterboxInfo(
            scale = scale,
            padX = padX,
            padY = padY,
            originalWidth = origW,
            originalHeight = origH,
            targetWidth = targetWidth,
            targetHeight = targetHeight
        )
    }
}
