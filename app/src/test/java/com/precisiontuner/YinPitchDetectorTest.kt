package com.precisiontuner

import com.precisiontuner.pitch.YinPitchDetector
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sin

class YinPitchDetectorTest {

    private val detector = YinPitchDetector()
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
    fun exposesMultiplePeriodicCandidatesForTracking() {
        val candidates = detector.detectCandidates(sineBuffer(164.81), sampleRate, maxCandidates = 5)
        assertTrue(candidates.isNotEmpty())
        assertTrue(candidates.size <= 5)
        assertTrue(candidates.any { candidate ->
            val cents = 1200.0 * ln(candidate.frequency / 164.81) / ln(2.0)
            abs(cents) <= 3.0
        })
        assertTrue(candidates.all { it.probability in 0.0..1.0 && it.voicedProbability in 0.0..1.0 })
    }
}
