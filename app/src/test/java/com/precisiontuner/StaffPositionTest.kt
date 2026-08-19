package com.precisiontuner

import com.precisiontuner.ear.StaffPosition
import com.precisiontuner.ear.Accidental
import com.precisiontuner.ear.NoteLetter
import com.precisiontuner.ear.StaffClef
import com.precisiontuner.ear.StaffNotation
import com.precisiontuner.ear.clefForMidi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffPositionTest {

    @Test
    fun `known notes land on the expected lines and spaces`() {
        assertEquals(0f, StaffPosition.lineOffset(64))     // E4 lowest line
        assertEquals(1f, StaffPosition.lineOffset(67))     // G4
        assertEquals(2f, StaffPosition.lineOffset(71))     // B4
        assertEquals(3f, StaffPosition.lineOffset(74))     // D5
        assertEquals(4f, StaffPosition.lineOffset(77))     // F5 top line
        assertEquals(0.5f, StaffPosition.lineOffset(65))   // F4 first space
        assertEquals(-1f, StaffPosition.lineOffset(60))    // C4 first ledger line
        assertEquals(-1.5f, StaffPosition.lineOffset(59))  // B3 between ledger lines
        assertEquals(-2f, StaffPosition.lineOffset(57))    // A3 second ledger line
        assertEquals(4.5f, StaffPosition.lineOffset(79))   // G5 above the staff
        assertEquals(5f, StaffPosition.lineOffset(81))     // A5 first upper ledger line
        assertEquals(6f, StaffPosition.lineOffset(84))     // C6 second upper ledger line
    }

    @Test
    fun `ledger lines follow the nearest-line rule`() {
        assertEquals(emptyList<Int>(), StaffPosition.ledgerOffsets(64))   // E4 inside
        assertEquals(emptyList<Int>(), StaffPosition.ledgerOffsets(77))   // F5 inside
        assertEquals(listOf(-1), StaffPosition.ledgerOffsets(60))         // C4 on lower line
        assertEquals(emptyList<Int>(), StaffPosition.ledgerOffsets(62))   // D4 first space: no ledger
        assertEquals(listOf(-1), StaffPosition.ledgerOffsets(59))         // B3 beyond C4 ledger
        assertEquals(listOf(-1, -2), StaffPosition.ledgerOffsets(57))     // A3 second lower line
        assertEquals(emptyList<Int>(), StaffPosition.ledgerOffsets(79))   // G5 first upper space
        assertEquals(listOf(5), StaffPosition.ledgerOffsets(81))          // A5 on first line
        assertEquals(listOf(5, 6), StaffPosition.ledgerOffsets(84))       // C6 second line
    }

    @Test
    fun `clef boundary and bass positions are correct`() {
        assertEquals(StaffClef.BASS, clefForMidi(59))
        assertEquals(StaffClef.TREBLE, clefForMidi(60))
        assertEquals(0f, StaffPosition.lineOffset(StaffNotation.fromMidi(43))) // G2 bass bottom line
        assertEquals(2f, StaffPosition.lineOffset(StaffNotation.fromMidi(50))) // D3 middle line
        assertEquals(4f, StaffPosition.lineOffset(StaffNotation.fromMidi(57))) // A3 top line
        assertEquals(-1f, StaffPosition.lineOffset(StaffNotation.fromMidi(60))) // C4 treble ledger
    }

    @Test
    fun `enharmonic spellings share pitch but occupy different positions`() {
        val sharp = StaffNotation(61, NoteLetter.C, 4, Accidental.SHARP)
        val flat = StaffNotation(61, NoteLetter.D, 4, Accidental.FLAT)
        assertEquals("C♯4", sharp.displayName)
        assertEquals("D♭4", flat.displayName)
        assertEquals(-1f, StaffPosition.lineOffset(sharp))
        assertEquals(-0.5f, StaffPosition.lineOffset(flat))
    }

    @Test
    fun `black keys are detected`() {
        assertTrue(StaffPosition.isBlackKey(66))  // F#4
        assertTrue(StaffPosition.isBlackKey(61))  // C#4
        assertTrue(StaffPosition.isBlackKey(75))  // D#5
        assertFalse(StaffPosition.isBlackKey(60)) // C4
        assertFalse(StaffPosition.isBlackKey(71)) // B4
        assertFalse(StaffPosition.isBlackKey(76)) // E5
    }

    @Test
    fun `line offsets are monotonic across the piano range`() {
        var previous = Float.NEGATIVE_INFINITY
        for (midi in 21..108) {
            val offset = StaffPosition.lineOffset(midi)
            // Black keys share the letter position of the preceding white key,
            // so offsets are non-decreasing, not strictly increasing.
            assertTrue("midi=$midi offset=$offset", offset >= previous)
            previous = offset
        }
    }

    @Test
    fun `line offsets agree with the note names`() {
        // C4 sits one line below E4; an octave above is 7 letter steps = 3.5 gaps.
        assertEquals(StaffPosition.lineOffset(60) + 3.5f, StaffPosition.lineOffset(72))
        assertEquals(StaffPosition.lineOffset(60) + 7f, StaffPosition.lineOffset(84))
    }
}
