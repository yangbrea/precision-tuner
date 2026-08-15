package com.example.tunner.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Generates a short metronome "click" as a decaying sine burst. Pure function,
 * JVM-testable (no Android APIs).
 */
object ClickSound {

    const val SAMPLE_RATE = 44100
    const val ACCENT_FREQ = 2000.0 // beat 1 (accent)
    const val NORMAL_FREQ = 1100.0 // other beats
    const val SUBDIVISION_FREQ = 800.0 // sub-beats (softer)

    private const val DECAY_RATE = 200.0 // ~63% decay in 5 ms

    /**
     * @param frequency  sine frequency in Hz.
     * @param durationMs click length in ms.
     * @param amplitude  0..1 peak amplitude.
     */
    fun generate(frequency: Double, durationMs: Double = 30.0, amplitude: Double = 0.8): ShortArray {
        val n = (durationMs / 1000.0 * SAMPLE_RATE).toInt().coerceAtLeast(1)
        return ShortArray(n) { i ->
            val t = i / SAMPLE_RATE.toDouble()
            val env = exp(-t * DECAY_RATE)
            val v = amplitude * env * sin(2.0 * PI * frequency * t)
            (v * Short.MAX_VALUE).toInt().toShort()
        }
    }
}
