package com.example.tunner.ui

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.tunner.ui.theme.TunerFlat
import com.example.tunner.ui.theme.TunerSharp
import kotlin.math.abs

internal const val GAUGE_MIN_CENTS = -50.0
internal const val GAUGE_MAX_CENTS = 50.0
internal const val GAUGE_IN_TUNE_CENTS = 5.0

internal enum class GaugeEdge { NONE, LOW, HIGH }
internal enum class GaugeTone { WAITING, FLAT, IN_TUNE, SHARP }

internal data class GaugeReading(
    val positionFraction: Float,
    val displayedCents: Double?,
    val edge: GaugeEdge,
    val tone: GaugeTone,
)

internal fun gaugeReading(cents: Double?): GaugeReading {
    if (cents == null || !cents.isFinite()) {
        return GaugeReading(0.5f, null, GaugeEdge.NONE, GaugeTone.WAITING)
    }
    val inTune = abs(cents) <= GAUGE_IN_TUNE_CENTS
    val displayed = if (inTune) 0.0 else cents.coerceIn(GAUGE_MIN_CENTS, GAUGE_MAX_CENTS)
    val fraction = ((displayed - GAUGE_MIN_CENTS) / (GAUGE_MAX_CENTS - GAUGE_MIN_CENTS)).toFloat()
    return GaugeReading(
        positionFraction = fraction,
        displayedCents = displayed,
        edge = when {
            cents < GAUGE_MIN_CENTS -> GaugeEdge.LOW
            cents > GAUGE_MAX_CENTS -> GaugeEdge.HIGH
            else -> GaugeEdge.NONE
        },
        tone = when {
            inTune -> GaugeTone.IN_TUNE
            cents < 0.0 -> GaugeTone.FLAT
            else -> GaugeTone.SHARP
        },
    )
}

internal fun shouldTriggerGaugePulse(previousTick: Int, newTick: Int): Boolean =
    newTick > 0 && newTick != previousTick

/** Fixed precision scale with a moving luminous pitch cursor. */
@Composable
fun TunerGauge(
    cents: Double?,
    flashTick: Int = 0,
    modifier: Modifier = Modifier,
) {
    val reading = gaugeReading(cents)
    val animatedPosition by animateFloatAsState(
        targetValue = reading.positionFraction,
        animationSpec = tween(durationMillis = 90),
        label = "gaugeCursor",
    )
    val pulse = remember { Animatable(1f) }
    var previousFlashTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(flashTick) {
        if (shouldTriggerGaugePulse(previousFlashTick, flashTick)) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(durationMillis = 420))
        }
        previousFlashTick = flashTick
    }

    val accent = MaterialTheme.colorScheme.primary
    val cursorColor = when (reading.tone) {
        GaugeTone.WAITING -> MaterialTheme.colorScheme.onSurfaceVariant
        GaugeTone.FLAT -> TunerFlat
        GaugeTone.IN_TUNE -> accent
        GaugeTone.SHARP -> TunerSharp
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val minorTick = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
    val majorTick = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    val centerColor = MaterialTheme.colorScheme.onBackground
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accessibilityText = when {
        cents == null -> "调音仪表，等待输入"
        reading.tone == GaugeTone.IN_TUNE -> "调音仪表，已调准"
        reading.edge == GaugeEdge.LOW -> "调音仪表，低于负五十音分"
        reading.edge == GaugeEdge.HIGH -> "调音仪表，高于正五十音分"
        cents < 0.0 -> "调音仪表，偏低 ${formatCents(cents)}"
        else -> "调音仪表，偏高 ${formatCents(cents)}"
    }

    Canvas(
        modifier = modifier
            .semantics { contentDescription = accessibilityText }
            .graphicsLayer { clip = false },
    ) {
        val horizontalPadding = 18.dp.toPx()
        val left = horizontalPadding
        val right = size.width - horizontalPadding
        val usableWidth = (right - left).coerceAtLeast(1f)
        val railY = size.height * 0.47f
        val labelBaseline = size.height - 3.dp.toPx()

        drawLine(trackColor, Offset(left, railY), Offset(right, railY), 8.dp.toPx(), StrokeCap.Round)
        drawLine(
            accent.copy(alpha = 0.22f),
            Offset(left + usableWidth * 0.45f, railY),
            Offset(left + usableWidth * 0.55f, railY),
            10.dp.toPx(),
            StrokeCap.Round,
        )

        for (tick in -50..50 step 5) {
            val x = left + usableWidth * ((tick + 50) / 100f)
            val major = tick % 10 == 0
            val center = tick == 0
            val halfHeight = when {
                center -> 25.dp.toPx()
                major -> 17.dp.toPx()
                else -> 11.dp.toPx()
            }
            drawLine(
                color = when {
                    center -> centerColor.copy(alpha = 0.9f)
                    major -> majorTick
                    else -> minorTick
                },
                start = Offset(x, railY - halfHeight),
                end = Offset(x, railY + halfHeight),
                strokeWidth = when {
                    center -> 2.dp.toPx()
                    major -> 1.5.dp.toPx()
                    else -> 1.dp.toPx()
                },
                cap = StrokeCap.Round,
            )
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor.toArgbInt()
            textSize = 11.dp.toPx()
            textAlign = Paint.Align.CENTER
        }
        listOf(-50, -25, 0, 25, 50).forEach { value ->
            val x = left + usableWidth * ((value + 50) / 100f)
            drawContext.canvas.nativeCanvas.drawText(
                if (value > 0) "+$value" else value.toString(), x, labelBaseline, textPaint,
            )
        }

        if (reading.displayedCents != null) {
            val cursorX = left + usableWidth * animatedPosition
            val cursorTop = railY - 29.dp.toPx()
            val cursorBottom = railY + 26.dp.toPx()
            drawLine(cursorColor.copy(alpha = 0.12f), Offset(cursorX, cursorTop - 4.dp.toPx()), Offset(cursorX, cursorBottom + 4.dp.toPx()), 16.dp.toPx(), StrokeCap.Round)
            drawLine(cursorColor.copy(alpha = 0.32f), Offset(cursorX, cursorTop), Offset(cursorX, cursorBottom), 8.dp.toPx(), StrokeCap.Round)
            drawLine(cursorColor, Offset(cursorX, cursorTop), Offset(cursorX, cursorBottom), 3.dp.toPx(), StrokeCap.Round)
            drawCircle(cursorColor.copy(alpha = 0.22f), 12.dp.toPx(), Offset(cursorX, railY))
            drawCircle(cursorColor, 4.dp.toPx(), Offset(cursorX, railY))

            if (reading.edge != GaugeEdge.NONE) {
                val direction = if (reading.edge == GaugeEdge.LOW) -1f else 1f
                val tipX = cursorX + direction * 10.dp.toPx()
                val baseX = cursorX + direction * 2.dp.toPx()
                val arrow = Path().apply {
                    moveTo(tipX, railY)
                    lineTo(baseX, railY - 6.dp.toPx())
                    lineTo(baseX, railY + 6.dp.toPx())
                    close()
                }
                drawPath(arrow, cursorColor)
            }
        }

        if (pulse.value < 1f) {
            val progress = pulse.value
            val center = Offset(left + usableWidth * 0.5f, railY)
            val pulseAlpha = (1f - progress) * 0.62f
            drawCircle(
                accent.copy(alpha = pulseAlpha),
                (7.dp + 31.dp * progress).toPx(),
                center,
                style = Stroke((3.dp * (1f - progress) + 1.dp).toPx()),
            )
            drawLine(
                accent.copy(alpha = pulseAlpha * 0.6f),
                Offset(center.x, railY - (28.dp + 12.dp * progress).toPx()),
                Offset(center.x, railY + (28.dp + 12.dp * progress).toPx()),
                8.dp.toPx(),
                StrokeCap.Round,
            )
        }
    }
}

private fun Color.toArgbInt(): Int =
    ((alpha * 255).toInt() shl 24) or
        ((red * 255).toInt() shl 16) or
        ((green * 255).toInt() shl 8) or
        (blue * 255).toInt()
