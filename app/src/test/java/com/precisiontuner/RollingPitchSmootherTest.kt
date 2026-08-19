package com.precisiontuner

import com.precisiontuner.pitch.RollingPitchSmoother
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RollingPitchSmootherTest {

    @Test fun `averages the sliding window of frequencies`() {
        val smoother = RollingPitchSmoother(3)
        assertEquals(440.0, smoother.push("n:69", 440.0)!!, 1e-9)
        assertEquals(441.0, smoother.push("n:69", 442.0)!!, 1e-9)
        assertEquals(441.0, smoother.push("n:69", 441.0)!!, 1e-9)
    }

    @Test fun `window caps the number of averaged samples`() {
        val smoother = RollingPitchSmoother(3)
        smoother.push("n:69", 440.0)
        smoother.push("n:69", 442.0)
        smoother.push("n:69", 444.0)
        // Oldest (440) drops; the window averages 442, 444, 442.
        assertEquals(442.6666666666667, smoother.push("n:69", 442.0)!!, 1e-9)
    }

    @Test fun `note change drops stale entries instead of blending notes`() {
        val smoother = RollingPitchSmoother(5)
        smoother.push("n:69", 440.0)
        smoother.push("n:69", 441.0)
        // A new note must not average against the old note's frequencies.
        assertEquals(220.0, smoother.push("n:45", 220.0)!!, 1e-9)
        assertEquals(221.0, smoother.push("n:45", 222.0)!!, 1e-9)
    }

    @Test fun `null or invalid signal clears the queue`() {
        val smoother = RollingPitchSmoother(5)
        smoother.push("n:69", 440.0)
        smoother.push("n:69", 442.0)
        assertNull(smoother.push("n:69", null))
        assertEquals(441.0, smoother.push("n:69", 441.0)!!, 1e-9)
        assertNull(smoother.push("n:69", Double.NaN))
    }

    @Test fun `window size can be shrunk at runtime`() {
        val smoother = RollingPitchSmoother(5)
        smoother.push("n:69", 440.0)
        smoother.push("n:69", 442.0)
        smoother.push("n:69", 444.0)
        smoother.setWindowSize(1)
        assertEquals(444.0, smoother.push("n:69", 444.0)!!, 1e-9)
    }

    @Test fun `reset clears all state`() {
        val smoother = RollingPitchSmoother(5)
        smoother.push("n:69", 440.0)
        smoother.reset()
        assertNull(smoother.push("n:69", null))
        assertEquals(441.0, smoother.push("n:69", 441.0)!!, 1e-9)
    }
}
