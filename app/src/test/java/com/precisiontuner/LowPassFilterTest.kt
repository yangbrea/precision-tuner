package com.precisiontuner

import com.precisiontuner.audio.LowPassFilter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class LowPassFilterTest {

    private val sampleRate = 44100
    private val filter = LowPassFilter(sampleRate.toDouble())

    private fun sine(freq: Double, samples: Int = 16384, amplitude: Double = 0.5): ShortArray =
        ShortArray(samples) { i ->
            (amplitude * sin(2.0 * PI * freq * i / sampleRate) * 32767.0).toInt().toShort()
        }

    private fun rms(buf: ShortArray): Double {
        var sum = 0.0
        for (v in buf) {
            val x = v.toDouble()
            sum += x * x
        }
        return sqrt(sum / buf.size)
    }

    @Test
    fun bypassWhenStrengthZero() {
        val input = sine(440.0)
        val copy = input.copyOf()
        filter.process(copy, 0f)
        assertArrayEquals("strength=0 must not modify the buffer", input, copy)
    }

    @Test
    fun attenuatesHighFrequencyMoreThanLow() {
        val lowIn = sine(82.0)
        val highIn = sine(5000.0)

        val lowOut = lowIn.copyOf()
        val highOut = highIn.copyOf()
        filter.process(lowOut, 1f)
        filter.process(highOut, 1f)

        val lowRatio = rms(lowOut) / rms(lowIn)
        val highRatio = rms(highOut) / rms(highIn)

        assertTrue("82 Hz should pass almost intact, ratio=$lowRatio", lowRatio > 0.85)
        assertTrue("5 kHz should be strongly attenuated, ratio=$highRatio", highRatio < 0.2)
    }

    @Test
    fun defaultStrengthNowAttenuatesHighHarmonics() {
        // Default 0.5 maps to ~2.2 kHz cutoff (logarithmic curve): a 5 kHz
        // harmonic is clearly attenuated while the 500 Hz region still passes.
        val lowIn = sine(500.0)
        val highIn = sine(5000.0)

        val lowOut = lowIn.copyOf()
        val highOut = highIn.copyOf()
        LowPassFilter(sampleRate.toDouble()).process(lowOut, 0.5f)
        LowPassFilter(sampleRate.toDouble()).process(highOut, 0.5f)

        val lowRatio = rms(lowOut) / rms(lowIn)
        val highRatio = rms(highOut) / rms(highIn)

        assertTrue("500 Hz should pass at default strength, ratio=$lowRatio", lowRatio > 0.85)
        assertTrue("5 kHz should be attenuated at default strength, ratio=$highRatio", highRatio < 0.5)
    }
}
