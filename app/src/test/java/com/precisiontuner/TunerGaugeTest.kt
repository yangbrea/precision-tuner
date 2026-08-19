package com.precisiontuner

import com.precisiontuner.ui.GaugeEdge
import com.precisiontuner.ui.GaugeTone
import com.precisiontuner.ui.MAX_PITCH_SAMPLES
import com.precisiontuner.ui.centGridX
import com.precisiontuner.ui.gaugeAngle
import com.precisiontuner.ui.gaugeReading
import com.precisiontuner.ui.pushPitchSample
import com.precisiontuner.ui.railCursorX
import com.precisiontuner.ui.shouldTriggerGaugePulse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TunerGaugeTest {
    @Test fun `key cents values map linearly across the rail`() {
        assertEquals(0f, gaugeReading(-50.0, TuneVisualState.HIGH).positionFraction, 0.0001f)
        assertEquals(0.25f, gaugeReading(-25.0, TuneVisualState.HIGH).positionFraction, 0.0001f)
        assertEquals(0.5f, gaugeReading(0.0, TuneVisualState.HIGH).positionFraction, 0.0001f)
        assertEquals(0.75f, gaugeReading(25.0, TuneVisualState.HIGH).positionFraction, 0.0001f)
        assertEquals(1f, gaugeReading(50.0, TuneVisualState.HIGH).positionFraction, 0.0001f)
    }

    @Test fun `out of range values clamp and expose direction`() {
        assertEquals(0f, gaugeReading(-80.0, TuneVisualState.LOW).positionFraction, 0.0001f)
        assertEquals(GaugeEdge.LOW, gaugeReading(-80.0, TuneVisualState.LOW).edge)
        assertEquals(1f, gaugeReading(72.0, TuneVisualState.HIGH).positionFraction, 0.0001f)
        assertEquals(GaugeEdge.HIGH, gaugeReading(72.0, TuneVisualState.HIGH).edge)
    }

    @Test fun `stable in tune visual state snaps to center regardless of raw cents`() {
        listOf(-5.0, -2.4, 0.0, 4.9, 5.0, 6.5).forEach { cents ->
            val reading = gaugeReading(cents, TuneVisualState.IN_TUNE)
            assertEquals(0.5f, reading.positionFraction, 0.0001f)
            assertEquals(GaugeTone.IN_TUNE, reading.tone)
        }
    }

    @Test fun `unconfirmed center input keeps smooth raw movement`() {
        // While the 4-frame confirmation is running the cursor must not snap.
        val sharp = gaugeReading(4.9, TuneVisualState.HIGH)
        assertTrue(sharp.positionFraction > 0.5f)
        assertEquals(GaugeTone.SHARP, sharp.tone)
        val flat = gaugeReading(-4.9, TuneVisualState.LOW)
        assertTrue(flat.positionFraction < 0.5f)
        assertEquals(GaugeTone.FLAT, flat.tone)
        assertTrue(gaugeReading(5.1, TuneVisualState.HIGH).positionFraction > 0.5f)
    }

    @Test fun `waiting state has no active reading`() {
        val reading = gaugeReading(null, TuneVisualState.WAITING)
        assertNull(reading.displayedCents)
        assertEquals(GaugeTone.WAITING, reading.tone)
    }

    @Test fun `flat and sharp states retain semantic color state`() {
        assertEquals(GaugeTone.FLAT, gaugeReading(-18.0, TuneVisualState.LOW).tone)
        assertEquals(GaugeTone.SHARP, gaugeReading(18.0, TuneVisualState.HIGH).tone)
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

    @Test fun `rail cursor maps fractions linearly across the track`() {
        val left = 100f
        val width = 800f
        assertEquals(100f, railCursorX(0f, left, width), 0.0001f)
        assertEquals(500f, railCursorX(0.5f, left, width), 0.0001f)
        assertEquals(900f, railCursorX(1f, left, width), 0.0001f)
        assertEquals(700f, railCursorX(0.75f, left, width), 0.0001f)
    }

    @Test fun `rail cursor clamps out of range fractions`() {
        assertEquals(100f, railCursorX(-0.5f, 100f, 800f), 0.0001f)
        assertEquals(900f, railCursorX(1.5f, 100f, 800f), 0.0001f)
    }

    @Test fun `cent grid maps cents to positions on the waterfall`() {
        assertEquals(500f, centGridX(0f, 500f, 800f), 0.0001f) // center
        assertEquals(100f, centGridX(-50f, 500f, 800f), 0.0001f) // left end
        assertEquals(900f, centGridX(50f, 500f, 800f), 0.0001f) // right end
        assertEquals(300f, centGridX(-25f, 500f, 800f), 0.0001f) // half left
        assertEquals(700f, centGridX(25f, 500f, 800f), 0.0001f) // half right
    }

    @Test fun `pitch waterfall prepends newest sample and caps length`() {
        var history: List<Float> = emptyList()
        for (i in 0 until 100) {
            history = pushPitchSample(history, i * 0.01f)
        }
        assertEquals(MAX_PITCH_SAMPLES, history.size)
        assertEquals(99 * 0.01f, history.first(), 0.0001f) // newest at index 0
        assertEquals((100 - MAX_PITCH_SAMPLES) * 0.01f, history.last(), 0.0001f)
    }

    @Test fun `pitch waterfall keeps silence gaps as NaN`() {
        var history = pushPitchSample(emptyList(), Float.NaN)
        history = pushPitchSample(history, 0.5f)
        history = pushPitchSample(history, 0.6f)
        assertEquals(3, history.size)
        assertTrue(!history[0].isNaN()) // newest = 0.6
        assertTrue(!history[1].isNaN()) // 0.5
        assertTrue(history[2].isNaN()) // oldest silence gap
    }
}
