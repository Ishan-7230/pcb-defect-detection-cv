package com.industry4.inspection.ai

import android.graphics.RectF

/**
 * Represents a single detected surface defect.
 *
 * @param classId Numeric class index predicted by the YOLO model.
 * @param label Human-readable defect name (e.g. "crease", "inclusion", "scratch").
 * @param confidence Confidence probability between 0.0 and 1.0.
 * @param boundingBox Bounding box coordinates normalized or scaled to original image dimensions.
 * @param color ARGB color value used for bounding box and label rendering.
 */
data class Detection(
    val classId: Int,
    val label: String,
    val confidence: Float,
    val boundingBox: RectF,
    val color: Long = getColorForClass(classId)
) {
    companion object {
        private val DEFECT_PALETTE = longArrayOf(
            0xFFFF1744, // Red (Crease)
            0xFFFF9100, // Amber (Crescent Gap)
            0xFFD500F9, // Magenta (Inclusion)
            0xFF00E5FF, // Cyan (Oil Spot)
            0xFFFFEA00, // Yellow (Punching Hole)
            0xFFFF3D00, // Deep Orange (Rolled Pit)
            0xFF00E676, // Bright Green (Silk Spot)
            0xFF7C4DFF, // Deep Purple (Waist Folding)
            0xFF2979FF, // Blue (Water Spot)
            0xFFE040FB  // Pink (Welding Line)
        )

        fun getColorForClass(classId: Int): Long {
            return if (classId in DEFECT_PALETTE.indices) {
                DEFECT_PALETTE[classId]
            } else {
                0xFFFF5252
            }
        }
    }
}
