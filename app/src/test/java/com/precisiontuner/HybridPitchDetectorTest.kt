package com.precisiontuner

import com.precisiontuner.pitch.HybridPitchDetector
import com.precisiontuner.pitch.OnlinePitchTracker
import com.precisiontuner.pitch.Spectrum
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

    private fun instrumentBuffer(
        frequency: Double,
        fundamental: Double = 0.18,
        second: Double = 0.55,
        third: Double = 0.18,
    ): ShortArray = ShortArray(frameSize) { i ->
        val t = 2.0 * PI * frequency * i / sampleRate
        val attack = (i / 300.0).coerceAtMost(1.0)
        val value = attack * (
            fundamental * sin(t) + second * sin(2.0 * t) + third * sin(3.0 * t)
        )
        (value * 32767.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    private fun assertPitch(buffer: ShortArray, expected: Double, toleranceCents: Double) {
        val pitch = detector.detect(buffer, sampleRate)
        assertNotNull("no pitch detected for $expected Hz", pitch)
        val cents = 1200.0 * ln(pitch!!.frequency / expected) / ln(2.0)
        assertTrue("expected $expected, got ${pitch.frequency} ($cents cents)", abs(cents) <= toleranceCents)
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
    fun detectsNoisyTone() {
        val freq = 196.0
        val signal = sineBuffer(freq, amplitude = 0.5)
        val rnd = Random(7)
        // Add uniform noise at ~0.16 amplitude → SNR ≈ 10 dB.
        for (i in signal.indices) {
            val noise = rnd.nextInt(10486) - 5243
            signal[i] = (signal[i] + noise)
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        val pitch = detector.detect(signal, sampleRate)
        assertNotNull("no pitch detected for noisy $freq Hz tone", pitch)
        val cents = 1200.0 * (ln(pitch!!.frequency / freq) / ln(2.0))
        assertTrue("noisy tone error too large: ${"%.1f".format(cents)} cents", abs(cents) <= 8.0)
    }

    @Test
    fun strongHarmonicsDoNotCauseOctaveErrors() {
        val frequencies = listOf(55.0, 73.42, 82.41, 164.81, 329.63, 659.25)
        frequencies.forEach { assertPitch(instrumentBuffer(it), it, 3.0) }
        // A 4096-sample frame contains fewer than four periods of E1; keep a
        // still-tight bound without doubling latency for every other string.
        assertPitch(instrumentBuffer(41.20), 41.20, 5.0)
    }

    @Test
    fun e4PrimaryPeriodSuppressesItsA2TriplePeriod() {
        val e4 = 329.63
        val candidates = detector.detectCandidates(sineBuffer(e4), sampleRate, maxCandidates = 8)
        assertTrue(candidates.any { abs(1200.0 * ln(it.frequency / e4) / ln(2.0)) < 5.0 })
        assertTrue(
            "E4 candidates contained false A2: ${candidates.map { it.frequency }}",
            candidates.none { abs(1200.0 * ln(it.frequency / (e4 / 3.0)) / ln(2.0)) < 45.0 },
        )
    }

    @Test
    fun realA2IsNotSuppressedByItsThirdHarmonic() {
        val a2 = 110.0
        val candidates = detector.detectCandidates(
            instrumentBuffer(a2, fundamental = 0.32, second = 0.18, third = 0.45),
            sampleRate,
            maxCandidates = 8,
        )
        assertTrue(
            "real A2 missing: ${candidates.map { it.frequency }}",
            candidates.any { abs(1200.0 * ln(it.frequency / a2) / ln(2.0)) < 8.0 },
        )
    }

    @Test
    fun fluctuatingE4CannotEstablishA2TrackerPath() {
        val e4 = 329.63
        val tracker = OnlinePitchTracker(3)
        val outputs = (0 until 18).mapNotNull { frame ->
            val amplitude = 0.12 + (frame % 6) * 0.055
            val tracked = tracker.submit(
                detector.detectCandidates(sineBuffer(e4, amplitude), sampleRate, maxCandidates = 5),
            )
            tracked?.pitch?.frequency
        }
        assertTrue(outputs.isNotEmpty())
        assertTrue(
            "tracker left E4: $outputs",
            outputs.all { abs(1200.0 * ln(it / e4) / ln(2.0)) < 8.0 },
        )
    }

    @Test
    fun e3WithLowFrequencyLeakDoesNotDropToE2() {
        val e3 = 164.81
        val buffer = instrumentBuffer(e3, fundamental = 0.42, second = 0.28, third = 0.12)
        for (i in buffer.indices) {
            val leak = 0.08 * sin(2.0 * PI * (e3 / 2.0) * i / sampleRate)
            buffer[i] = (buffer[i] + leak * 32767.0).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        assertPitch(buffer, e3, 3.0)
    }

    @Test
    fun e3SurvivesIntermittentImpactNoise() {
        val e3 = 164.81
        val buffer = instrumentBuffer(e3, fundamental = 0.40, second = 0.30, third = 0.12)
        for (i in 250 until buffer.size step 700) {
            buffer[i] = if ((i / 700) % 2 == 0) Short.MAX_VALUE else Short.MIN_VALUE
        }
        assertPitch(buffer, e3, 8.0)
    }

    @Test
    fun bassE2SurvivesPowerLineHum() {
        val e2 = 82.41
        val buffer = instrumentBuffer(e2, fundamental = 0.42, second = 0.30, third = 0.12)
        for (i in buffer.indices) {
            val hum = 0.08 * sin(2.0 * PI * 50.0 * i / sampleRate) +
                0.04 * sin(2.0 * PI * 100.0 * i / sampleRate)
            buffer[i] = (buffer[i] + hum * 32767.0).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        assertPitch(buffer, e2, 8.0)
    }

    @Test
    fun lockedE3IgnoresItsE2SubharmonicCandidate() {
        val e3 = 164.81
        val pitch = detector.detectLocked(instrumentBuffer(e3), sampleRate, e3)
        assertNotNull(pitch)
        val cents = 1200.0 * ln(pitch!!.frequency / e3) / ln(2.0)
        assertTrue("locked E3 was ${pitch.frequency}", abs(cents) <= 3.0)
    }

    @Test
    fun actualE2IsStrongerThanFalseE3HarmonicLock() {
        val e2 = 82.41
        val e3 = 164.81
        val buffer = instrumentBuffer(e2, fundamental = 0.42, second = 0.30, third = 0.12)
        val broad = detector.detect(buffer, sampleRate)
        val locked = detector.detectLocked(buffer, sampleRate, e3)
        assertNotNull(broad)
        val cents = 1200.0 * ln(broad!!.frequency / e2) / ln(2.0)
        assertTrue("broad E2 was ${broad.frequency}", abs(cents) <= 3.0)
        assertTrue(
            "false E3 lock was too confident: locked=${locked?.confidence}, broad=${broad.confidence}",
            locked == null || locked.confidence < broad.confidence - 0.05,
        )
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
