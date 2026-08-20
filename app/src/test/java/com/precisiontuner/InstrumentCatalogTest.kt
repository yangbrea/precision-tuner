package com.precisiontuner

import com.precisiontuner.tuning.InstrumentCatalog
import com.precisiontuner.tuning.InstrumentString
import com.precisiontuner.tuning.NoteMapper
import com.precisiontuner.tuning.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class InstrumentCatalogTest {

    @Test
    fun everyInstrumentHasATunableTuning() {
        InstrumentCatalog.instruments.forEach { inst ->
            assertTrue("${inst.id} has no tunings", inst.tunings.isNotEmpty())
            assertNotNull("${inst.id} default tuning missing", inst.defaultTuning())
            inst.tunings.forEach { t ->
                assertTrue("${inst.id}/${t.id} has empty strings", t.strings.isNotEmpty())
            }
        }
    }

    @Test
    fun guitarNearestString() {
        val g = InstrumentCatalog.tuning("guitar", "standard")!!
        assertEquals(6, g.nearestString(40)?.number) // E2 -> string 6
        assertEquals(1, g.nearestString(64)?.number) // E4 -> string 1
        assertEquals(5, g.nearestString(45)?.number) // A2 -> string 5
    }

    @Test
    fun nearestStringUsesFrequencyDistanceWithinCurrentTuning() {
        val guitar = InstrumentCatalog.tuning("guitar", "standard")!!
        val dropD = InstrumentCatalog.tuning("guitar", "drop_d")!!
        val halfDown = InstrumentCatalog.tuning("guitar", "half_down")!!
        val ukulele = InstrumentCatalog.tuning("ukulele", "standard")!!

        assertEquals("E2", guitar.nearestString(82.41)?.fullNote)
        assertEquals("D2", dropD.nearestString(73.42)?.fullNote)
        assertEquals("D#2", halfDown.nearestString(77.78)?.fullNote)
        assertEquals("C4", ukulele.nearestString(261.63)?.fullNote)
    }

    @Test
    fun e2CannotBeInTuneForViolin() {
        val violin = InstrumentCatalog.tuning("violin", "standard")!!
        val target = violin.nearestString(82.41)!!
        val cents = target.centsFrom(82.41)

        assertEquals("G3", target.fullNote)
        assertTrue(abs(cents) > 5.0)
    }

    @Test
    fun invalidFrequencyHasNoTarget() {
        val tuning = InstrumentCatalog.tuning("guitar", "standard")!!
        assertNull(tuning.nearestString(0.0))
        assertNull(tuning.nearestString(Double.NaN))
    }

    @Test
    fun customTuningUsesItsOwnTargets() {
        val custom = Tuning(
            id = "custom",
            name = "Custom",
            strings = listOf(
                InstrumentString(1, "C", "C4", 60),
                InstrumentString(2, "F", "F3", 53),
            ),
        )

        val target = custom.nearestString(175.0)!!
        assertEquals("F3", target.fullNote)
        assertTrue(abs(target.centsFrom(175.0)) < 5.0)
    }

    @Test
    fun frequencyFromMidi() {
        val e2 = InstrumentCatalog.tuning("guitar", "standard")!!.byNumber(6)!!
        assertEquals(82.41, e2.frequency, 0.02)
        assertEquals(NoteMapper.frequencyFromMidi(e2.midi), e2.frequency, 1e-9)
    }

    @Test
    fun frequencyFollowsA4Reference() {
        val a4 = InstrumentCatalog.tuning("violin", "standard")!!.byNumber(2)!! // A4
        assertEquals(440.0, a4.frequency(440.0), 1e-9)
        assertEquals(466.0, a4.frequency(466.0), 1e-9)
        assertEquals(415.0, a4.frequency(415.0), 1e-9)
        // Cents deviation is measured against the calibrated target: a 466 Hz
        // tone is exactly in tune only when the reference is 466.
        assertEquals(0.0, a4.centsFrom(466.0, 466.0), 0.01)
        assertTrue(abs(a4.centsFrom(466.0, 440.0)) > 95.0)
    }

    @Test
    fun tuningFallsBackToDefault() {
        val t = InstrumentCatalog.tuning("violin", "nonexistent")!!
        assertEquals("standard", t.id)
        assertEquals(4, t.strings.size)
    }

    @Test
    fun unknownInstrumentIsNull() {
        assertNull(InstrumentCatalog.instrument("piano"))
        assertNull(InstrumentCatalog.tuning("piano", "standard"))
    }
}
