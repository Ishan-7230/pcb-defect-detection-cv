package com.industry4.inspection.ui.components

import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.industry4.inspection.ai.Detection
import kotlin.math.max

@Composable
fun BoundingBoxOverlay(
    detections: List<Detection>,
    sourceWidth: Int,
    sourceHeight: Int,
    modifier: Modifier = Modifier
) {
    if (sourceWidth <= 0 || sourceHeight <= 0) return

    val textPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 34f
        isAntiAlias = true
        isFakeBoldText = true
    }
    val textBounds = Rect()

    Canvas(modifier = modifier.fillMaxSize()) {
        val viewW = size.width
        val viewH = size.height

        // Calculate aspect-fill or aspect-fit scaling
        val scaleX = viewW / sourceWidth.toFloat()
        val scaleY = viewH / sourceHeight.toFloat()

        for (detection in detections) {
            val box = detection.boundingBox
            val left = box.left * scaleX
            val top = box.top * scaleY
            val right = box.right * scaleX
            val bottom = box.bottom * scaleY
            val width = max(0f, right - left)
            val height = max(0f, bottom - top)

            val boxColor = Color(detection.color)

            // Draw bounding box
            drawRoundRect(
                color = boxColor,
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = CornerRadius(8f, 8f),
                style = Stroke(width = 4f)
            )

            // Draw label background and text
            val text = "${detection.label.uppercase()} ${(detection.confidence * 100).toInt()}%"
            textPaint.getTextBounds(text, 0, text.length, textBounds)

            val pillPadding = 8f
            val pillHeight = textBounds.height() + pillPadding * 2
            val pillWidth = textBounds.width() + pillPadding * 2

            val pillTop = if (top - pillHeight < 0) top else top - pillHeight
            val pillLeft = left

            drawRoundRect(
                color = boxColor,
                topLeft = Offset(pillLeft, pillTop),
                size = Size(pillWidth, pillHeight),
                cornerRadius = CornerRadius(6f, 6f)
            )

            drawContext.canvas.nativeCanvas.drawText(
                text,
                pillLeft + pillPadding,
                pillTop + pillPadding + textBounds.height(),
                textPaint
            )
        }
    }
}
