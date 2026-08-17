package com.precisiontuner

import com.precisiontuner.tuning.NoteMapper
import com.precisiontuner.tuning.Temperament
import com.precisiontuner.tuning.Temperaments
import org.junit.Assert.assertEquals
import org.junit.Test

class TemperamentTest {

    // ---- reference anchor: A4 is 0 cents in every temperament ----

    @Test
    fun a4IsZeroCentsInEveryTemperament() {
        Temperament.entries.forEach { t ->
            val note = Temperaments.nearestNote(440.0, 440.0, t)
            assertEquals("A", note.name)
            assertEquals(4, note.octave)
            assertEquals(0.0, note.cents, 0.001)
        }
    }

    @Test
    fun referenceShiftAppliesEqually() {
        Temperament.entries.forEach { t ->
            assertEquals(7.85, Temperaments.nearestNote(442.0, 440.0, t).cents, 0.01)
        }
    }

    // ---- equal temperament keeps the classic 12-TET mapping ----

    @Test
    fun equalTemperamentMatchesNoteMapper() {
        val note = Temperaments.nearestNote(329.63, 440.0, Temperament.EQUAL)
        assertEquals("E", note.name)
        assertEquals(4, note.octave)
        assertEquals(64, note.midi)
        assertEquals(0.0, note.cents, 0.1) // 329.63 is ET E4 rounded to 2 decimals
    }

    // ---- an ET-tuned note read against the other temperaments ----

    @Test
    fun etD4InPythagoreanIsNearlyInTune() {
        // Pythagorean D = 9/8 → target 293.33 Hz (ET D4 = 293.66).
        val note = Temperaments.nearestNote(293.66, 440.0, Temperament.PYTHAGOREAN)
        assertEquals("D", note.name)
        assertEquals(1.96, note.cents, 0.1)
    }

    @Test
    fun etD4InJustIsFlatByTheSyntonicComma() {
        // Just D = 9/8 over just C (264 Hz) → target 297.0 Hz; the ET D is
        // roughly a syntonic comma flat of it.
        val note = Temperaments.nearestNote(293.66, 440.0, Temperament.JUST)
        assertEquals("D", note.name)
        assertEquals(-19.58, note.cents, 0.1)
    }

    @Test
    fun etE4IsNearlyInTuneInBothNonEqualScales() {
        // E targets 330 Hz in both scales (ET E4 = 329.63).
        listOf(Temperament.PYTHAGOREAN, Temperament.JUST).forEach { t ->
            val note = Temperaments.nearestNote(329.63, 440.0, t)
            assertEquals("E", note.name)
            assertEquals(-1.96, note.cents, 0.1)
        }
    }

    @Test
    fun etC4DeviatesDifferentlyPerScale() {
        val pyth = Temperaments.nearestNote(261.63, 440.0, Temperament.PYTHAGOREAN)
        assertEquals("C", pyth.name)
        assertEquals(5.9, pyth.cents, 0.1) // Pythagorean C4 = 260.74 Hz

        val just = Temperaments.nearestNote(261.63, 440.0, Temperament.JUST)
        assertEquals("C", just.name)
        assertEquals(-15.6, just.cents, 0.1) // just C4 = 264 Hz
    }

    @Test
    fun justBb4SitsWellAboveEqualBb4() {
        // Just A#/Bb4 target = 440 * (9/5) / (5/3) = 475.2 Hz.
        val note = Temperaments.nearestNote(466.16, 440.0, Temperament.JUST)
        assertEquals("A#", note.name)
        assertEquals(-33.25, note.cents, 0.1)
    }

    // ---- octave / midi correctness ----

    @Test
    fun justE4ResolvesToCorrectOctaveAndMidi() {
        val note = Temperaments.nearestNote(330.0, 440.0, Temperament.JUST)
        assertEquals("E", note.name)
        assertEquals(4, note.octave)
        assertEquals(64, note.midi)
        assertEquals(0.0, note.cents, 0.001)
    }

    @Test
    fun pythagoreanC3IsRecognizedAtLowOctave() {
        // Pythagorean C4 = 260.74 Hz, so 130.37 Hz is its C3 (the scale defines
        // its own octave grid, distinct from 12-TET C3 = 130.81 Hz).
        val note = Temperaments.nearestNote(130.37, 440.0, Temperament.PYTHAGOREAN)
        assertEquals("C", note.name)
        assertEquals(3, note.octave)
        assertEquals(0.0, note.cents, 0.1)
    }

    // ---- frequency() mirrors nearestNote targets ----

    @Test
    fun frequencyMatchesAnchoredTargets() {
        assertEquals(440.0, Temperaments.frequency(Temperament.JUST, 9, 4, 440.0), 0.001)
        assertEquals(330.0, Temperaments.frequency(Temperament.JUST, 4, 4, 440.0), 0.001)
        assertEquals(330.0, Temperaments.frequency(Temperament.PYTHAGOREAN, 4, 4, 440.0), 0.001)
        assertEquals(293.33, Temperaments.frequency(Temperament.PYTHAGOREAN, 2, 4, 440.0), 0.01)
        assertEquals(264.0, Temperaments.frequency(Temperament.JUST, 0, 4, 440.0), 0.001)
        // Equal temperament frequency is identical to the classic formula.
        assertEquals(
            NoteMapper.frequencyFromMidi(69),
            Temperaments.frequency(Temperament.EQUAL, 9, 4, 440.0),
            0.001,
        )
    }
}
