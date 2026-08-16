package com.example.tunner.audio

/**
 * Downsamples a 16-bit PCM frame to [points] normalized floats and applies a
 * fixed [gain] so quiet microphone input is visible.
 *
 * Fixed (not adaptive) gain preserves the pluck's amplitude envelope; values
 * are clamped to [-1, 1]. Purely functional for tests.
 */
fun downsampleWaveform(
    buffer: ShortArray,
    points: Int = 256,
    gain: Float = FIXED_GAIN,
): List<Float> {
    if (buffer.isEmpty() || points <= 0) return emptyList()
    val stride = maxOf(1, buffer.size / points)
    return List(points) { i ->
        (buffer[(i * stride).coerceAtMost(buffer.size - 1)] / 32768f * gain)
            .coerceIn(-1f, 1f)
    }
}

private const val FIXED_GAIN = 24.0f
