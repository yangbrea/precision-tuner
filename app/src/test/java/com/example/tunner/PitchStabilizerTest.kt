package com.example.tunner

import com.example.tunner.pitch.PitchStabilizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PitchStabilizerTest {
    @Test fun `single octave error is ignored`() {
        val tracker = PitchStabilizer(windowSize = 3)
        assertNull(tracker.submit(164.8))
        assertNull(tracker.submit(164.9))
        assertEquals(164.8, tracker.submit(164.8)!!, 0.2)
        assertEquals(164.8, tracker.submit(82.4)!!, 0.2)
        assertEquals(164.8, tracker.submit(164.85)!!, 0.2)
    }

    @Test fun `sustained new octave is accepted after three frames`() {
        val tracker = PitchStabilizer(windowSize = 3)
        repeat(3) { tracker.submit(164.8) }
        assertEquals(164.8, tracker.submit(82.4)!!, 0.2)
        assertEquals(164.8, tracker.submit(82.42)!!, 0.2)
        assertEquals(82.42, tracker.submit(82.41)!!, 0.1)
    }

    @Test fun `confirmed manual transition can bypass duplicate delay`() {
        val tracker = PitchStabilizer(windowSize = 5)
        repeat(3) { tracker.submit(164.8) }
        assertEquals(82.4, tracker.submit(82.4, transitionConfirmed = true)!!, 0.01)
    }
}
