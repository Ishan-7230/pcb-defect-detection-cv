package com.industry4.inspection

import android.graphics.RectF
import com.industry4.inspection.ai.LetterboxInfo
import com.industry4.inspection.ai.YoloPostProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class YoloPostProcessorTest {

    private lateinit var postProcessor: YoloPostProcessor
    private val classLabels = listOf(
        "crease",
        "crescent_gap",
        "inclusion",
        "oil_spot",
        "punching_hole",
        "rolled_pit",
        "silk_spot",
        "waist_folding",
        "water_spot",
        "welding_line"
    )

    private val letterboxInfo = LetterboxInfo(
        scale = 1.0f,
        padX = 0f,
        padY = 0f,
        originalWidth = 640,
        originalHeight = 640,
        targetWidth = 640,
        targetHeight = 640
    )

    @Before
    fun setUp() {
        postProcessor = YoloPostProcessor(
            confidenceThreshold = 0.25f,
            iouThreshold = 0.45f
        )
    }

    @Test
    fun testEmptyDetectionsWhenScoresBelowThreshold() {
        // Output shape: [1, 14, 10]
        val channels = 14
        val numAnchors = 10
        val buffer = ByteBuffer.allocateDirect(channels * numAnchors * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        // Fill with low scores (< 0.25)
        for (i in 0 until channels * numAnchors) {
            buffer.put(0.10f)
        }
        buffer.rewind()

        val detections = postProcessor.process(
            outputBuffer = buffer,
            shape = longArrayOf(1, channels.toLong(), numAnchors.toLong()),
            letterboxInfo = letterboxInfo,
            classLabels = classLabels
        )

        assertTrue("Expected 0 detections when scores are below threshold", detections.isEmpty())
    }

    @Test
    fun testSingleDefectDetection() {
        // Output shape: [1, 14, 5]
        val channels = 14
        val numAnchors = 5
        val buffer = ByteBuffer.allocateDirect(channels * numAnchors * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        val rawArray = FloatArray(channels * numAnchors)

        // Anchor 0: cx=320, cy=320, w=100, h=80
        rawArray[0 * numAnchors + 0] = 320f
        rawArray[1 * numAnchors + 0] = 320f
        rawArray[2 * numAnchors + 0] = 100f
        rawArray[3 * numAnchors + 0] = 80f

        // Class 2 ("inclusion"): high confidence 0.88f
        val inclusionClassIndex = 2
        rawArray[(4 + inclusionClassIndex) * numAnchors + 0] = 0.88f

        buffer.put(rawArray)
        buffer.rewind()

        val detections = postProcessor.process(
            outputBuffer = buffer,
            shape = longArrayOf(1, channels.toLong(), numAnchors.toLong()),
            letterboxInfo = letterboxInfo,
            classLabels = classLabels
        )

        assertEquals(1, detections.size)
        val d = detections[0]
        assertEquals("inclusion", d.label)
        assertEquals(2, d.classId)
        assertEquals(0.88f, d.confidence, 0.001f)
        assertEquals(270f, d.boundingBox.left, 0.1f) // 320 - 50
        assertEquals(280f, d.boundingBox.top, 0.1f)  // 320 - 40
        assertEquals(370f, d.boundingBox.right, 0.1f)// 320 + 50
        assertEquals(360f, d.boundingBox.bottom, 0.1f)// 320 + 40
    }

    @Test
    fun testNmsSuppressionOfOverlappingBoxes() {
        // Two anchors predicting same class in same location
        val channels = 14
        val numAnchors = 2
        val buffer = ByteBuffer.allocateDirect(channels * numAnchors * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        val rawArray = FloatArray(channels * numAnchors)

        // Anchor 0: cx=100, cy=100, w=50, h=50, conf=0.90 for class 0 ("crease")
        rawArray[0 * numAnchors + 0] = 100f
        rawArray[1 * numAnchors + 0] = 100f
        rawArray[2 * numAnchors + 0] = 50f
        rawArray[3 * numAnchors + 0] = 50f
        rawArray[(4 + 0) * numAnchors + 0] = 0.90f

        // Anchor 1: almost identical box cx=102, cy=101, w=50, h=50, conf=0.75 for class 0
        rawArray[0 * numAnchors + 1] = 102f
        rawArray[1 * numAnchors + 1] = 101f
        rawArray[2 * numAnchors + 1] = 50f
        rawArray[3 * numAnchors + 1] = 50f
        rawArray[(4 + 0) * numAnchors + 1] = 0.75f

        buffer.put(rawArray)
        buffer.rewind()

        val detections = postProcessor.process(
            outputBuffer = buffer,
            shape = longArrayOf(1, channels.toLong(), numAnchors.toLong()),
            letterboxInfo = letterboxInfo,
            classLabels = classLabels
        )

        // IoU should suppress anchor 1
        assertEquals(1, detections.size)
        assertEquals(0.90f, detections[0].confidence, 0.001f)
    }

    @Test
    fun testMultipleDistinctDefectsRetained() {
        val channels = 14
        val numAnchors = 2
        val buffer = ByteBuffer.allocateDirect(channels * numAnchors * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        val rawArray = FloatArray(channels * numAnchors)

        // Anchor 0: "crease" in top-left
        rawArray[0 * numAnchors + 0] = 50f
        rawArray[1 * numAnchors + 0] = 50f
        rawArray[2 * numAnchors + 0] = 30f
        rawArray[3 * numAnchors + 0] = 30f
        rawArray[(4 + 0) * numAnchors + 0] = 0.85f

        // Anchor 1: "welding_line" in bottom-right
        rawArray[0 * numAnchors + 1] = 500f
        rawArray[1 * numAnchors + 1] = 500f
        rawArray[2 * numAnchors + 1] = 100f
        rawArray[3 * numAnchors + 1] = 20f
        rawArray[(4 + 9) * numAnchors + 1] = 0.92f

        buffer.put(rawArray)
        buffer.rewind()

        val detections = postProcessor.process(
            outputBuffer = buffer,
            shape = longArrayOf(1, channels.toLong(), numAnchors.toLong()),
            letterboxInfo = letterboxInfo,
            classLabels = classLabels
        )

        assertEquals(2, detections.size)
    }
}
