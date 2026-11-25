package com.industry4.inspection.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.industry4.inspection.data.models.InspectionStatus
import com.industry4.inspection.ui.theme.DefectAlertRed
import com.industry4.inspection.ui.theme.DefectLightBg
import com.industry4.inspection.ui.theme.PassLightBg
import com.industry4.inspection.ui.theme.PassNormalGreen
import com.industry4.inspection.ui.theme.WarningAmber
import com.industry4.inspection.ui.theme.WarningLightBg

@Composable
fun StatusBadge(
    status: InspectionStatus,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, borderColor) = when (status) {
        InspectionStatus.DEFECT_DETECTED -> Triple(DefectLightBg, DefectAlertRed, DefectAlertRed)
        InspectionStatus.NO_TRAINED_DEFECT_DETECTED -> Triple(PassLightBg, PassNormalGreen, PassNormalGreen)
        InspectionStatus.ERROR -> Triple(WarningLightBg, WarningAmber, WarningAmber)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = status.displayName,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
