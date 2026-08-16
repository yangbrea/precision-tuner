package com.example.tunner.audio

import kotlin.math.abs

/**
 * Downsamples a 16-bit PCM frame to [points] normalized floats and applies
 * per-frame automatic gain so the waveform fills ~90% of the display range.
 *
 * Without gain, real microphone input (typically 5%-20% of full scale) would
 * render as a near-flat line. Purely functional for tests.
 */
fun downsampleWaveform(buffer: ShortArray, points: Int = 256): List<Float> {
    if (buffer.isEmpty() || points <= 0) return emptyList()
    val stride = maxOf(1, buffer.size / points)
    val raw = List(points) { i ->
        buffer[(i * stride).coerceAtMost(buffer.size - 1)] / 32768f
    }
    val peak = raw.maxOfOrNull { abs(it) } ?: 0f
    if (peak < MIN_PEAK) return raw // silence: leave near-flat
    val gain = TARGET_PEAK / peak
    return raw.map { (it * gain).coerceIn(-1f, 1f) }
}

private const val TARGET_PEAK = 0.9f
private const val MIN_PEAK = 0.01f
