package com.precisiontuner

import com.precisiontuner.ear.IntervalLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntervalLibraryTest {

    @Test
    fun `every interval has a unique semitone count`() {
        val semitones = IntervalLibrary.ALL.map { it.semitones }
        assertEquals(semitones.size, semitones.toSet().size)
        assertEquals(13, IntervalLibrary.ALL.size)
        assertEquals((0..12).toList(), semitones.sorted())
    }

    @Test
    fun `basic set is a subset of all and has enough options for a quiz`() {
        assertTrue(IntervalLibrary.BASIC_SEMITONES.all { it in 0..12 })
        assertTrue(IntervalLibrary.BASIC.all { it.semitones in IntervalLibrary.BASIC_SEMITONES })
        assertTrue(IntervalLibrary.BASIC.size >= 4)
    }

    @Test
    fun `bySemitone resolves every known interval`() {
        assertEquals("纯一度", IntervalLibrary.bySemitone(0).name)
        assertEquals("纯五度", IntervalLibrary.bySemitone(7).name)
        assertEquals("纯八度", IntervalLibrary.bySemitone(12).name)
    }
}
