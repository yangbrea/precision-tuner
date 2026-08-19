package com.precisiontuner

import com.precisiontuner.ear.RhythmLibrary
import com.precisiontuner.ear.RhythmPattern
import com.precisiontuner.ui.ear.beatGrids
import com.precisiontuner.ui.ear.findBeamGroups
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Beam grouping follows VexFlow's auto-beaming: runs of equal short notes
 * beam together per beat, never across a beat boundary.
 */
class RhythmStaffBeamTest {

    private fun groups(pattern: RhythmPattern): List<Pair<Int, Int>> =
        findBeamGroups(pattern.notes, pattern.onsetGrids, beatGrids(pattern))
            .map { it.start to it.end }

    @Test fun `eight eighths beam as four groups of two`() {
        assertEquals(listOf(0 to 1, 2 to 3, 4 to 5, 6 to 7), groups(RhythmLibrary.EIGHTHS))
    }

    @Test fun `sixteenth run beams one beat per group`() {
        // 16 sixteenths at 3 grids each: 4 groups of 4 (one beat each).
        assertEquals(
            listOf(0 to 3, 4 to 7, 8 to 11, 12 to 15),
            groups(RhythmLibrary.ALL_SIXTEENTHS),
        )
    }

    @Test fun `six eight syncopation beams the leading eighth run`() {
        // 6/8 beat = 18 grids: the three leading eighths share one beam.
        assertEquals(listOf(0 to 2), groups(RhythmLibrary.SIX_EIGHT_SYNCOPATED))
    }

    @Test fun `pick-up sixteenths beam together within the beat`() {
        // 前八后十六: the two sixteenths after the eighth share a beam.
        val pattern = RhythmLibrary.EIGHTH_TWO_SIXTEENTHS // [6,3,3,12,12,12]
        assertEquals(listOf(1 to 2), groups(pattern))
    }

    @Test fun `three four eighths beam within one bar`() {
        // 3/4 six eighths: onsets 0,6,12,18,24,30, beat 12 -> groups of 2.
        assertEquals(
            listOf(0 to 1, 2 to 3, 4 to 5),
            groups(RhythmLibrary.THREE_FOUR_EIGHTHS),
        )
    }

    @Test fun `isolated eighths across a quarter do not beam`() {
        // 四分与八分 [12,6,6,12,12]: the two eighths sit inside beat 2 -> one group.
        val pattern = RhythmLibrary.QUARTER_EIGHTH
        assertEquals(listOf(1 to 2), groups(pattern))
    }
}
