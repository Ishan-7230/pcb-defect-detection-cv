package com.industry4.inspection.data.models

/**
 * Inspection classification statuses.
 * Per Industry 4.0 quality assurance principles:
 * We do not mark components as "Defect-Free"; instead, if no trained defect is detected above threshold,
 * the status is strictly NO_TRAINED_DEFECT_DETECTED.
 */
enum class InspectionStatus(val displayName: String) {
    DEFECT_DETECTED("DEFECT DETECTED"),
    NO_TRAINED_DEFECT_DETECTED("NO TRAINED DEFECT DETECTED"),
    ERROR("ERROR")
}
