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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.tunner.settings.GaugeStyle
import com.example.tunner.ui.theme.TunerFlat
import com.example.tunner.ui.theme.TunerSharp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal const val GAUGE_MIN_CENTS = -50.0
internal const val GAUGE_MAX_CENTS = 50.0
internal const val GAUGE_IN_TUNE_CENTS = 5.0

internal enum class GaugeEdge { NONE, LOW, HIGH }
internal enum class GaugeTone { WAITING, FLAT, IN_TUNE, SHARP }

internal data class GaugeReading(
    val positionFraction: Float,
    val displayedCents: Double?,
    val actualCents: Double?,
    val edge: GaugeEdge,
    val tone: GaugeTone,
)

internal fun gaugeReading(cents: Double?): GaugeReading {
    if (cents == null || !cents.isFinite()) {
        return GaugeReading(0.5f, null, null, GaugeEdge.NONE, GaugeTone.WAITING)
    }
    val inTune = abs(cents) <= GAUGE_IN_TUNE_CENTS
    val displayed = if (inTune) 0.0 else cents.coerceIn(GAUGE_MIN_CENTS, GAUGE_MAX_CENTS)
    val fraction = ((displayed - GAUGE_MIN_CENTS) / (GAUGE_MAX_CENTS - GAUGE_MIN_CENTS)).toFloat()
    return GaugeReading(
        positionFraction = fraction,
        displayedCents = displayed,
        actualCents = cents,
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

/** Maps a 0..1 scale fraction to a 200° arc centered at 270°. */
internal fun gaugeAngle(positionFraction: Float): Float =
    170f + positionFraction.coerceIn(0f, 1f) * 200f

/** Shared palette passed down to both gauge styles. */
internal class GaugeColors(
    val accent: Color,
    val cursor: Color,
    val track: Color,
    val minorTick: Color,
    val majorTick: Color,
    val center: Color,
    val label: Color,
)

/**
 * Tuner gauge in two switchable styles sharing the same flat visual language
 * (fixed -50..+50 cents scale, luminous cursor, in-tune pulse):
 *  - [GaugeStyle.RAIL]: horizontal precision scale with a moving cursor.
 *  - [GaugeStyle.DIAL]: semicircular dial with a radial luminous cursor.
 */
@Composable
fun TunerGauge(
    cents: Double?,
    flashTick: Int = 0,
    style: GaugeStyle = GaugeStyle.RAIL,
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
    val colors = GaugeColors(
        accent = accent,
        cursor = cursorColor,
        track = MaterialTheme.colorScheme.surfaceVariant,
        minorTick = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
        majorTick = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
        center = MaterialTheme.colorScheme.onBackground,
        label = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
        when (style) {
            GaugeStyle.RAIL -> drawRailGauge(reading, animatedPosition, pulse.value, colors)
            GaugeStyle.DIAL -> drawDialGauge(reading, animatedPosition, pulse.value, colors)
        }
    }
}

/** Horizontal precision rail: track, in-tune zone, ticks, labels, luminous cursor. */
private fun DrawScope.drawRailGauge(
    reading: GaugeReading,
    animatedPosition: Float,
    pulseValue: Float,
    c: GaugeColors,
) {
    val horizontalPadding = 18.dp.toPx()
    val left = horizontalPadding
    val right = size.width - horizontalPadding
    val usableWidth = (right - left).coerceAtLeast(1f)
    val railY = size.height * 0.47f
    val labelBaseline = size.height - 3.dp.toPx()

    drawLine(c.track, Offset(left, railY), Offset(right, railY), 8.dp.toPx(), StrokeCap.Round)
    drawLine(
        c.accent.copy(alpha = 0.22f),
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
                center -> c.center.copy(alpha = 0.9f)
                major -> c.majorTick
                else -> c.minorTick
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
        color = c.label.toArgbInt()
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
        drawLine(c.cursor.copy(alpha = 0.12f), Offset(cursorX, cursorTop - 4.dp.toPx()), Offset(cursorX, cursorBottom + 4.dp.toPx()), 16.dp.toPx(), StrokeCap.Round)
        drawLine(c.cursor.copy(alpha = 0.32f), Offset(cursorX, cursorTop), Offset(cursorX, cursorBottom), 8.dp.toPx(), StrokeCap.Round)
        drawLine(c.cursor, Offset(cursorX, cursorTop), Offset(cursorX, cursorBottom), 3.dp.toPx(), StrokeCap.Round)
        drawCircle(c.cursor.copy(alpha = 0.22f), 12.dp.toPx(), Offset(cursorX, railY))
        drawCircle(c.cursor, 4.dp.toPx(), Offset(cursorX, railY))

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
            drawPath(arrow, c.cursor)
        }
    }

    if (pulseValue < 1f) {
        val progress = pulseValue
        val center = Offset(left + usableWidth * 0.5f, railY)
        val pulseAlpha = (1f - progress) * 0.62f
        drawCircle(
            c.accent.copy(alpha = pulseAlpha),
            (7.dp + 31.dp * progress).toPx(),
            center,
            style = Stroke((3.dp * (1f - progress) + 1.dp).toPx()),
        )
        drawLine(
            c.accent.copy(alpha = pulseAlpha * 0.6f),
            Offset(center.x, railY - (28.dp + 12.dp * progress).toPx()),
            Offset(center.x, railY + (28.dp + 12.dp * progress).toPx()),
            8.dp.toPx(),
            StrokeCap.Round,
        )
    }
}

/** Semicircular dial: arc track, in-tune zone, radial ticks, labels, radial cursor. */
private fun DrawScope.drawDialGauge(
    reading: GaugeReading,
    animatedPosition: Float,
    pulseValue: Float,
    c: GaugeColors,
) {
    val cx = size.width / 2f
    val cy = size.height * 0.82f
    val radius = min(size.width * 0.42f, size.height * 0.70f).coerceAtLeast(48.dp.toPx())
    val topLeft = Offset(cx - radius, cy - radius)
    val arcSize = Size(radius * 2f, radius * 2f)

    // A restrained instrument face gives the dial visual mass without adding a card border.
    drawCircle(
        color = c.track.copy(alpha = 0.30f),
        radius = radius * 0.91f,
        center = Offset(cx, cy),
    )
    drawArc(
        color = c.majorTick.copy(alpha = 0.16f),
        startAngle = 170f,
        sweepAngle = 200f,
        useCenter = false,
        topLeft = Offset(cx - radius * 0.84f, cy - radius * 0.84f),
        size = Size(radius * 1.68f, radius * 1.68f),
        style = Stroke(width = 1.dp.toPx()),
    )

    // A 200° outer track has more presence than a compressed half-circle.
    drawArc(
        color = c.track,
        startAngle = 170f,
        sweepAngle = 200f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round),
    )

    val zoneSweep = ((GAUGE_IN_TUNE_CENTS * 2.0 / (GAUGE_MAX_CENTS - GAUGE_MIN_CENTS)) * 200.0).toFloat()
    drawArc(
        color = c.accent.copy(alpha = 0.38f),
        startAngle = 270f - zoneSweep / 2f,
        sweepAngle = zoneSweep,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = 13.dp.toPx(), cap = StrokeCap.Round),
    )

    for (tick in -50..50 step 5) {
        val major = tick % 10 == 0
        val center = tick == 0
        val angle = gaugeAngle((tick + 50) / 100f)
        val rad = Math.toRadians(angle.toDouble())
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        val inner = when {
            center -> radius - 46.dp.toPx()
            major -> radius - 39.dp.toPx()
            else -> radius - 29.dp.toPx()
        }
        val outer = radius - 14.dp.toPx()
        drawLine(
            color = when {
                center -> c.center.copy(alpha = 0.9f)
                major -> c.majorTick
                else -> c.minorTick
            },
            start = Offset(cx + inner * cosA, cy + inner * sinA),
            end = Offset(cx + outer * cosA, cy + outer * sinA),
            strokeWidth = when {
                center -> 2.dp.toPx()
                major -> 1.5.dp.toPx()
                else -> 1.dp.toPx()
            },
            cap = StrokeCap.Round,
        )
    }

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = c.label.toArgbInt()
        textSize = 12.dp.toPx()
        textAlign = Paint.Align.CENTER
    }
    val labelRadius = radius * 0.66f
    listOf(-50, -25, 0, 25, 50).forEach { value ->
        val angle = gaugeAngle((value + 50) / 100f)
        val rad = Math.toRadians(angle.toDouble())
        val x = cx + labelRadius * cos(rad).toFloat()
        val y = cy + labelRadius * sin(rad).toFloat() + 4.dp.toPx()
        drawContext.canvas.nativeCanvas.drawText(
            if (value > 0) "+$value" else value.toString(), x, y, textPaint,
        )
    }

    // Digital cents capsule makes the large dial useful even before reading the needle precisely.
    val valueText = reading.actualCents?.let(::formatCents) ?: "—"
    val capsuleWidth = 86.dp.toPx()
    val capsuleHeight = 30.dp.toPx()
    val capsuleCenterY = cy - radius * 0.30f
    drawRoundRect(
        color = c.track.copy(alpha = 0.94f),
        topLeft = Offset(cx - capsuleWidth / 2f, capsuleCenterY - capsuleHeight / 2f),
        size = Size(capsuleWidth, capsuleHeight),
        cornerRadius = CornerRadius(15.dp.toPx()),
    )
    val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (reading.displayedCents == null) c.label.copy(alpha = 0.55f).toArgbInt() else c.cursor.toArgbInt()
        textSize = 15.dp.toPx()
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    drawContext.canvas.nativeCanvas.drawText(
        valueText,
        cx,
        capsuleCenterY - (valuePaint.ascent() + valuePaint.descent()) / 2f,
        valuePaint,
    )

    if (reading.displayedCents != null) {
        val angle = gaugeAngle(animatedPosition)
        val rad = Math.toRadians(angle.toDouble())
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        val needleLength = radius - 20.dp.toPx()
        val tip = Offset(cx + needleLength * cosA, cy + needleLength * sinA)
        val pivot = Offset(cx, cy)
        val counterweight = Offset(cx - 22.dp.toPx() * cosA, cy - 22.dp.toPx() * sinA)
        drawLine(c.cursor.copy(alpha = 0.12f), pivot, tip, 16.dp.toPx(), StrokeCap.Round)
        drawLine(c.cursor.copy(alpha = 0.32f), pivot, tip, 8.dp.toPx(), StrokeCap.Round)
        drawLine(c.cursor, pivot, tip, 3.dp.toPx(), StrokeCap.Round)
        drawLine(c.cursor.copy(alpha = 0.75f), pivot, counterweight, 4.dp.toPx(), StrokeCap.Round)
        drawCircle(c.cursor.copy(alpha = 0.22f), 12.dp.toPx(), tip)
        drawCircle(c.cursor, 4.dp.toPx(), tip)
        drawCircle(c.cursor.copy(alpha = 0.18f), 16.dp.toPx(), pivot)
        drawCircle(c.track, 9.dp.toPx(), pivot)
        drawCircle(c.cursor, 5.dp.toPx(), pivot)

        if (reading.edge != GaugeEdge.NONE) {
            val edgeAngle = if (reading.edge == GaugeEdge.LOW) 170f else 370f
            val edgeRad = Math.toRadians(edgeAngle.toDouble())
            val edgeCos = cos(edgeRad).toFloat()
            val edgeSin = sin(edgeRad).toFloat()
            val arrowTip = Offset(cx + (radius + 5.dp.toPx()) * edgeCos, cy + (radius + 5.dp.toPx()) * edgeSin)
            val arrowBase = Offset(cx + (radius - 5.dp.toPx()) * edgeCos, cy + (radius - 5.dp.toPx()) * edgeSin)
            val tangentX = -edgeSin * 6.dp.toPx()
            val tangentY = edgeCos * 6.dp.toPx()
            val arrow = Path().apply {
                moveTo(arrowTip.x, arrowTip.y)
                lineTo(arrowBase.x + tangentX, arrowBase.y + tangentY)
                lineTo(arrowBase.x - tangentX, arrowBase.y - tangentY)
                close()
            }
            drawPath(arrow, c.cursor)
        }
    }

    if (pulseValue < 1f) {
        val progress = pulseValue
        val pivot = Offset(cx, cy)
        val pulseAlpha = (1f - progress) * 0.62f
        drawCircle(
            c.accent.copy(alpha = pulseAlpha),
            (7.dp + 31.dp * progress).toPx(),
            pivot,
            style = Stroke((3.dp * (1f - progress) + 1.dp).toPx()),
        )
        drawLine(
            c.accent.copy(alpha = pulseAlpha * 0.6f),
            Offset(cx, cy - (28.dp + 12.dp * progress).toPx()),
            Offset(cx, cy - 2.dp.toPx()),
            8.dp.toPx(),
            StrokeCap.Round,
        )
    }
}

private fun Color.toArgbInt(): Int =
    ((alpha * 255).toInt() shl 24) or
        ((red * 255).toInt() shl 16) or
        ((green * 255).toInt() shl 8) or
        (blue * 255).toInt()
