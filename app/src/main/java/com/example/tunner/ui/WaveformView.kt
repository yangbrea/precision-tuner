package com.example.tunner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Time-domain waveform view: draws the normalized audio samples as a polyline
 * around a center baseline.
 */
@Composable
fun WaveformView(
    waveform: List<Float>,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.primary
    val baseline = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier) {
        if (waveform.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val midY = h / 2f
        val halfH = h / 2f

        drawLine(
            color = baseline,
            start = Offset(0f, midY),
            end = Offset(w, midY),
            strokeWidth = 1.dp.toPx(),
        )

        val path = Path()
        val step = w / (waveform.size - 1)
        waveform.forEachIndexed { i, v ->
            val x = i * step
            val y = midY - v.coerceIn(-1f, 1f) * halfH
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = color, style = Stroke(width = 1.5.dp.toPx()))
    }
}
