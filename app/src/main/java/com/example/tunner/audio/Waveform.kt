package com.example.tunner.audio

/**
 * Downsamples a 16-bit PCM frame to [points] normalized floats in [-1, 1].
 *
 * Uses simple decimation (one sample per stride). Purely functional for tests.
 */
fun downsampleWaveform(buffer: ShortArray, points: Int = 256): List<Float> {
    if (buffer.isEmpty() || points <= 0) return emptyList()
    val stride = maxOf(1, buffer.size / points)
    return List(points) { i ->
        buffer[(i * stride).coerceAtMost(buffer.size - 1)] / 32768f
    }
}
