package com.example.tunner

import com.example.tunner.tuning.NoteMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteMapperTest {

    private fun noteFull(freq: Double): String {
        val n = NoteMapper.noteFromFrequency(freq)
        return n.name + n.octave
    }

    @Test
    fun mapsKnownNotes() {
        assertEquals("E2", noteFull(82.41))
        assertEquals("A2", noteFull(110.0))
        assertEquals("D3", noteFull(146.83))
        assertEquals("G3", noteFull(196.0))
        assertEquals("B3", noteFull(246.94))
        assertEquals("E4", noteFull(329.63))
        assertEquals("A4", noteFull(440.0))
        assertEquals("A♯4", noteFull(466.16))
        assertEquals("C4", noteFull(261.63))
    }

    @Test
    fun a4IsZeroCents() {
        assertEquals(0.0, NoteMapper.cents(440.0), 0.001)
    }

    @Test
    fun centsForSharpNote() {
        // 445 Hz is ~+19.56 cents sharp of A4.
        assertEquals(19.56, NoteMapper.cents(445.0), 0.1)
    }

    @Test
    fun midiRoundTrip() {
        for (midi in 36..88) {
            val f = NoteMapper.frequencyFromMidi(midi)
            assertEquals(midi, NoteMapper.midiFromFrequency(f))
        }
    }

    @Test
    fun customReferenceA4() {
        // At A4 = 442 Hz, 442 Hz is the new zero.
        assertEquals(0.0, NoteMapper.cents(442.0, a4 = 442.0), 0.001)
        assertEquals(69, NoteMapper.midiFromFrequency(442.0, a4 = 442.0))
    }
}
