package com.example.tunner

import com.example.tunner.audio.downsampleWaveform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class WaveformTest {

    @Test
    fun returnsRequestedPoints() {
        val buf = ShortArray(4096)
        assertEquals(256, downsampleWaveform(buf, 256).size)
        assertEquals(128, downsampleWaveform(buf, 128).size)
    }

    @Test
    fun normalizesToUnityRange() {
        val buf = ShortArray(4096) { i ->
            (sin(2.0 * PI * i / 256.0) * 32767.0).toInt().toShort()
        }
        val w = downsampleWaveform(buf, 256)
        assertTrue(w.all { it in -1f..1f })
    }

    @Test
    fun decimatesCorrectly() {
        // Ramp 0..4095 → stride 16 → w[i] = buf[i*16]/32768.
        val buf = ShortArray(4096) { it.toShort() }
        val w = downsampleWaveform(buf, 256)
        assertEquals(0f, w[0], 1e-6f)
        assertEquals(16 / 32768f, w[1], 1e-6f)
        assertEquals(255 * 16 / 32768f, w[255], 1e-6f)
    }

    @Test
    fun emptyBufferReturnsEmpty() {
        assertTrue(downsampleWaveform(ShortArray(0), 256).isEmpty())
    }
}
