package com.precisiontuner

import com.precisiontuner.ear.Difficulty
import com.precisiontuner.ear.DifficultyPresets
import com.precisiontuner.ear.RhythmLibrary
import com.precisiontuner.ear.RhythmPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RhythmLibraryTest {

    @Test fun `every difficulty pool is well populated`() {
        assertEquals(12, RhythmLibrary.EASY.size)
        assertEquals(19, RhythmLibrary.MEDIUM.size)
        assertEquals(18, RhythmLibrary.HARD.size)
        Difficulty.entries.forEach { difficulty ->
            val pool = DifficultyPresets.rhythmPatterns(difficulty)
            assertTrue("$difficulty pool too small: ${pool.size}", pool.size >= 4)
        }
    }

    @Test fun `difficulty pools are disjoint`() {
        val easy = RhythmLibrary.EASY.map { it.name }.toSet()
        val medium = RhythmLibrary.MEDIUM.map { it.name }.toSet()
        val hard = RhythmLibrary.HARD.map { it.name }.toSet()
        assertTrue(easy.intersect(medium).isEmpty())
        assertTrue(easy.intersect(hard).isEmpty())
        assertTrue(medium.intersect(hard).isEmpty())
    }

    @Test fun `every pattern has valid structure and length contrast`() {
        val all = RhythmLibrary.EASY + RhythmLibrary.MEDIUM + RhythmLibrary.HARD
        val names = all.map { it.name }
        assertEquals("names must be unique", all.size, names.toSet().size)

        all.forEach { pattern ->
            assertTrue("${pattern.name}: empty", pattern.notes.isNotEmpty())
            assertTrue("${pattern.name}: bad grids", pattern.notes.all { it.grids > 0 })
            assertTrue("${pattern.name}: totalGrids <= 0", pattern.totalGrids > 0)
            // At least 3 audible notes (>= 2 intervals) so a reproduction has
            // discriminating power.
            assertTrue(
                "${pattern.name}: expectedTaps ${pattern.expectedTaps} < 3",
                pattern.expectedTaps >= 3,
            )
            // No "tap evenly N times" filler: the inter-onset intervals must
            // contain at least two distinct lengths.
            val tapGaps = pattern.tapOnsetGrids.zipWithNext { a, b -> b - a }
            assertTrue(
                "${pattern.name}: uniform pattern (no length contrast): $tapGaps",
                tapGaps.distinct().size >= 2,
            )
            // Onsets strictly increase.
            val onsets = pattern.onsetGrids
            assertTrue(
                "${pattern.name}: onsets not monotonic ${onsets}",
                onsets.zipWithNext().all { (a, b) -> b > a },
            )
            // Tap onsets are a sublist of the onsets.
            assertTrue(pattern.tapOnsetGrids.all { it in onsets })
        }
    }

    @Test fun `patterns fill their time signature bar`() {
        (RhythmLibrary.EASY + RhythmLibrary.MEDIUM + RhythmLibrary.HARD)
            .forEach { pattern ->
                // A (a/b) bar holds 48*a/b grid units: 4/4 -> 48, 3/4 -> 36,
                // 6/8 -> 36, 2/4 -> 24.
                val expected = 48 * pattern.beatsPerBar / pattern.beatUnit
                assertEquals(
                    "${pattern.name} should fill its ${pattern.beatsPerBar}/${pattern.beatUnit} bar",
                    expected,
                    pattern.totalGrids,
                )
            }
    }

    @Test fun `non four-four patterns carry the correct time signature`() {
        assertEquals(3, RhythmLibrary.THREE_FOUR_DOTTED.beatsPerBar)
        assertEquals(4, RhythmLibrary.THREE_FOUR_DOTTED.beatUnit)
        assertEquals(6, RhythmLibrary.SIX_EIGHT_SYNCOPATED.beatsPerBar)
        assertEquals(8, RhythmLibrary.SIX_EIGHT_SYNCOPATED.beatUnit)
        assertEquals(2, RhythmLibrary.TWO_FOUR_QUARTER_EIGHTH.beatsPerBar)
        assertEquals(4, RhythmLibrary.TWO_FOUR_QUARTER_EIGHTH.beatUnit)
    }

    @Test fun `rests reduce expected taps without changing onsets`() {
        val withRest = RhythmLibrary.QUARTER_REST // [12, rest, 12, 12]
        assertEquals(4, withRest.notes.size)
        assertEquals(3, withRest.expectedTaps)
        assertEquals(4, withRest.onsetGrids.size)
        assertEquals(3, withRest.tapOnsetGrids.size)
    }
}
