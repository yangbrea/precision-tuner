package com.example.tunner

import com.example.tunner.pitch.CrepeHybridArbitrator
import com.example.tunner.pitch.PitchCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrepeHybridArbitratorTest {
    private fun candidate(frequency: Double, confidence: Double = 0.9) = PitchCandidate(
        frequency, confidence, confidence, confidence, confidence,
    )

    @Test fun `ordinary note does not request neural inference`() {
        val arbitrator = CrepeHybridArbitrator()
        assertNull(arbitrator.triggerReason(listOf(candidate(329.63))))
    }

    @Test fun `three-times conflict is resolved to crepe-supported E4`() {
        val arbitrator = CrepeHybridArbitrator()
        val dsp = listOf(candidate(110.0, 0.94), candidate(329.63, 0.82))
        val trigger = arbitrator.triggerReason(dsp)
        val result = arbitrator.arbitrate(dsp, 329.7, 0.91, 0.5, trigger)
        assertEquals(329.63, result.single().frequency, 0.01)
        assertEquals("crepe", arbitrator.state.decisionSource)
    }

    @Test fun `tail resonance cannot replace a stable E4 anchor`() {
        val arbitrator = CrepeHybridArbitrator()
        arbitrator.observeAccepted(329.63)
        val dsp = listOf(candidate(110.0, 0.96))
        val trigger = arbitrator.triggerReason(dsp)
        val result = arbitrator.arbitrate(dsp, 329.5, 0.88, 0.5, trigger)
        assertTrue(result.single().frequency > 320.0)
        assertEquals("anchor_3x", trigger)
    }

    @Test fun `new real A2 supported by crepe can replace E4`() {
        val arbitrator = CrepeHybridArbitrator()
        arbitrator.observeAccepted(329.63)
        val dsp = listOf(candidate(110.0, 0.95))
        repeat(3) {
            val trigger = arbitrator.triggerReason(dsp)
            val result = arbitrator.arbitrate(dsp, 110.1, 0.93, 0.5, trigger)
            assertEquals(110.0, result.single().frequency, 0.01)
        }
        assertEquals(3, arbitrator.state.confirmationFrames)
    }

    @Test fun `weak or unrelated neural result falls back to DSP`() {
        val arbitrator = CrepeHybridArbitrator()
        val dsp = listOf(candidate(110.0), candidate(329.63))
        val trigger = arbitrator.triggerReason(dsp)
        assertEquals(dsp, arbitrator.arbitrate(dsp, 329.63, 0.2, 0.5, trigger))
        assertEquals(dsp, arbitrator.arbitrate(dsp, 220.0, 0.95, 0.5, trigger))
    }

    @Test fun `anchor releases after configured missing frames`() {
        val arbitrator = CrepeHybridArbitrator(releaseFrames = 3)
        arbitrator.observeAccepted(329.63)
        repeat(3) { arbitrator.observeMissing() }
        assertNull(arbitrator.state.anchorFrequency)
    }
}
