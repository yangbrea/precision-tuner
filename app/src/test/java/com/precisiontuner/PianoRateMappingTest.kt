package com.precisiontuner

import com.precisiontuner.audio.PianoReferenceEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PianoRateMappingTest {

    @Test
    fun `every midi in the piano range maps to a rate within the playback bounds`() {
        for (midi in 21..108) {
            val rate = PianoReferenceEngine.sampleRateForMidi(midi)
            assertTrue("midi=$midi rate=$rate", rate >= PianoReferenceEngine.MIN_RATE)
            assertTrue("midi=$midi rate=$rate", rate <= PianoReferenceEngine.MAX_RATE)
        }
    }

    @Test
    fun `sample index points at the nearest keycenter`() {
        for (midi in 21..108) {
            val index = PianoReferenceEngine.sampleIndexForMidi(midi)
            val keycenter = PianoReferenceEngine.KEYCENTERS[index]
            val distance = abs(keycenter - midi)
            assertTrue("midi=$midi nearest=$keycenter dist=$distance", distance <= 2)
        }
    }

    @Test
    fun `sampled keycenters map to themselves`() {
        PianoReferenceEngine.KEYCENTERS.forEachIndexed { index, midi ->
            assertEquals(index, PianoReferenceEngine.sampleIndexForMidi(midi))
        }
    }

    @Test
    fun `rate compensates calibration so playback lands on equal temperament`() {
        // Each bundled sample was measured CALIBRATION[i] cents sharp of its
        // nominal ET pitch, so the rate must lower it by exactly that many
        // cents (playback cents + measured deviation ≈ 0).
        for ((index, midi) in PianoReferenceEngine.KEYCENTERS.withIndex()) {
            val rate = PianoReferenceEngine.sampleRateForMidi(midi)
            val playbackCents = 1200.0 * kotlin.math.log2(rate.toDouble())
            val compensationError = playbackCents + PianoReferenceEngine.CALIBRATION[index]
            assertTrue("midi=$midi error=$compensationError", abs(compensationError) < 0.6)
        }
    }

    @Test
    fun `duration scales inversely with playback rate`() {
        // 2.0 s sample at 44100 Hz, 16-bit mono.
        val frames = 44100 * 2
        assertEquals(2000L, PianoReferenceEngine.durationMillis(frames, 1.0f))
        assertEquals(4000L, PianoReferenceEngine.durationMillis(frames, 0.5f)) // low note rings longer
        assertEquals(1000L, PianoReferenceEngine.durationMillis(frames, 2.0f)) // high note, shorter
        assertEquals(1333L, PianoReferenceEngine.durationMillis(frames, 1.5f))
    }

    @Test
    fun `duration guards against invalid input`() {
        assertEquals(0L, PianoReferenceEngine.durationMillis(0, 1.0f))
        assertEquals(0L, PianoReferenceEngine.durationMillis(-100, 1.0f))
        assertEquals(0L, PianoReferenceEngine.durationMillis(100, 0f))
        assertEquals(0L, PianoReferenceEngine.durationMillis(100, -1f))
    }

    @Test
    fun `every midi maps to a positive predicted duration`() {
        for (midi in 21..108) {
            // The real sample is ~2.0 s (106.wav 1.39 s) stretched by the rate,
            // so the prediction must always land between ~1 s and ~5 s.
            val frames = 44100 * 2
            val rate = PianoReferenceEngine.sampleRateForMidi(midi)
            val duration = PianoReferenceEngine.durationMillis(frames, rate)
            assertTrue("midi=$midi rate=$rate duration=$duration", duration in 1000L..5000L)
        }
    }
}
