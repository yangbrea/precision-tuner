package com.example.tunner.pitch

import kotlin.math.min

/**
 * Spectrum visualization constants and helpers.
 *
 * The detector produces a fixed number of display bands spanning [0, MAX_HZ],
 * normalized so the tallest band is 1.0.
 */
object Spectrum {
    const val BANDS = 128
    const val MAX_HZ = 1500.0

    /**
     * Collapse a half-FFT magnitude spectrum ([magnitude], one entry per bin)
     * into [BANDS] display bands using max-pooling, then normalize to [0, 1].
     *
     * @param fftSize   the full FFT length (2 × [magnitude].size).
     */
    fun build(magnitude: DoubleArray, sampleRate: Int, fftSize: Int): FloatArray {
        val bands = FloatArray(BANDS)
        val binHz = sampleRate.toDouble() / fftSize
        val maxBin = min(magnitude.size, (MAX_HZ / binHz).toInt())
        for (b in 1 until maxBin) {
            val freq = b * binHz
            val idx = (freq / MAX_HZ * BANDS).toInt().coerceIn(0, BANDS - 1)
            val v = magnitude[b].toFloat()
            if (v > bands[idx]) bands[idx] = v
        }
        val peak = bands.maxOrNull() ?: 0f
        if (peak > 0f) {
            for (i in bands.indices) bands[i] /= peak
        }
        return bands
    }
}
