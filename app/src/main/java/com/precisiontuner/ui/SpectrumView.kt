package com.precisiontuner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.precisiontuner.pitch.Spectrum

/**
 * Live frequency-spectrum visualization: a bar chart of the normalized
 * magnitude bands (0 .. [Spectrum.MAX_HZ] Hz) and a vertical marker at the
 * detected fundamental.
 */
@Composable
fun SpectrumView(
    spectrum: List<Float>,
    detectedFrequency: Double?,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val barColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier) {
        if (spectrum.isEmpty()) return@Canvas

        val n = spectrum.size
        val barWidth = size.width / n
        val gap = barWidth * 0.3f
        for (i in 0 until n) {
            val magnitude = spectrum[i].coerceIn(0f, 1f)
            val h = magnitude * size.height
            if (h <= 0f) continue
            drawRect(
                color = barColor,
                topLeft = Offset(i * barWidth + gap / 2f, size.height - h),
                size = Size((barWidth - gap).coerceAtLeast(0.5f), h),
            )
        }

        if (detectedFrequency != null) {
            val x = (detectedFrequency / Spectrum.MAX_HZ).toFloat()
                .coerceIn(0f, 1f) * size.width
            drawLine(
                color = accent,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 2.dp.toPx(),
            )
        }
    }
}
