package com.precisiontuner.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.precisiontuner.settings.ThemePreset

internal data class ThemePalette(
    val dark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val onBackground: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
)

internal fun themePalette(preset: ThemePreset): ThemePalette = when (preset) {
    ThemePreset.MIDNIGHT -> ThemePalette(
        true, Color(0xFF0B1220), Color(0xFF131E30), Color(0xFF1D2A40),
        Color(0xFF64B5F6), Color(0xFF082032), Color(0xFFF6C177), Color(0xFF332407),
        Color(0xFFE6EDF7), Color(0xFFB5C0D1), Color(0xFF64748B),
    )
    ThemePreset.FOREST -> ThemePalette(
        true, Color(0xFF0D1712), Color(0xFF15231B), Color(0xFF213329),
        Color(0xFF6FCF97), Color(0xFF082116), Color(0xFFE9C46A), Color(0xFF302400),
        Color(0xFFE7F2EA), Color(0xFFB7C7BB), Color(0xFF657569),
    )
    ThemePreset.GRAPHITE_ROSE -> ThemePalette(
        true, Color(0xFF151217), Color(0xFF211B23), Color(0xFF302832),
        Color(0xFFF48FB1), Color(0xFF32101E), Color(0xFFB39DDB), Color(0xFF251333),
        Color(0xFFF5EBF1), Color(0xFFCBBEC7), Color(0xFF756874),
    )
    ThemePreset.WARM_PAPER -> ThemePalette(
        false, Color(0xFFF7F1E7), Color(0xFFFFFBF5), Color(0xFFECE1D2),
        Color(0xFFA64B2A), Color.White, Color(0xFF8A6D3B), Color.White,
        Color(0xFF2D2118), Color(0xFF66594E), Color(0xFF9A8C7D),
    )
    ThemePreset.OCEAN -> ThemePalette(
        false, Color(0xFFEEF7F8), Color.White, Color(0xFFDCECEF),
        Color(0xFF007C91), Color.White, Color(0xFF3D6F73), Color.White,
        Color(0xFF10282C), Color(0xFF50676A), Color(0xFF789094),
    )
    ThemePreset.LAVENDER -> ThemePalette(
        false, Color(0xFFF5F1FA), Color(0xFFFDFBFF), Color(0xFFE8E0F0),
        Color(0xFF6D4DB3), Color.White, Color(0xFF8E5A78), Color.White,
        Color(0xFF271F30), Color(0xFF675D6D), Color(0xFF908398),
    )
    ThemePreset.CLASSIC -> error("Classic colors are built from the user's settings")
}

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
    themePreset: ThemePreset = ThemePreset.CLASSIC,
    content: @Composable () -> Unit,
) {
    val targetScheme = remember(accent, darkTheme, themePreset) {
        if (themePreset == ThemePreset.CLASSIC) {
            if (darkTheme) buildDarkScheme(accent) else buildLightScheme(accent)
        } else {
            buildPresetScheme(themePalette(themePreset))
        }
    }
    var startScheme by remember { mutableStateOf(targetScheme) }
    var endScheme by remember { mutableStateOf(targetScheme) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(targetScheme) {
        startScheme = lerpColorScheme(startScheme, endScheme, progress.value)
        endScheme = targetScheme
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = THEME_TRANSITION_MS))
    }
    MaterialTheme(
        colorScheme = lerpColorScheme(startScheme, endScheme, progress.value),
        content = content,
    )
}

private fun buildPresetScheme(palette: ThemePalette): ColorScheme {
    val containerAmount = if (palette.dark) 0.28f else 0.14f
    val error = if (palette.dark) Color(0xFFFFB4AB) else Color(0xFFBA1A1A)
    val onError = if (palette.dark) Color(0xFF690005) else Color.White
    val errorContainer = if (palette.dark) Color(0xFF93000A) else Color(0xFFFFDAD6)
    val onErrorContainer = if (palette.dark) Color(0xFFFFDAD6) else Color(0xFF410002)
    return ColorScheme(
        primary = palette.primary,
        onPrimary = palette.onPrimary,
        primaryContainer = blend(palette.surface, palette.primary, containerAmount),
        onPrimaryContainer = palette.onBackground,
        secondary = palette.secondary,
        onSecondary = palette.onSecondary,
        secondaryContainer = blend(palette.surface, palette.secondary, containerAmount),
        onSecondaryContainer = palette.onBackground,
        tertiary = blend(palette.primary, palette.secondary, 0.50f),
        onTertiary = palette.onPrimary,
        tertiaryContainer = blend(palette.surfaceVariant, palette.secondary, containerAmount),
        onTertiaryContainer = palette.onBackground,
        background = palette.background,
        onBackground = palette.onBackground,
        surface = palette.surface,
        onSurface = palette.onBackground,
        surfaceVariant = palette.surfaceVariant,
        onSurfaceVariant = palette.onSurfaceVariant,
        surfaceTint = palette.primary,
        inverseSurface = palette.onBackground,
        inverseOnSurface = palette.background,
        inversePrimary = palette.primary,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        outline = palette.outline,
        outlineVariant = blend(palette.surfaceVariant, palette.outline, 0.52f),
        scrim = Color.Black,
        surfaceBright = if (palette.dark) blend(palette.surfaceVariant, palette.onBackground, 0.10f)
            else palette.surface,
        surfaceDim = if (palette.dark) palette.background else palette.surfaceVariant,
        surfaceContainer = palette.surface,
        surfaceContainerHigh = blend(palette.surface, palette.surfaceVariant, 0.55f),
        surfaceContainerHighest = palette.surfaceVariant,
        surfaceContainerLow = blend(palette.background, palette.surface, 0.55f),
        surfaceContainerLowest = if (palette.dark) blend(palette.background, Color.Black, 0.16f)
            else Color.White,
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
