package com.industry4.inspection.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = IndustrialPrimary,
    onPrimary = IndustrialSurface,
    secondary = IndustrialSecondary,
    onSecondary = IndustrialSurface,
    background = IndustrialBackground,
    onBackground = IndustrialTextPrimary,
    surface = IndustrialSurface,
    onSurface = IndustrialTextPrimary,
    surfaceVariant = IndustrialSurfaceVariant,
    onSurfaceVariant = IndustrialTextSecondary,
    outline = IndustrialBorder
)

@Composable
fun Industry4InspectionTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
