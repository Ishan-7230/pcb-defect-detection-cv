package com.industry4.inspection.ai

import android.graphics.RectF
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * Decodes YOLO raw output tensors into candidate detections, performs Non-Maximum Suppression (NMS),
 * and maps bounding boxes back to the original input image coordinate space.
 */
class YoloPostProcessor(
    var confidenceThreshold: Float = 0.25f,
    var iouThreshold: Float = 0.45f
) {

    /**
     * Internal candidate detection prior to NMS.
     */
    private data class Candidate(
        val classId: Int,
        val confidence: Float,
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float
    )

    /**
     * Decodes the raw output FloatBuffer from ONNX Runtime.
     *
     * @param outputBuffer FloatBuffer returned by the ONNX session
     * @param shape Output tensor dimensions (e.g. [1, 14, 8400] or [1, 8400, 14])
     * @param letterboxInfo Geometry info for inverting the letterbox resize
     * @param classLabels List of defect class names
     */
    fun process(
        outputBuffer: FloatBuffer,
        shape: LongArray,
        letterboxInfo: LetterboxInfo,
        classLabels: List<String>
    ): List<Detection> {
        val candidates = mutableListOf<Candidate>()
        outputBuffer.rewind()

        if (shape.size == 3) {
            val d0 = shape[0].toInt() // 1
            val d1 = shape[1].toInt() // could be (4 + C) or 8400
            val d2 = shape[2].toInt() // could be 8400 or (4 + C)

            if (d1 < d2 && d1 >= 5) {
                // Shape: [1, 4 + C, numAnchors] (Standard Ultralytics YOLOv8 / YOLO11)
                val channels = d1
                val numAnchors = d2
                val numClasses = channels - 4

                val rawArray = FloatArray(channels * numAnchors)
                outputBuffer.get(rawArray)

                for (anchorIdx in 0 until numAnchors) {
                    // Extract best class score
                    var bestScore = 0.0f
                    var bestClassId = -1

                    for (c in 0 until numClasses) {
                        val score = rawArray[(4 + c) * numAnchors + anchorIdx]
                        if (score > bestScore) {
                            bestScore = score
                            bestClassId = c
                        }
                    }

                    if (bestScore >= confidenceThreshold && bestClassId >= 0) {
                        val cx = rawArray[0 * numAnchors + anchorIdx]
                        val cy = rawArray[1 * numAnchors + anchorIdx]
                        val w = rawArray[2 * numAnchors + anchorIdx]
                        val h = rawArray[3 * numAnchors + anchorIdx]

                        val x1 = cx - w / 2f
                        val y1 = cy - h / 2f
                        val x2 = cx + w / 2f
                        val y2 = cy + h / 2f

                        candidates.add(Candidate(bestClassId, bestScore, x1, y1, x2, y2))
                    }
                }
            } else if (d1 > d2 && d2 >= 5) {
                // Shape: [1, numAnchors, 4 + C] (Transposed format)
                val numAnchors = d1
                val channels = d2
                val numClasses = channels - 4

                val rawArray = FloatArray(numAnchors * channels)
                outputBuffer.get(rawArray)

                for (anchorIdx in 0 until numAnchors) {
                    val offset = anchorIdx * channels
                    var bestScore = 0.0f
                    var bestClassId = -1

                    for (c in 0 until numClasses) {
                        val score = rawArray[offset + 4 + c]
                        if (score > bestScore) {
                            bestScore = score
                            bestClassId = c
                        }
                    }

                    if (bestScore >= confidenceThreshold && bestClassId >= 0) {
                        val cx = rawArray[offset]
                        val cy = rawArray[offset + 1]
                        val w = rawArray[offset + 2]
                        val h = rawArray[offset + 3]

                        val x1 = cx - w / 2f
                        val y1 = cy - h / 2f
                        val x2 = cx + w / 2f
                        val y2 = cy + h / 2f

                        candidates.add(Candidate(bestClassId, bestScore, x1, y1, x2, y2))
                    }
                }
            } else if (d2 == 6) {
                // Shape: [1, N, 6] (End-to-End NMS format: [x1, y1, x2, y2, score, classId])
                val numDetections = d1
                val rawArray = FloatArray(numDetections * 6)
                outputBuffer.get(rawArray)

                for (i in 0 until numDetections) {
                    val offset = i * 6
                    val score = rawArray[offset + 4]
                    if (score >= confidenceThreshold) {
                        val x1 = rawArray[offset]
                        val y1 = rawArray[offset + 1]
                        val x2 = rawArray[offset + 2]
                        val y2 = rawArray[offset + 3]
                        val classId = rawArray[offset + 5].toInt()
                        candidates.add(Candidate(classId, score, x1, y1, x2, y2))
                    }
                }
            }
        }

        // Apply Non-Maximum Suppression (NMS)
        val nmsResults = applyNms(candidates)

        // Map bounding box coordinates back to original image space
        return nmsResults.map { candidate ->
            val origX1 = max(0f, (candidate.x1 - letterboxInfo.padX) / letterboxInfo.scale)
            val origY1 = max(0f, (candidate.y1 - letterboxInfo.padY) / letterboxInfo.scale)
            val origX2 = min(letterboxInfo.originalWidth.toFloat(), (candidate.x2 - letterboxInfo.padX) / letterboxInfo.scale)
            val origY2 = min(letterboxInfo.originalHeight.toFloat(), (candidate.y2 - letterboxInfo.padY) / letterboxInfo.scale)

            val label = if (candidate.classId in classLabels.indices) {
                classLabels[candidate.classId]
            } else {
                "Defect #${candidate.classId}"
            }

            Detection(
                classId = candidate.classId,
                label = label,
                confidence = candidate.confidence,
                boundingBox = RectF(origX1, origY1, origX2, origY2)
            )
        }
    }

    /**
     * Class-wise Non-Maximum Suppression (NMS)
     */
    private fun applyNms(candidates: List<Candidate>): List<Candidate> {
        if (candidates.isEmpty()) return emptyList()

        // Group by class to avoid suppressing different defects that co-occur
        val groupedByClass = candidates.groupBy { it.classId }
        val finalDetections = mutableListOf<Candidate>()

        for ((_, classCandidates) in groupedByClass) {
            val sorted = classCandidates.sortedByDescending { it.confidence }.toMutableList()

            while (sorted.isNotEmpty()) {
                val best = sorted.removeAt(0)
                finalDetections.add(best)

                val iterator = sorted.iterator()
                while (iterator.hasNext()) {
                    val other = iterator.next()
                    if (calculateIoU(best, other) >= iouThreshold) {
                        iterator.remove()
                    }
                }
            }
        }

        return finalDetections
    }

    /**
     * Computes Intersection over Union (IoU) between two bounding boxes.
     */
    private fun calculateIoU(a: Candidate, b: Candidate): Float {
        val interX1 = max(a.x1, b.x1)
        val interY1 = max(a.y1, b.y1)
        val interX2 = min(a.x2, b.x2)
        val interY2 = min(a.y2, b.y2)

        val interWidth = max(0f, interX2 - interX1)
        val interHeight = max(0f, interY2 - interY1)
        val interArea = interWidth * interHeight

        val areaA = max(0f, a.x2 - a.x1) * max(0f, a.y2 - a.y1)
        val areaB = max(0f, b.x2 - b.x1) * max(0f, b.y2 - b.y1)
        val unionArea = areaA + areaB - interArea

        return if (unionArea <= 0f) 0f else interArea / unionArea
    }
}
