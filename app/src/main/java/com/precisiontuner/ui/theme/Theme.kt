package com.precisiontuner.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * App theme. [accent] is the "in tune" color (configurable); [darkTheme] picks
 * the dark or light palette.
 *
 * Switching [darkTheme] animates a gradual color transition (lerp between the
 * dark and light color schemes) instead of snapping, so the whole UI fades
 * smoothly between modes. Besides [accent], the surface/container slots are
 * explicitly overridden so Material 3 components (NavigationBar indicator &
 * container, Slider inactive track, ModalBottomSheet, …) follow the app's
 * neutral palette and the accent instead of the default purple-tinted baseline.
 */
@Composable
fun TunerTheme(
    accent: Color = TunerPrimary,
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkColors = remember(accent) { buildDarkScheme(accent) }
    val lightColors = remember(accent) { buildLightScheme(accent) }
    val progress by animateFloatAsState(
        targetValue = if (darkTheme) 1f else 0f,
        animationSpec = tween(durationMillis = THEME_TRANSITION_MS),
        label = "themeModeTransition",
    )
    MaterialTheme(
        colorScheme = lerpColorScheme(darkColors, lightColors, progress),
        content = content,
    )
}

private fun buildDarkScheme(accent: Color): ColorScheme = darkColorScheme(
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

private fun buildLightScheme(accent: Color): ColorScheme = lightColorScheme(
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

/** Interpolates every color-scheme slot between the dark and light palettes. */
private fun lerpColorScheme(dark: ColorScheme, light: ColorScheme, t: Float): ColorScheme =
    ColorScheme(
        primary = lerp(dark.primary, light.primary, t),
        onPrimary = lerp(dark.onPrimary, light.onPrimary, t),
        primaryContainer = lerp(dark.primaryContainer, light.primaryContainer, t),
        onPrimaryContainer = lerp(dark.onPrimaryContainer, light.onPrimaryContainer, t),
        secondary = lerp(dark.secondary, light.secondary, t),
        onSecondary = lerp(dark.onSecondary, light.onSecondary, t),
        secondaryContainer = lerp(dark.secondaryContainer, light.secondaryContainer, t),
        onSecondaryContainer = lerp(dark.onSecondaryContainer, light.onSecondaryContainer, t),
        tertiary = lerp(dark.tertiary, light.tertiary, t),
        onTertiary = lerp(dark.onTertiary, light.onTertiary, t),
        tertiaryContainer = lerp(dark.tertiaryContainer, light.tertiaryContainer, t),
        onTertiaryContainer = lerp(dark.onTertiaryContainer, light.onTertiaryContainer, t),
        background = lerp(dark.background, light.background, t),
        onBackground = lerp(dark.onBackground, light.onBackground, t),
        surface = lerp(dark.surface, light.surface, t),
        onSurface = lerp(dark.onSurface, light.onSurface, t),
        surfaceVariant = lerp(dark.surfaceVariant, light.surfaceVariant, t),
        onSurfaceVariant = lerp(dark.onSurfaceVariant, light.onSurfaceVariant, t),
        surfaceTint = lerp(dark.surfaceTint, light.surfaceTint, t),
        inverseSurface = lerp(dark.inverseSurface, light.inverseSurface, t),
        inverseOnSurface = lerp(dark.inverseOnSurface, light.inverseOnSurface, t),
        inversePrimary = lerp(dark.inversePrimary, light.inversePrimary, t),
        error = lerp(dark.error, light.error, t),
        onError = lerp(dark.onError, light.onError, t),
        errorContainer = lerp(dark.errorContainer, light.errorContainer, t),
        onErrorContainer = lerp(dark.onErrorContainer, light.onErrorContainer, t),
        outline = lerp(dark.outline, light.outline, t),
        outlineVariant = lerp(dark.outlineVariant, light.outlineVariant, t),
        scrim = lerp(dark.scrim, light.scrim, t),
        surfaceBright = lerp(dark.surfaceBright, light.surfaceBright, t),
        surfaceDim = lerp(dark.surfaceDim, light.surfaceDim, t),
        surfaceContainer = lerp(dark.surfaceContainer, light.surfaceContainer, t),
        surfaceContainerHigh = lerp(dark.surfaceContainerHigh, light.surfaceContainerHigh, t),
        surfaceContainerHighest = lerp(dark.surfaceContainerHighest, light.surfaceContainerHighest, t),
        surfaceContainerLow = lerp(dark.surfaceContainerLow, light.surfaceContainerLow, t),
        surfaceContainerLowest = lerp(dark.surfaceContainerLowest, light.surfaceContainerLowest, t),
    )

/** Mixes [accent] into [base] by [amount] (0 = pure base, 1 = pure accent). */
private fun blend(base: Color, accent: Color, amount: Float): Color =
    lerp(base, accent, amount)

private const val THEME_TRANSITION_MS = 500
