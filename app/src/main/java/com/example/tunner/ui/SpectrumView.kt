package com.example.tunner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tunner.pitch.Spectrum
import com.example.tunner.tuning.NoteMapper

/**
 * Live frequency-spectrum visualization: a bar chart of the normalized
 * magnitude bands (0 .. [Spectrum.MAX_HZ] Hz), a vertical marker at the
 * detected fundamental, and note-name labels on the strongest peaks.
 */
@Composable
fun SpectrumView(
    spectrum: List<Float>,
    detectedFrequency: Double?,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val barColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onBackground
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor, fontWeight = FontWeight.Medium)

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

        // Annotate the strongest peaks with their note names.
        val peaks = findPeaks(spectrum, maxPeaks = 5, threshold = 0.25f)
        peaks.forEach { idx ->
            val freq = idx * Spectrum.MAX_HZ / n
            val note = NoteMapper.noteFromFrequency(freq)
            val label = "${note.name}${note.octave}"
            val layout = textMeasurer.measure(AnnotatedString(label), style = labelStyle)
            val x = (idx * barWidth).coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
            drawText(layout, topLeft = Offset(x, 0f))
        }
    }
}

private fun findPeaks(spectrum: List<Float>, maxPeaks: Int, threshold: Float): List<Int> {
    val candidates = mutableListOf<Pair<Int, Float>>()
    for (i in 1 until spectrum.size - 1) {
        val v = spectrum[i]
        if (v >= threshold && v >= spectrum[i - 1] && v >= spectrum[i + 1]) {
            candidates.add(i to v)
        }
    }
    return candidates.sortedByDescending { it.second }.take(maxPeaks).map { it.first }
}
