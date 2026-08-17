package com.precisiontuner

import com.precisiontuner.pitch.NoiseFloorEstimator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoiseFloorEstimatorTest {
    @Test fun `analyzed unvoiced ambient adapts gate upward`() {
        val gate = NoiseFloorEstimator(initialFloor = 0.001)
        assertTrue(gate.shouldAnalyze(0.01, 2.5))
        repeat(160) { gate.observeUnvoiced(0.01) }
        assertFalse(gate.shouldAnalyze(0.01, 2.5))
    }

    @Test fun `accepted signal does not train noise floor`() {
        val gate = NoiseFloorEstimator(initialFloor = 0.001)
        repeat(100) { assertTrue(gate.shouldAnalyze(0.01, 2.5)) }
        assertTrue(gate.shouldAnalyze(0.003, 2.5))
    }

    @Test fun `single impact cannot immediately close gate`() {
        val gate = NoiseFloorEstimator(initialFloor = 0.001)
        gate.observeUnvoiced(1.0)
        assertTrue(gate.shouldAnalyze(0.004, 2.5))
    }

    @Test fun `gate rejected sustained tone cannot train floor upward`() {
        val gate = NoiseFloorEstimator(initialFloor = 0.006)
        repeat(80) { gate.observeGateRejected(0.01) }
        assertTrue(gate.shouldAnalyze(0.01, 2.5))
    }

    @Test fun `recent voiced signal freezes upward ambient learning`() {
        val gate = NoiseFloorEstimator(initialFloor = 0.001)
        gate.observeVoiced(0.02)
        repeat(20) { gate.observeUnvoiced(0.015) }
        assertTrue(gate.shouldAnalyze(0.004, 2.5))
    }
}
