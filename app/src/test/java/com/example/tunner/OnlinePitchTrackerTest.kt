package com.example.tunner

import com.example.tunner.pitch.OnlinePitchTracker
import com.example.tunner.pitch.PitchCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnlinePitchTrackerTest {
    private fun candidate(frequency: Double, probability: Double = 0.9) = PitchCandidate(
        frequency = frequency,
        periodicity = probability,
        spectralQuality = probability,
        probability = probability,
        voicedProbability = probability,
    )

    @Test fun `requires three frames before first output`() {
        val tracker = OnlinePitchTracker(3)
        assertNull(tracker.submit(listOf(candidate(164.81))))
        assertNull(tracker.submit(listOf(candidate(164.82))))
        assertEquals(164.81, tracker.submit(listOf(candidate(164.80)))!!.pitch!!.frequency, 0.03)
    }

    @Test fun `single low octave candidate cannot replace stable pitch`() {
        val tracker = OnlinePitchTracker(3)
        repeat(3) { tracker.submit(listOf(candidate(164.81))) }
        assertNull(tracker.submit(listOf(candidate(82.405)))!!.pitch)
        assertEquals(164.81, tracker.submit(listOf(candidate(164.82)))!!.pitch!!.frequency, 0.03)
    }

    @Test fun `real octave change wins after three consistent frames`() {
        val tracker = OnlinePitchTracker(3)
        repeat(3) { tracker.submit(listOf(candidate(164.81))) }
        assertNull(tracker.submit(listOf(candidate(82.41)))!!.pitch)
        assertNull(tracker.submit(listOf(candidate(82.42)))!!.pitch)
        assertEquals(82.41, tracker.submit(listOf(candidate(82.40)))!!.pitch!!.frequency, 0.03)
    }

    @Test fun `target prior chooses target from otherwise equal candidates`() {
        val tracker = OnlinePitchTracker(3)
        val choices = listOf(candidate(82.41, 0.88), candidate(164.81, 0.88))
        repeat(2) { assertNull(tracker.submit(choices, targetFrequency = 164.81)) }
        assertEquals(164.81, tracker.submit(choices, targetFrequency = 164.81)!!.pitch!!.frequency, 0.01)
    }

    @Test fun `strong sustained wrong note overcomes target prior`() {
        val tracker = OnlinePitchTracker(3)
        val target = 164.81
        val actual = 82.41
        val choices = listOf(candidate(target, 0.52), candidate(actual, 0.96))
        repeat(2) { assertNull(tracker.submit(choices, targetFrequency = target)) }
        assertEquals(actual, tracker.submit(choices, targetFrequency = target)!!.pitch!!.frequency, 0.01)
    }

    @Test fun `unvoiced path is emitted for weak candidates`() {
        val tracker = OnlinePitchTracker(3)
        val weak = listOf(candidate(164.81, 0.05))
        repeat(2) { assertNull(tracker.submit(weak)) }
        assertNull(tracker.submit(weak)!!.pitch)
    }
}
