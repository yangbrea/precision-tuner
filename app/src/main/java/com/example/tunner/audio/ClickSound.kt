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

    /**
     * Generates a sharp, bright "ding" cue: a decaying tone with harmonic
     * partials (2nd/3rd/5th) on top of the fundamental, so it cuts through
     * with a piercing, bell-like edge instead of a soft pure sine.
     *
     * @param frequency  fundamental frequency in Hz.
     * @param durationMs ding length in ms.
     * @param amplitude  0..1 peak amplitude.
     */
    fun generateDing(frequency: Double, durationMs: Double = 140.0, amplitude: Double = 0.4): ShortArray {
        val n = (durationMs / 1000.0 * SAMPLE_RATE).toInt().coerceAtLeast(1)
        // (partial multiplier to gain): 3rd harmonic adds the sharp/piercing
        // edge, 2nd and 5th add metallic shimmer.
        val partials = listOf(1.0 to 1.0, 2.0 to 0.25, 3.0 to 0.15, 5.0 to 0.08)
        val norm = partials.sumOf { it.second }
        return ShortArray(n) { i ->
            val t = i / SAMPLE_RATE.toDouble()
            val env = exp(-t * DECAY_RATE)
            val wave = partials.sumOf { (mult, gain) -> gain * sin(2.0 * PI * frequency * mult * t) } / norm
            val v = amplitude * env * wave
            (v * Short.MAX_VALUE).toInt().toShort()
        }
    }
}
