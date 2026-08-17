package com.precisiontuner

import com.precisiontuner.audio.downsampleWaveform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WaveformTest {

    private fun constantBuffer(value: Float): ShortArray =
        ShortArray(4096) { (value * 32767f).toInt().toShort() }

    @Test
    fun returnsRequestedPoints() {
        assertEquals(256, downsampleWaveform(ShortArray(4096), 256).size)
        assertEquals(128, downsampleWaveform(ShortArray(4096), 128).size)
    }

    @Test
    fun fixedGainScalesSignal() {
        // 0.01 * 24 = 0.24
        val w = downsampleWaveform(constantBuffer(0.01f), 256)
        val peak = w.maxOf { abs(it) }
        assertTrue("peak=$peak should be ~0.24", peak in 0.20f..0.28f)
    }

    @Test
    fun fixedGainClipsToUnity() {
        // 0.9 * 24 = 21.6 -> clamped to 1.0
        val w = downsampleWaveform(constantBuffer(0.9f), 256)
        assertTrue(w.all { it in -1f..1f })
        assertTrue(w.maxOf { abs(it) } <= 1f)
    }

    @Test
    fun silenceStaysFlat() {
        val w = downsampleWaveform(ShortArray(4096), 256)
        assertTrue(w.all { it == 0f })
    }

    @Test
    fun emptyReturnsEmpty() {
        assertTrue(downsampleWaveform(ShortArray(0), 256).isEmpty())
    }
}
