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
    fun loudSignalScaledToTarget() {
        val w = downsampleWaveform(constantBuffer(0.9f), 256)
        val peak = w.maxOf { abs(it) }
        assertTrue("peak=$peak should be ~0.9", peak in 0.8f..1.0f)
    }

    @Test
    fun quietSignalAmplified() {
        val w = downsampleWaveform(constantBuffer(0.05f), 256)
        val peak = w.maxOf { abs(it) }
        assertTrue("peak=$peak should be amplified to ~0.9", peak in 0.8f..1.0f)
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
