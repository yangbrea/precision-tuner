package com.precisiontuner

import com.precisiontuner.settings.AccentColor
import com.precisiontuner.settings.AppSettings
import com.precisiontuner.settings.ThemeMode
import com.precisiontuner.settings.ThemePreset
import com.precisiontuner.settings.effectiveThemeMode
import com.precisiontuner.settings.parseThemePreset
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemePresetSettingsTest {
    @Test
    fun legacyAndUnknownValuesFallBackToClassic() {
        assertEquals(ThemePreset.CLASSIC, parseThemePreset(null))
        assertEquals(ThemePreset.CLASSIC, parseThemePreset("UNKNOWN_PRESET"))
    }

    @Test
    fun systemPresetsLockTheirDeclaredMode() {
        ThemePreset.entries.filter { it != ThemePreset.CLASSIC }.forEach { preset ->
            val opposite = if (preset.lockedMode == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
            val settings = AppSettings(themeMode = opposite, themePreset = preset)
            assertEquals(preset.lockedMode, settings.effectiveThemeMode)
        }
    }

    @Test
    fun returningToClassicRestoresModeAndAccent() {
        val saved = AppSettings(
            themeMode = ThemeMode.DARK,
            accent = AccentColor.PINK,
            themePreset = ThemePreset.OCEAN,
        )
        val classic = saved.copy(themePreset = ThemePreset.CLASSIC)
        assertEquals(ThemeMode.DARK, classic.effectiveThemeMode)
        assertEquals(AccentColor.PINK, classic.accent)
    }
}
