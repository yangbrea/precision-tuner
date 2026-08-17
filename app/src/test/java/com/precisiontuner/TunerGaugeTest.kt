package com.precisiontuner

import com.precisiontuner.ui.GaugeEdge
import com.precisiontuner.ui.GaugeTone
import com.precisiontuner.ui.gaugeAngle
import com.precisiontuner.ui.gaugeReading
import com.precisiontuner.ui.shouldTriggerGaugePulse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TunerGaugeTest {
    @Test fun `key cents values map linearly across the rail`() {
        assertEquals(0f, gaugeReading(-50.0).positionFraction, 0.0001f)
        assertEquals(0.25f, gaugeReading(-25.0).positionFraction, 0.0001f)
        assertEquals(0.5f, gaugeReading(0.0).positionFraction, 0.0001f)
        assertEquals(0.75f, gaugeReading(25.0).positionFraction, 0.0001f)
        assertEquals(1f, gaugeReading(50.0).positionFraction, 0.0001f)
    }

    @Test fun `out of range values clamp and expose direction`() {
        assertEquals(0f, gaugeReading(-80.0).positionFraction, 0.0001f)
        assertEquals(GaugeEdge.LOW, gaugeReading(-80.0).edge)
        assertEquals(1f, gaugeReading(72.0).positionFraction, 0.0001f)
        assertEquals(GaugeEdge.HIGH, gaugeReading(72.0).edge)
    }

    @Test fun `in tune window snaps to center inclusively`() {
        listOf(-5.0, -2.4, 0.0, 4.9, 5.0).forEach { cents ->
            val reading = gaugeReading(cents)
            assertEquals(0.5f, reading.positionFraction, 0.0001f)
            assertEquals(GaugeTone.IN_TUNE, reading.tone)
        }
        assertTrue(gaugeReading(-5.1).positionFraction < 0.5f)
        assertTrue(gaugeReading(5.1).positionFraction > 0.5f)
    }

    @Test fun `waiting state has no active reading`() {
        val reading = gaugeReading(null)
        assertNull(reading.displayedCents)
        assertEquals(GaugeTone.WAITING, reading.tone)
    }

    @Test fun `flat and sharp states retain semantic color state`() {
        assertEquals(GaugeTone.FLAT, gaugeReading(-18.0).tone)
        assertEquals(GaugeTone.SHARP, gaugeReading(18.0).tone)
    }

    @Test fun `pulse only triggers for a new positive tick`() {
        assertFalse(shouldTriggerGaugePulse(0, 0))
        assertTrue(shouldTriggerGaugePulse(0, 1))
        assertFalse(shouldTriggerGaugePulse(1, 1))
        assertTrue(shouldTriggerGaugePulse(1, 2))
    }

    @Test fun `dial angle maps rail fraction to the centered 200 degree arc`() {
        assertEquals(170f, gaugeAngle(0f), 0.0001f) // -50¢, lower-left end
        assertEquals(220f, gaugeAngle(0.25f), 0.0001f)
        assertEquals(270f, gaugeAngle(0.5f), 0.0001f) // 0¢, top
        assertEquals(320f, gaugeAngle(0.75f), 0.0001f)
        assertEquals(370f, gaugeAngle(1f), 0.0001f) // +50¢, lower-right end
    }

    @Test fun `dial angle clamps out of range fractions`() {
        assertEquals(170f, gaugeAngle(-0.5f), 0.0001f)
        assertEquals(370f, gaugeAngle(1.5f), 0.0001f)
    }
}
