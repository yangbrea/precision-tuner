package com.precisiontuner

import com.precisiontuner.ear.RhythmNote
import com.precisiontuner.ear.RhythmPattern
import com.precisiontuner.ear.RhythmScorer
import com.precisiontuner.ear.RhythmLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RhythmScorerTest {

    // Hand-built steady pulse (removed from the library as "tap evenly" filler,
    // but the scorer must still handle it).
    private val quarters = RhythmPattern("测试四分", notes = List(4) { RhythmNote(12) })

    @Test fun `perfect reproduction scores one hundred`() {
        val scored = RhythmScorer.score(quarters, listOf(0L, 500L, 1000L, 1500L))
        assertEquals(100, scored.score)
        assertTrue(scored.correct)
        assertEquals(4, scored.expected)
    }

    @Test fun `slight timing jitter still counts as correct`() {
        // ~6-8% per-interval jitter: normalized error ~0.03 -> ~93.
        val scored = RhythmScorer.score(quarters, listOf(0L, 480L, 1050L, 1520L))
        assertTrue("score=${scored.score}", scored.score >= 85)
        assertTrue(scored.correct)
    }

    @Test fun `clearly uneven tapping of an even pattern fails`() {
        // [300, 400, 200] ms vs a steady quarter pulse: normalized error ~0.07
        // -> ~81, clearly below the 85 threshold.
        val scored = RhythmScorer.score(quarters, listOf(0L, 300L, 700L, 900L))
        assertTrue("score=${scored.score}", scored.score < 85)
        assertFalse(scored.correct)
    }

    @Test fun `wrong tap count is a format error`() {
        val scored = RhythmScorer.score(quarters, listOf(0L, 500L, 1000L))
        assertEquals(0, scored.score)
        assertFalse(scored.correct)
        assertEquals(3, scored.tapped)
    }

    @Test fun `tapping a dotted pattern evenly is not correct`() {
        // Dotted rhythm needs 50/17/33 spacing; an even 33/33/33 fails.
        val dotted = RhythmLibrary.DOTTED_EIGHTH // [18,6,12,12]
        val scored = RhythmScorer.score(dotted, listOf(0L, 400L, 800L, 1200L))
        assertTrue("score=${scored.score}", scored.score < 80)
        assertFalse(scored.correct)
    }

    @Test fun `reproducing the dotted pattern correctly passes`() {
        val dotted = RhythmLibrary.DOTTED_EIGHTH
        val scored = RhythmScorer.score(dotted, listOf(0L, 700L, 950L, 1400L))
        assertTrue("score=${scored.score}", scored.score >= 80)
        assertTrue(scored.correct)
    }

    @Test fun `tempo drift does not punish a steady rhythm`() {
        // Accelerating taps: intervals 550/450/360 ms, still a steady quarter
        // pulse relative to the total -> high score.
        val scored = RhythmScorer.score(quarters, listOf(0L, 550L, 1000L, 1360L))
        assertTrue("score=${scored.score}", scored.score >= 80)
        assertTrue(scored.correct)
    }

    @Test fun `leading silence before the first tap is ignored`() {
        // Times are relative to the first tap, so an absolute offset changes
        // nothing (the recorder already strips it; this guards the scorer).
        val offset = RhythmScorer.score(quarters, listOf(500L, 1000L, 1500L, 2000L))
        assertEquals(100, offset.score)
    }

    @Test fun `a sixteenth run must be tapped densely to pass`() {
        val sixteenths = RhythmPattern("测试十六分", notes = List(16) { RhythmNote(3) })
        val perfect = RhythmScorer.score(
            sixteenths,
            List(16) { it * 125L }, // 125 ms apart, total 1875 ms
        )
        assertEquals(100, perfect.score)
        assertTrue(perfect.correct)
    }

    @Test fun `single tap pattern cannot be scored`() {
        val single = RhythmPattern("单音", notes = listOf(RhythmNote(12)))
        val scored = RhythmScorer.score(single, listOf(0L))
        assertEquals(0, scored.score)
        assertFalse(scored.correct)
    }

    @Test fun `two equal notes have no discriminating power and score zero`() {
        // Two equal notes = one interval, which normalizes to 1.0 no matter how
        // the user taps — any two taps would be a perfect score. The scorer must
        // reject such patterns outright (MIN_TAPS = 3).
        val two = RhythmPattern("二音", notes = listOf(RhythmNote(12), RhythmNote(12)))
        assertEquals(0, RhythmScorer.score(two, listOf(0L, 500L)).score)
        assertEquals(0, RhythmScorer.score(two, listOf(0L, 1200L)).score)
        assertEquals(0, RhythmScorer.score(two, listOf(0L, 100L)).score)
        assertFalse(RhythmScorer.score(two, listOf(0L, 500L)).correct)
    }

    @Test fun `negative or zero timestamps never crash`() {
        val scored = RhythmScorer.score(quarters, listOf(0L, 0L, 0L, 0L))
        assertEquals(0, scored.score)
        assertFalse(scored.correct)
    }
}
