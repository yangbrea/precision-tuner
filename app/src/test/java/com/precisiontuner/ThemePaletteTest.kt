package com.precisiontuner

import androidx.compose.ui.graphics.Color
import com.precisiontuner.settings.ThemeMode
import com.precisiontuner.settings.ThemePreset
import com.precisiontuner.ui.theme.themePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class ThemePaletteTest {
    @Test
    fun paletteModesMatchPresetDeclarations() {
        ThemePreset.entries.filter { it != ThemePreset.CLASSIC }.forEach { preset ->
            val expectedDark = preset.lockedMode == ThemeMode.DARK
            assertEquals(preset.label, expectedDark, themePalette(preset).dark)
        }
    }

    @Test
    fun presetTextAndControlColorsMeetNormalTextContrast() {
        ThemePreset.entries.filter { it != ThemePreset.CLASSIC }.forEach { preset ->
            val palette = themePalette(preset)
            assertContrast(preset, "background", palette.onBackground, palette.background)
            assertContrast(preset, "surface", palette.onBackground, palette.surface)
            assertContrast(preset, "primary", palette.onPrimary, palette.primary)
            assertContrast(preset, "secondary", palette.onSecondary, palette.secondary)
        }
    }

    private fun assertContrast(
        preset: ThemePreset,
        role: String,
        foreground: Color,
        background: Color,
    ) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("${preset.name} $role contrast was $ratio", ratio >= 4.5)
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val firstLum = luminance(first)
        val secondLum = luminance(second)
        return (max(firstLum, secondLum) + 0.05) / (min(firstLum, secondLum) + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun linear(channel: Float): Double {
            val value = channel.toDouble()
            return if (value <= 0.04045) value / 12.92
            else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(color.red) +
            0.7152 * linear(color.green) +
            0.0722 * linear(color.blue)
    }
}
