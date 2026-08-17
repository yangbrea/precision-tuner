package com.precisiontuner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunerStateTest {

    @Test
    fun trackingPitchWithinThresholdIsInTune() {
        val state = TunerState(
            cents = 3.0,
            detectionPhase = DetectionPhase.TRACKING,
        )

        assertTrue(state.isInTune)
    }

    @Test
    fun outOfRangePitchCanNeverBeInTune() {
        val state = TunerState(
            cents = 0.0,
            detectionPhase = DetectionPhase.OUT_OF_RANGE,
        )

        assertFalse(state.isInTune)
    }

    @Test
    fun waitingForInputIsNotInTune() {
        assertFalse(TunerState(detectionPhase = DetectionPhase.WAITING).isInTune)
    }
}
