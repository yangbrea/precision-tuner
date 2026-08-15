package com.example.tunner

import com.example.tunner.tuning.InstrumentCatalog
import com.example.tunner.tuning.NoteMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun frequencyFromMidi() {
        val e2 = InstrumentCatalog.tuning("guitar", "standard")!!.byNumber(6)!!
        assertEquals(82.41, e2.frequency, 0.02)
        assertEquals(NoteMapper.frequencyFromMidi(e2.midi), e2.frequency, 1e-9)
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
