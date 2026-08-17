package com.precisiontuner

import com.precisiontuner.pitch.AutomaticStringTracker
import com.precisiontuner.pitch.PitchCandidate
import com.precisiontuner.tuning.InstrumentString
import com.precisiontuner.tuning.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutomaticStringTrackerTest {
    private val tuning = Tuning("test", "Test", listOf(
        InstrumentString(1, "E", "E4", 64),
        InstrumentString(2, "B", "B3", 59),
        InstrumentString(3, "E", "E3", 52),
        InstrumentString(4, "E", "E2", 40),
    ))

    private fun candidate(string: Int, probability: Double = 0.95, cents: Double = 0.0): PitchCandidate {
        val frequency = tuning.byNumber(string)!!.frequency * Math.pow(2.0, cents / 1200.0)
        return PitchCandidate(frequency, probability, probability, probability, probability)
    }

    @Test fun `acquires only after three consistent frames and preserves octave identity`() {
        val tracker = AutomaticStringTracker()
        repeat(2) { assertNull(tracker.submit(tuning, listOf(candidate(3)), false, false, 8.0, 0.5).activeString) }
        assertEquals(3, tracker.submit(tuning, listOf(candidate(3)), false, false, 8.0, 0.5).activeString!!.number)
    }

    @Test fun `candidate outside capture range is not acquired`() {
        val tracker = AutomaticStringTracker()
        repeat(4) {
            assertNull(tracker.submit(tuning, listOf(candidate(3, cents = 260.0)), false, false, 8.0, 0.5).activeString)
        }
    }

    @Test fun `tail octave candidates cannot replace active string and it eventually releases`() {
        val tracker = AutomaticStringTracker(releaseFrames = 5)
        repeat(3) { tracker.submit(tuning, listOf(candidate(3)), false, false, 8.0, 0.5) }
        repeat(4) {
            assertEquals(3, tracker.submit(tuning, listOf(candidate(4, 0.7)), false, false, 3.0, 0.5).activeString!!.number)
        }
        assertNull(tracker.submit(tuning, listOf(candidate(4, 0.7)), false, false, 3.0, 0.5).activeString)
    }

    @Test fun `onset switches to another legal string after three frames`() {
        val tracker = AutomaticStringTracker()
        repeat(3) { tracker.submit(tuning, listOf(candidate(3)), false, false, 8.0, 0.5) }
        assertEquals(3, tracker.submit(tuning, listOf(candidate(2)), false, true, 8.0, 0.5).activeString!!.number)
        assertEquals(3, tracker.submit(tuning, listOf(candidate(2)), false, false, 8.0, 0.5).activeString!!.number)
        assertEquals(2, tracker.submit(tuning, listOf(candidate(2)), false, false, 8.0, 0.5).activeString!!.number)
    }

    @Test fun `strong sustained alternate needs six frames while weak alternate cannot switch`() {
        val tracker = AutomaticStringTracker(releaseFrames = 20)
        repeat(3) { tracker.submit(tuning, listOf(candidate(3)), false, false, 8.0, 0.5) }
        repeat(7) {
            assertEquals(3, tracker.submit(tuning, listOf(candidate(2, 0.8)), false, false, 8.0, 0.5).activeString!!.number)
        }
        repeat(5) {
            assertEquals(3, tracker.submit(tuning, listOf(candidate(2)), false, false, 8.0, 0.5).activeString!!.number)
        }
        assertEquals(2, tracker.submit(tuning, listOf(candidate(2)), false, false, 8.0, 0.5).activeString!!.number)
    }
}
