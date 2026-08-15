package com.example.tunner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Fixed dark theme — tuners are usually used in low light / on stage.
 *
 * @param accent the primary accent color (the "in tune" color); defaults to the
 *               green used throughout the app.
 */
@Composable
fun TunerTheme(
    accent: Color = TunerPrimary,
    content: @Composable () -> Unit,
) {
    val colors = darkColorScheme(
        primary = accent,
        onPrimary = Color(0xFF00391B),
        secondary = TunerAccent,
        onSecondary = Color(0xFF3E2E00),
        background = TunerBackground,
        onBackground = TunerOnDark,
        surface = TunerSurface,
        onSurface = TunerOnDark,
        surfaceVariant = TunerSurfaceVariant,
        onSurfaceVariant = TunerOnDarkMuted,
    )
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
