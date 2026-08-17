package com.precisiontuner

import com.precisiontuner.ui.beatAngle
import com.precisiontuner.ui.nodeLit
import org.junit.Assert.assertEquals
import org.junit.Test

class MetronomeRingTest {

    @Test
    fun `beat angles are evenly spaced clockwise from the top`() {
        assertEquals(270f, beatAngle(0, 4), 0.0001f) // beat 1 at top
        assertEquals(360f, beatAngle(1, 4), 0.0001f) // right
        assertEquals(450f, beatAngle(2, 4), 0.0001f) // bottom
        assertEquals(540f, beatAngle(3, 4), 0.0001f) // left
    }

    @Test
    fun `beat angle wraps around and guards degenerate counts`() {
        assertEquals(270f, beatAngle(4, 4), 0.0001f) // wraps to beat 1
        assertEquals(390f, beatAngle(1, 3), 0.0001f) // 120° spacing
        assertEquals(270f, beatAngle(0, 0), 0.0001f) // no beats -> top
        assertEquals(270f, beatAngle(2, 1), 0.0001f) // single beat repeats at top
    }

    @Test
    fun `nodes stay dim ahead of the sweep and light up after passing`() {
        assertEquals(0f, nodeLit(0.10f, true, 0.25f)) // sweep still ahead
        assertEquals(0f, nodeLit(0.24f, true, 0.25f))
        assertEquals(0f, nodeLit(0.24f, true, 0.25f))
        // Brighten over NODE_GLOW_SPAN (0.06) once passed.
        assertEquals(0.5f, nodeLit(0.28f, true, 0.25f), 0.0001f)
        assertEquals(1f, nodeLit(0.31f, true, 0.25f), 0.0001f)
        assertEquals(1f, nodeLit(0.90f, true, 0.25f), 0.0001f) // stays lit
    }

    @Test
    fun `nodes are dim when stopped and clamp to lit at the start`() {
        assertEquals(0f, nodeLit(0.9f, false, 0.25f)) // stopped -> dim
        assertEquals(1f, nodeLit(0.5f, true, 0.0f), 0.0001f) // start node lit once running
        assertEquals(1f, nodeLit(5f, true, 0.25f), 0.0001f) // progress beyond 1 clamps
    }
}
