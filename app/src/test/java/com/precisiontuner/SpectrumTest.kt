package com.precisiontuner

import com.precisiontuner.pitch.Spectrum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class SpectrumTest {

    @Test
    fun globalPeakEqualsDisplayCeiling() {
        val magnitude = DoubleArray(512)
        magnitude[100] = 1.0 // 100 Hz, the frame peak
        val bands = Spectrum.build(magnitude, sampleRate = 4096, fftSize = 4096)
        assertEquals(Spectrum.DISPLAY_CEILING, bands.maxOrNull()!!, 1e-6f)
    }

    @Test
    fun halfPowerBandIsHalfOfCeiling() {
        val magnitude = DoubleArray(512)
        magnitude[100] = 1.0         // peak
        magnitude[200] = sqrt(0.5)   // -3 dB (half power)
        val bands = Spectrum.build(magnitude, sampleRate = 4096, fftSize = 4096)
        val idx200 = bandIndex(200.0)
        assertEquals(0.5f * Spectrum.DISPLAY_CEILING, bands[idx200], 0.001f)
        assertTrue(bands.maxOrNull()!! > bands[idx200])
    }

    @Test
    fun quietBandStaysNearZero() {
        val magnitude = DoubleArray(512)
        magnitude[100] = 1.0
        magnitude[300] = 0.0001 // 1e-8 power → ≈9.4e-9 height
        val bands = Spectrum.build(magnitude, sampleRate = 4096, fftSize = 4096)
        assertTrue(bands[bandIndex(300.0)] < 0.001f)
    }

    @Test
    fun harmonicStrongerThanFundamentalUsesGlobalPeak() {
        val magnitude = DoubleArray(512)
        magnitude[100] = 0.8 // fundamental (weaker)
        magnitude[200] = 1.0 // harmonic (global peak)
        val bands = Spectrum.build(magnitude, sampleRate = 4096, fftSize = 4096)
        val idxFund = bandIndex(100.0)
        val idxHarm = bandIndex(200.0)
        // The reference is the frame-wide max (the harmonic), not the fundamental.
        assertEquals(Spectrum.DISPLAY_CEILING, bands[idxHarm], 1e-6f)
        assertEquals(0.64f * Spectrum.DISPLAY_CEILING, bands[idxFund], 0.001f)
    }

    @Test
    fun equalStrongPeaksShowEqualHeight() {
        val magnitude = DoubleArray(512)
        magnitude[100] = 1.0
        magnitude[200] = 1.0 // genuinely equal power
        val bands = Spectrum.build(magnitude, sampleRate = 4096, fftSize = 4096)
        assertEquals(Spectrum.DISPLAY_CEILING, bands[bandIndex(100.0)], 1e-6f)
        assertEquals(Spectrum.DISPLAY_CEILING, bands[bandIndex(200.0)], 1e-6f)
    }

    @Test
    fun silentInputProducesAllZeroBands() {
        val bands = Spectrum.build(DoubleArray(512), sampleRate = 4096, fftSize = 4096)
        assertTrue(bands.all { it == 0f })
    }

    private fun bandIndex(freqHz: Double): Int =
        (freqHz / Spectrum.MAX_HZ * Spectrum.BANDS).toInt().coerceIn(0, Spectrum.BANDS - 1)
}
