package com.example.tunner.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.tunner.ui.theme.TunerFlat
import com.example.tunner.ui.theme.TunerSharp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val MIN_CENTS = -50.0
private const val MAX_CENTS = 50.0
private const val IN_TUNE_CENTS = 5.0

/**
 * Semicircular needle gauge spanning -50..+50 cents.
 *
 * @param cents current deviation, or null when no confident pitch is detected
 *              (the needle rests at center in a muted color).
 */
@Composable
fun TunerGauge(
    cents: Double?,
    modifier: Modifier = Modifier,
) {
    val inTune = cents != null && abs(cents) <= IN_TUNE_CENTS
    val accent = MaterialTheme.colorScheme.primary
    val needleColor = when {
        cents == null -> MaterialTheme.colorScheme.onSurfaceVariant
        inTune -> accent
        cents < 0 -> TunerFlat
        else -> TunerSharp
    }

    // Smooth the needle: animate toward the current (clamped) reading so it
    // glides instead of snapping on every frame.
    val targetCents = (cents?.coerceIn(MIN_CENTS, MAX_CENTS) ?: 0.0).toFloat()
    val animatedCents by animateFloatAsState(
        targetValue = targetCents,
        animationSpec = tween(durationMillis = 90),
        label = "needle",
    )

    // Colors read before the Canvas (not accessible inside DrawScope).
    val dialColor = MaterialTheme.colorScheme.surfaceVariant
    val tickCenterColor = MaterialTheme.colorScheme.onBackground
    val tickMinorColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height * 0.88f
        val radius = minOf(size.width * 0.5f, size.height * 0.82f)

        // Dial arc.
        drawArc(
            color = dialColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(cx - radius, cy - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round),
        )

        // Green in-tune zone centered on 0 cents.
        val greenHalfSweep = ((IN_TUNE_CENTS / (MAX_CENTS - MIN_CENTS)) * 180.0 / 2.0).toFloat()
        drawArc(
            color = accent.copy(alpha = 0.35f),
            startAngle = 270f - greenHalfSweep,
            sweepAngle = greenHalfSweep * 2f,
            useCenter = false,
            topLeft = Offset(cx - radius, cy - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round),
        )

        // Ticks every 5 cents (major every 10).
        val tickOuter = radius - 14.dp.toPx()
        var c = MIN_CENTS.toInt()
        while (c <= MAX_CENTS.toInt()) {
            val major = c % 10 == 0
            val inner = if (major) radius - 36.dp.toPx() else radius - 28.dp.toPx()
            val angle = angleFor(c.toDouble())
            val p1 = pointAt(cx, cy, tickOuter, angle)
            val p2 = pointAt(cx, cy, inner, angle)
            val tickColor = if (c == 0) tickCenterColor else tickMinorColor
            drawLine(
                color = tickColor,
                start = p1,
                end = p2,
                strokeWidth = if (major) 2.5.dp.toPx() else 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
            c += 5
        }

        // Needle (uses the animated value for smooth motion).
        val tip = pointAt(cx, cy, radius - 28.dp.toPx(), angleFor(animatedCents.toDouble()))
        drawLine(
            color = needleColor,
            start = Offset(cx, cy),
            end = tip,
            strokeWidth = 5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(color = needleColor, radius = 7.dp.toPx(), center = Offset(cx, cy))
    }
}

private fun angleFor(cents: Double): Double {
    val t = ((cents - MIN_CENTS) / (MAX_CENTS - MIN_CENTS)).coerceIn(0.0, 1.0)
    // 180° = left, 270° = top, 360° = right (Compose y-axis points down).
    return 180.0 + t * 180.0
}

private fun pointAt(cx: Float, cy: Float, r: Float, angleDeg: Double): Offset {
    val rad = Math.toRadians(angleDeg)
    return Offset(
        (cx + r * cos(rad)).toFloat(),
        (cy + r * sin(rad)).toFloat(),
    )
}
