package com.precisiontuner

import com.precisiontuner.ear.Difficulty
import com.precisiontuner.ear.DifficultyPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DifficultyPresetsTest {

    companion object {
        /** Union of every note pool across difficulties (used by QuestionGeneratorTest). */
        fun notePoolAll(): Set<Int> =
            Difficulty.entries.flatMap { DifficultyPresets.notePool(it) }.toSet()
    }

    @Test
    fun `every difficulty keeps at least four candidates per exercise`() {
        for (difficulty in Difficulty.entries) {
            assertTrue("note ${difficulty.name}", DifficultyPresets.notePool(difficulty).size >= 4)
            assertTrue("staff ${difficulty.name}", DifficultyPresets.staffPool(difficulty).size >= 4)
            assertTrue("interval ${difficulty.name}", DifficultyPresets.intervalSemitones(difficulty).size >= 4)
            assertTrue("chord ${difficulty.name}", DifficultyPresets.chords(difficulty).size >= 4)
            assertTrue("scale ${difficulty.name}", DifficultyPresets.scales(difficulty).size >= 4)
        }
    }

    @Test
    fun `staff pools use independent progressive ranges`() {
        assertEquals(DifficultyPresets.NOTE_EASY, DifficultyPresets.staffPool(Difficulty.EASY))
        assertEquals(48..83, DifficultyPresets.staffPool(Difficulty.MEDIUM).let { it.first()..it.last() })
        assertEquals(36..84, DifficultyPresets.staffPool(Difficulty.HARD).let { it.first()..it.last() })
        assertEquals(24, DifficultyPresets.NOTE_HARD.size) // auditory pool is unchanged
    }

    @Test
    fun `note pool grows with difficulty`() {
        assertEquals(7, DifficultyPresets.notePool(Difficulty.EASY).size)
        assertEquals(12, DifficultyPresets.notePool(Difficulty.MEDIUM).size)
        assertEquals(24, DifficultyPresets.notePool(Difficulty.HARD).size)
    }

    @Test
    fun `note pools are nested`() {
        val easy = DifficultyPresets.notePool(Difficulty.EASY).toSet()
        val medium = DifficultyPresets.notePool(Difficulty.MEDIUM).toSet()
        val hard = DifficultyPresets.notePool(Difficulty.HARD).toSet()
        assertTrue(easy.all { it in medium })
        assertTrue(medium.all { it in hard })
    }

    @Test
    fun `interval pool grows with difficulty`() {
        assertEquals(7, DifficultyPresets.intervalSemitones(Difficulty.EASY).size)
        assertEquals(13, DifficultyPresets.intervalSemitones(Difficulty.MEDIUM).size)
        assertEquals(13, DifficultyPresets.intervalSemitones(Difficulty.HARD).size)
    }

    @Test
    fun `chord pool grows with difficulty`() {
        assertEquals(4, DifficultyPresets.chords(Difficulty.EASY).size)
        assertEquals(6, DifficultyPresets.chords(Difficulty.MEDIUM).size)
        assertEquals(8, DifficultyPresets.chords(Difficulty.HARD).size)
    }

    @Test
    fun `scale pool grows with difficulty`() {
        assertEquals(4, DifficultyPresets.scales(Difficulty.EASY).size)
        assertEquals(8, DifficultyPresets.scales(Difficulty.MEDIUM).size)
        assertEquals(8, DifficultyPresets.scales(Difficulty.HARD).size)
    }

    @Test
    fun `hard scale pool is the full library`() {
        assertEquals(
            com.precisiontuner.ear.ScaleLibrary.ALL.map { it.name }.toSet(),
            DifficultyPresets.scales(Difficulty.HARD).map { it.name }.toSet(),
        )
    }
}
