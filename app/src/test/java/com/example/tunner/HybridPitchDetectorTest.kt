package com.example.tunner

import com.example.tunner.pitch.HybridPitchDetector
import com.example.tunner.pitch.Spectrum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sin

class HybridPitchDetectorTest {

    private val detector = HybridPitchDetector()
    private val sampleRate = 44100
    private val frameSize = 4096

    private fun sineBuffer(frequency: Double, amplitude: Double = 0.5): ShortArray {
        val buf = ShortArray(frameSize)
        for (i in buf.indices) {
            val v = amplitude * sin(2.0 * PI * frequency * i / sampleRate)
            buf[i] = (v * 32767.0).toInt().toShort()
        }
        return buf
    }

    private fun assertDetected(expectedFreq: Double, toleranceCents: Double) {
        val pitch = detector.detect(sineBuffer(expectedFreq), sampleRate)
        assertNotNull("no pitch detected for $expectedFreq Hz", pitch)
        val cents = 1200.0 * (ln(pitch!!.frequency / expectedFreq) / ln(2.0))
        assertTrue(
            "expected $expectedFreq Hz, got ${pitch.frequency} Hz (${"%.2f".format(cents)} cents off)",
            abs(cents) <= toleranceCents
        )
    }

    @Test
    fun detectsGuitarStrings() {
        val targets = listOf(82.41, 110.0, 146.83, 196.0, 246.94, 329.63)
        for (f in targets) assertDetected(f, toleranceCents = 5.0)
    }

    @Test
    fun detectsReferenceA4() {
        assertDetected(440.0, toleranceCents = 5.0)
    }

    @Test
    fun detectsHigherTone() {
        assertDetected(1000.0, toleranceCents = 5.0)
    }

    @Test
    fun rejectsSilence() {
        assertNull(detector.detect(ShortArray(frameSize), sampleRate))
    }

    @Test
    fun rejectsWhiteNoise() {
        val rnd = Random(42)
        val noise = ShortArray(frameSize) { (rnd.nextInt(65536) - 32768).toShort() }
        assertNull(detector.detect(noise, sampleRate))
    }

    @Test
    fun exposesSpectrumBands() {
        detector.detect(sineBuffer(440.0), sampleRate)
        val spectrum = detector.lastSpectrum
        assertNotNull(spectrum)
        assertEquals(Spectrum.BANDS, spectrum!!.size)
        // Normalized so no band exceeds 1.0.
        assertTrue(spectrum.all { it in 0f..1f })
    }
}
