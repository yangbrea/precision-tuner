package com.example.tunner

import com.example.tunner.tuning.NoteMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals("A#4", noteFull(466.16))
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

    // ---- midiFromName: keyboard direct input for tuning presets ----

    @Test
    fun parsesNaturalNotes() {
        assertEquals(64, NoteMapper.midiFromName("E4", 4)) // C4 = 60
        assertEquals(62, NoteMapper.midiFromName("D4", 4))
        assertEquals(24, NoteMapper.midiFromName("C1", 4))
        assertEquals(96, NoteMapper.midiFromName("C7", 4))
        assertEquals(67, NoteMapper.midiFromName("G4", 4))
    }

    @Test
    fun parsesCaseInsensitivelyAndTrimsWhitespace() {
        assertEquals(64, NoteMapper.midiFromName("e4", 4))
        assertEquals(64, NoteMapper.midiFromName(" E4 ", 4))
        assertEquals(69, NoteMapper.midiFromName("a4", 4))
    }

    @Test
    fun parsesSharps() {
        assertEquals(51, NoteMapper.midiFromName("D#3", 4))
        assertEquals(68, NoteMapper.midiFromName("G#4", 4))
        assertEquals(61, NoteMapper.midiFromName("C#4", 4))
    }

    @Test
    fun normalizesFlatsToEnharmonicSharps() {
        assertEquals(46, NoteMapper.midiFromName("Bb2", 4)) // = A#2
        assertEquals(49, NoteMapper.midiFromName("Db3", 4)) // = C#3
        assertEquals(51, NoteMapper.midiFromName("Eb3", 4)) // = D#3
        assertEquals(54, NoteMapper.midiFromName("Gb3", 4)) // = F#3
        assertEquals(68, NoteMapper.midiFromName("Ab4", 4)) // = G#4
        assertEquals(46, NoteMapper.midiFromName("bb2", 4)) // lowercase b letter = B-flat-2
    }

    @Test
    fun omittingOctaveUsesDefault() {
        assertEquals(62, NoteMapper.midiFromName("D", 4)) // D4
        assertEquals(50, NoteMapper.midiFromName("D", 3)) // D3
        assertEquals(71, NoteMapper.midiFromName("B", 4)) // B4
    }

    @Test
    fun rejectsUnparseableInput() {
        assertNull(NoteMapper.midiFromName("", 4))
        assertNull(NoteMapper.midiFromName("  ", 4))
        assertNull(NoteMapper.midiFromName("H4", 4))
        assertNull(NoteMapper.midiFromName("C4.5", 4))
        assertNull(NoteMapper.midiFromName("CC4", 4))
        assertNull(NoteMapper.midiFromName("4E", 4))
    }

    @Test
    fun rejectsTheoreticalSpellings() {
        assertNull(NoteMapper.midiFromName("E#4", 4))
        assertNull(NoteMapper.midiFromName("B#3", 4))
        assertNull(NoteMapper.midiFromName("Cb4", 4))
        assertNull(NoteMapper.midiFromName("Fb3", 4))
    }

    @Test
    fun rejectsMidiOutside0To127() {
        assertNull(NoteMapper.midiFromName("C-2", 4)) // -12
        assertNull(NoteMapper.midiFromName("G10", 4)) // 139
        assertEquals(12, NoteMapper.midiFromName("C0", 4)) // boundary, valid MIDI
        assertEquals(127, NoteMapper.midiFromName("G9", 4)) // boundary, valid MIDI
    }
}
