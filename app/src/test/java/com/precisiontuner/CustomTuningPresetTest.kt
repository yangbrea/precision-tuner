package com.precisiontuner

import com.precisiontuner.tuning.CustomTuningPreset
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomTuningPresetTest {
    @Test
    fun `preset uses namespaced id and preserves string order`() {
        val preset = CustomTuningPreset("abc", "开放 D", "guitar", listOf(62, 57, 54, 50, 45, 38))

        val tuning = preset.toTuning()

        assertEquals("custom:abc", tuning.id)
        assertEquals("开放 D", tuning.name)
        assertEquals(listOf(62, 57, 54, 50, 45, 38), tuning.strings.map { it.midi })
        assertEquals(listOf(1, 2, 3, 4, 5, 6), tuning.strings.map { it.number })
        assertEquals(listOf("D4", "A3", "F#3", "D3", "A2", "D2"), tuning.strings.map { it.fullNote })
    }
}
