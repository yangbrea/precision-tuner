package com.example.tunner

import com.example.tunner.audio.ClickSound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ClickSoundTest {

    @Test
    fun correctLength() {
        val buf = ClickSound.generate(2000.0, durationMs = 30.0)
        assertEquals((30.0 / 1000.0 * ClickSound.SAMPLE_RATE).toInt(), buf.size)
    }

    @Test
    fun decaysToNearZero() {
        val buf = ClickSound.generate(1100.0, durationMs = 30.0)
        assertEquals(0, buf.first().toInt()) // sine starts at phase 0
        assertTrue("last sample should be near zero", abs(buf.last().toInt()) < 2000)
    }

    @Test
    fun accentDiffersFromNormal() {
        val a = ClickSound.generate(ClickSound.ACCENT_FREQ)
        val n = ClickSound.generate(ClickSound.NORMAL_FREQ)
        assertFalse(a.contentEquals(n))
    }

    @Test
    fun nonSilent() {
        val buf = ClickSound.generate(2000.0, amplitude = 0.8)
        val peak = buf.maxOf { abs(it.toInt()) }
        assertTrue("click should not be silent (peak=$peak)", peak > 10000)
    }
}
