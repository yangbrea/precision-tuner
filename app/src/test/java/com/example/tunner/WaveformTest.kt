package com.example.tunner

import com.example.tunner.audio.downsampleWaveform
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
    fun fixedGainScalesQuietSignal() {
        // 0.05 * 6 = 0.3
        val w = downsampleWaveform(constantBuffer(0.05f), 256)
        val peak = w.maxOf { abs(it) }
        assertTrue("peak=$peak should be ~0.3", peak in 0.25f..0.35f)
    }

    @Test
    fun fixedGainClipsToUnity() {
        // 0.9 * 6 = 5.4 -> clamped to 1.0
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
