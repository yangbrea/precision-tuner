package com.precisiontuner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * App theme. [accent] is the "in tune" color (configurable); [darkTheme] picks
 * the dark or light palette.
 */
@Composable
fun TunerTheme(
    accent: Color = TunerPrimary,
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color(0xFF00391B),
            secondary = TunerAccent,
            onSecondary = Color(0xFF3E2E00),
            background = DarkBackground,
            onBackground = DarkOnBackground,
            surface = DarkSurface,
            onSurface = DarkOnBackground,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = DarkOnSurfaceVariant,
            outlineVariant = DarkOutlineVariant,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color(0xFF00391B),
            secondary = TunerAccent,
            onSecondary = Color(0xFF3E2E00),
            background = LightBackground,
            onBackground = LightOnBackground,
            surface = LightSurface,
            onSurface = LightOnBackground,
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = LightOnSurfaceVariant,
            outlineVariant = LightOutlineVariant,
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}
