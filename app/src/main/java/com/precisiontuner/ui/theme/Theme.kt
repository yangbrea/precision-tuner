package com.precisiontuner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * App theme. [accent] is the "in tune" color (configurable); [darkTheme] picks
 * the dark or light palette.
 *
 * Besides [accent], the surface/container slots are explicitly overridden so
 * Material 3 components (NavigationBar indicator & container, Slider inactive
 * track, ModalBottomSheet, …) follow the app's neutral palette and the accent
 * instead of the default purple-tinted baseline containers.
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
            surfaceContainerLowest = Color(0xFF0B0B0D),
            surfaceContainerLow = Color(0xFF141418),
            surfaceContainer = DarkSurface,
            surfaceContainerHigh = Color(0xFF202026),
            surfaceContainerHighest = DarkSurfaceVariant,
            secondaryContainer = blend(DarkSurface, accent, 0.25f),
            onSecondaryContainer = blend(DarkOnBackground, accent, 0.15f),
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
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF2F2F5),
            surfaceContainer = LightBackground,
            surfaceContainerHigh = Color(0xFFEFEFF2),
            surfaceContainerHighest = LightSurfaceVariant,
            secondaryContainer = blend(Color.White, accent, 0.14f),
            onSecondaryContainer = blend(LightOnBackground, accent, 0.30f),
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Mixes [accent] into [base] by [amount] (0 = pure base, 1 = pure accent). */
private fun blend(base: Color, accent: Color, amount: Float): Color =
    lerp(base, accent, amount)
