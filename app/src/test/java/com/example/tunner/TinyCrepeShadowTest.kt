package com.example.tunner

import com.example.tunner.pitch.TinyCrepeResult
import com.example.tunner.pitch.TinyCrepeShadow
import com.example.tunner.pitch.TinyCrepeShadowMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class TinyCrepeShadowTest {
    @Test fun `resampler creates normalized 1024 sample frame`() {
        val pcm = ShortArray(4096) { index ->
            (sin(2.0 * PI * 164.81 * index / 44_100.0) * 12_000).toInt().toShort()
        }
        val output = FloatArray(TinyCrepeShadow.INPUT_SIZE)
        TinyCrepeShadow.prepareInput(pcm, 44_100, output)
        val mean = output.average()
        val deviation = sqrt(output.sumOf { (it - mean) * (it - mean) } / output.size)
        assertEquals(0.0, mean, 1e-5)
        assertEquals(1.0, deviation, 1e-4)
        assertTrue(output.all(Float::isFinite))
    }

    @Test fun `silence normalization remains finite`() {
        val output = FloatArray(TinyCrepeShadow.INPUT_SIZE)
        TinyCrepeShadow.prepareInput(ShortArray(4096), 44_100, output)
        assertTrue(output.all { it == 0f })
    }

    @Test fun `shadow metrics classify agreement octave and unvoiced without arbitration`() {
        val metrics = TinyCrepeShadowMetrics()
        fun result(frequency: Double, confidence: Double = 0.9, ms: Double = 10.0) =
            TinyCrepeResult(frequency, confidence, FloatArray(360), ms)
        metrics.observe(result(164.81, ms = 8.0), 164.82)
        metrics.observe(result(82.405, ms = 12.0), 164.81)
        metrics.observe(result(164.81, confidence = 0.2), null)
        val snapshot = metrics.snapshot()
        assertEquals(3, snapshot.frames)
        assertEquals(1, snapshot.agreement)
        assertEquals(1, snapshot.octaveConflict)
        assertEquals(1, snapshot.neuralUnvoiced)
        assertEquals(1, snapshot.dspUnvoiced)
        assertEquals(12.0, snapshot.maxMs, 0.001)
    }
}
