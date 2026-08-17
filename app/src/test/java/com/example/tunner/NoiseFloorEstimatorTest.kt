package com.example.tunner

import com.example.tunner.pitch.NoiseFloorEstimator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoiseFloorEstimatorTest {
    @Test fun `gate adapts upward using rejected frames`() {
        val gate = NoiseFloorEstimator(initialFloor = 0.001)
        assertTrue(gate.shouldAnalyze(0.01, 2.5))
        repeat(30) { gate.observeRejected(0.01) }
        assertFalse(gate.shouldAnalyze(0.01, 2.5))
    }

    @Test fun `accepted signal does not train noise floor`() {
        val gate = NoiseFloorEstimator(initialFloor = 0.001)
        repeat(100) { assertTrue(gate.shouldAnalyze(0.01, 2.5)) }
        assertTrue(gate.shouldAnalyze(0.003, 2.5))
    }

    @Test fun `single impact cannot immediately close gate`() {
        val gate = NoiseFloorEstimator(initialFloor = 0.001)
        gate.observeRejected(1.0)
        assertTrue(gate.shouldAnalyze(0.004, 2.5))
    }
}
