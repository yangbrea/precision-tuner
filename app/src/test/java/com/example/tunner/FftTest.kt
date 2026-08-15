package com.example.tunner

import com.example.tunner.pitch.Fft
import com.example.tunner.pitch.hann
import com.example.tunner.pitch.interpolatePeakLog
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class FftTest {

    private val n = 4096
    private val sampleRate = 44100

    private fun hannSine(freq: Double): DoubleArray {
        val win = hann(n)
        return DoubleArray(n) { i -> 0.5 * sin(2.0 * PI * freq * i / sampleRate) * win[i] }
    }

    private fun peakFrequency(signal: DoubleArray): Double {
        val re = signal.copyOf()
        val im = DoubleArray(n)
        Fft.transform(re, im)
        val half = n / 2
        val mag = DoubleArray(half) { i -> sqrt(re[i] * re[i] + im[i] * im[i]) }
        var peak = 1
        for (b in 2 until half - 1) {
            if (mag[b] > mag[peak]) peak = b
        }
        return interpolatePeakLog(mag, peak) * sampleRate / n
    }

    @Test
    fun peakAtBinCenter() {
        val f = 10.0 * sampleRate / n // exactly on bin 10 ≈ 107.67 Hz
        assertEquals(f, peakFrequency(hannSine(f)), 2.0)
    }

    @Test
    fun peakAtNonBinCenter() {
        assertEquals(440.0, peakFrequency(hannSine(440.0)), 5.0)
    }

    @Test
    fun peakAtLowGuitarString() {
        assertEquals(82.41, peakFrequency(hannSine(82.41)), 5.0)
    }
}
