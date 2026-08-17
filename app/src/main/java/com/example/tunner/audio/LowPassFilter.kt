package com.example.tunner.audio

import kotlin.math.PI
import kotlin.math.exp

/**
 * One-pole IIR low-pass filter applied to the PCM stream before pitch detection.
 *
 * A stronger filter removes more high-frequency noise / harmonics, which helps
 * the detector lock onto the fundamental (especially on low guitar strings).
 *
 * [strength] is 0..1: 0 bypasses the filter entirely; otherwise it maps to a
 * cutoff frequency from 12000 Hz down to 400 Hz on a logarithmic curve
 * (`cutoff = 12000 * (400/12000)^strength`), so the default 0.5 sits at
 * ~2.2 kHz instead of the old linear 6.2 kHz and actually filters something.
 *
 * The filter is stateful across frames; changing the strength resets its state.
 */
class LowPassFilter(private val sampleRate: Double = 44100.0) {

    private var previous = 0.0
    private var currentStrength = -1f
    private var alpha = 1.0

    fun process(buffer: ShortArray, strength: Float) {
        applyStrength(strength)
        if (strength <= 0f) return // bypass (state is untouched)

        for (i in buffer.indices) {
            val x = buffer[i].toDouble()
            val y = previous + alpha * (x - previous)
            previous = y
            buffer[i] = y.coerceIn(SHORT_MIN, SHORT_MAX).toInt().toShort()
        }
    }

    private fun applyStrength(strength: Float) {
        if (strength == currentStrength) return
        currentStrength = strength
        val cutoff = if (strength <= 0f) {
            Double.POSITIVE_INFINITY
        } else {
            // Logarithmic map: 12 kHz at 0+ (weak, nearly no filtering), 400 Hz
            // at 1 (strong). The default 0.5 → ~2.2 kHz, low enough to cut the
            // harmonic cloud above the fundamental.
            MAX_CUTOFF * Math.pow(MIN_CUTOFF / MAX_CUTOFF, strength.toDouble())
        }
        alpha = if (cutoff == Double.POSITIVE_INFINITY) {
            1.0
        } else {
            1.0 - exp(-2.0 * PI * cutoff / sampleRate)
        }
        // Reset state only when the coefficient changes, so that a running
        // stream stays phase-continuous across frames.
        previous = 0.0
    }

    private companion object {
        const val MIN_CUTOFF = 400.0     // Hz at strength = 1
        const val MAX_CUTOFF = 12000.0   // Hz at strength -> 0+
        const val SHORT_MIN = -32768.0
        const val SHORT_MAX = 32767.0
    }
}
