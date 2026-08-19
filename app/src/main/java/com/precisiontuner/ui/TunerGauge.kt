package com.precisiontuner.ui

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
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
import com.precisiontuner.TuneVisualState
import com.precisiontuner.settings.GaugeStyle
import com.precisiontuner.ui.theme.TunerFlat
import com.precisiontuner.ui.theme.TunerSharp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal const val GAUGE_MIN_CENTS = -50.0
internal const val GAUGE_MAX_CENTS = 50.0
internal const val GAUGE_IN_TUNE_CENTS = 5.0

/**
 * How many pitch samples the waterfall keeps on screen (one per frame, so 72
 * samples ≈ 1.2 s of pitch history scrolling down the view).
 */
internal const val MAX_PITCH_SAMPLES = 72

internal enum class GaugeEdge { NONE, LOW, HIGH }
internal enum class GaugeTone { WAITING, FLAT, IN_TUNE, SHARP }

internal data class GaugeReading(
    val positionFraction: Float,
    val displayedCents: Double?,
    val actualCents: Double?,
    val edge: GaugeEdge,
    val tone: GaugeTone,
)

/**
 * Prepends [fraction] (0..1, or NaN for no signal) as the newest sample of a pitch
 * waterfall history, dropping the oldest so the buffer stays capped. The newest
 * sample always lives at index 0 (the "now" edge of the waterfall).
 */
internal fun pushPitchSample(history: List<Float>, fraction: Float): List<Float> {
    val next = ArrayList<Float>(history.size + 1)
    next.add(fraction)
    next.addAll(history)
    while (next.size > MAX_PITCH_SAMPLES) next.removeAt(next.size - 1)
    return next
}

/**
 * Maps the raw cents plus the stabilized visual verdict to a gauge reading.
 *
 * The cursor only snaps to the center after [TuneVisualState.IN_TUNE] is
 * confirmed; during the ~180 ms confirmation it keeps following the raw cents
 * so the movement stays smooth. The tone (cursor color / semantics) always
 * comes from the stabilized [visualState], never from raw [cents].
 */
internal fun gaugeReading(cents: Double?, visualState: TuneVisualState): GaugeReading {
    if (cents == null || !cents.isFinite()) {
        return GaugeReading(0.5f, null, null, GaugeEdge.NONE, GaugeTone.WAITING)
    }
    val inTune = visualState == TuneVisualState.IN_TUNE
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
        tone = when (visualState) {
            TuneVisualState.WAITING -> GaugeTone.WAITING
            TuneVisualState.LOW -> GaugeTone.FLAT
            TuneVisualState.IN_TUNE -> GaugeTone.IN_TUNE
            TuneVisualState.HIGH -> GaugeTone.SHARP
        },
    )
}

internal fun shouldTriggerGaugePulse(previousTick: Int, newTick: Int): Boolean =
    newTick > 0 && newTick != previousTick

/** Maps a 0..1 scale fraction to a 200° arc centered at 270°. */
internal fun gaugeAngle(positionFraction: Float): Float =
    170f + positionFraction.coerceIn(0f, 1f) * 200f

/** Maps a 0..1 scale fraction to its horizontal cursor position on the rail. */
internal fun railCursorX(positionFraction: Float, left: Float, usableWidth: Float): Float =
    left + positionFraction.coerceIn(0f, 1f) * usableWidth

/** Maps a cents value to its horizontal grid position on the pitch waterfall. */
internal fun centGridX(cent: Float, centerX: Float, bandWidth: Float): Float =
    centerX + (cent / GAUGE_MAX_CENTS.toFloat()) * (bandWidth / 2f)

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
 * Tuner gauge in three switchable styles sharing the same flat visual language
 * (fixed -50..+50 cents scale, luminous cursor, in-tune pulse):
 *  - [GaugeStyle.RAIL]: horizontal precision scale with a moving cursor.
 *  - [GaugeStyle.DIAL]: semicircular dial with a radial luminous cursor.
 *  - [GaugeStyle.TRAIL]: scrolling pitch waterfall — the live pitch trace flows
 *    down the panel like a waterfall toward the fixed target-pitch center line.
 */
@Composable
fun TunerGauge(
    cents: Double?,
    flashTick: Int = 0,
    style: GaugeStyle = GaugeStyle.RAIL,
    visualState: TuneVisualState = TuneVisualState.WAITING,
    modifier: Modifier = Modifier,
) {
    val reading = gaugeReading(cents, visualState)
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

    // Pitch waterfall history for the TRAIL style: one sample per frame at the
    // "now" (top) edge, older samples scrolling down and fading. NaN marks
    // silence so the trace breaks across gaps. Runs only while TRAIL is active
    // and resets when the style changes.
    val currentCents by rememberUpdatedState(cents)
    var pitchHistory by remember { mutableStateOf(emptyList<Float>()) }
    LaunchedEffect(style) {
        pitchHistory = emptyList()
        if (style != GaugeStyle.TRAIL) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val sample = currentCents?.takeIf { it.isFinite() }?.let {
                ((it.coerceIn(GAUGE_MIN_CENTS, GAUGE_MAX_CENTS) - GAUGE_MIN_CENTS) /
                    (GAUGE_MAX_CENTS - GAUGE_MIN_CENTS)).toFloat()
            } ?: Float.NaN
            pitchHistory = pushPitchSample(pitchHistory, sample)
        }
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
        reading.tone == GaugeTone.FLAT -> "调音仪表，偏低 ${formatCents(cents)}"
        reading.tone == GaugeTone.SHARP -> "调音仪表，偏高 ${formatCents(cents)}"
        else -> "调音仪表，等待输入"
    }

    Canvas(
        modifier = modifier
            .semantics { contentDescription = accessibilityText }
            .graphicsLayer { clip = false },
    ) {
        when (style) {
            GaugeStyle.RAIL -> drawRailGauge(reading, animatedPosition, pulse.value, colors)
            GaugeStyle.DIAL -> drawDialGauge(reading, animatedPosition, pulse.value, colors)
            GaugeStyle.TRAIL -> drawWaterfallGauge(reading, pulse.value, pitchHistory, colors)
        }
    }
}

/** Geometry shared by the rail and the trail gauge (both are horizontal). */
private class RailGeometry(
    val left: Float,
    val right: Float,
    val usableWidth: Float,
    val railY: Float,
    val labelBaseline: Float,
)

private fun DrawScope.railGeometry(): RailGeometry {
    val horizontalPadding = 18.dp.toPx()
    val left = horizontalPadding
    val right = size.width - horizontalPadding
    return RailGeometry(
        left = left,
        right = right,
        usableWidth = (right - left).coerceAtLeast(1f),
        railY = size.height * 0.47f,
        labelBaseline = size.height - 3.dp.toPx(),
    )
}

/** Static rail: track, in-tune zone, ticks, labels. The scale never moves. */
private fun DrawScope.drawRailBackground(
    c: GaugeColors,
    g: RailGeometry,
) {
    val railY = g.railY

    drawLine(c.track, Offset(g.left, railY), Offset(g.right, railY), 8.dp.toPx(), StrokeCap.Round)
    drawLine(
        c.accent.copy(alpha = 0.22f),
        Offset(g.left + g.usableWidth * 0.45f, railY),
        Offset(g.left + g.usableWidth * 0.55f, railY),
        10.dp.toPx(),
        StrokeCap.Round,
    )

    for (tick in -50..50 step 5) {
        val x = railCursorX((tick + 50) / 100f, g.left, g.usableWidth)
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
        val x = railCursorX((value + 50) / 100f, g.left, g.usableWidth)
        drawContext.canvas.nativeCanvas.drawText(
            if (value > 0) "+$value" else value.toString(), x, g.labelBaseline, textPaint,
        )
    }
}

/** Luminous moving cursor plus the out-of-range edge arrow. */
private fun DrawScope.drawRailCursor(
    animatedPosition: Float,
    reading: GaugeReading,
    c: GaugeColors,
    g: RailGeometry,
) {
    val cursorX = railCursorX(animatedPosition, g.left, g.usableWidth)
    val railY = g.railY
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

/** Expanding in-tune pulse radiating from the rail center. */
private fun DrawScope.drawRailPulse(
    pulseValue: Float,
    c: GaugeColors,
    g: RailGeometry,
) {
    val progress = pulseValue
    val center = Offset(g.left + g.usableWidth * 0.5f, g.railY)
    val pulseAlpha = (1f - progress) * 0.62f
    drawCircle(
        c.accent.copy(alpha = pulseAlpha),
        (7.dp + 31.dp * progress).toPx(),
        center,
        style = Stroke((3.dp * (1f - progress) + 1.dp).toPx()),
    )
    drawLine(
        c.accent.copy(alpha = pulseAlpha * 0.6f),
        Offset(center.x, g.railY - (28.dp + 12.dp * progress).toPx()),
        Offset(center.x, g.railY + (28.dp + 12.dp * progress).toPx()),
        8.dp.toPx(),
        StrokeCap.Round,
    )
}

/** Horizontal precision rail: fixed scale, luminous moving cursor, in-tune pulse. */
private fun DrawScope.drawRailGauge(
    reading: GaugeReading,
    animatedPosition: Float,
    pulseValue: Float,
    c: GaugeColors,
) {
    val g = railGeometry()
    drawRailBackground(c, g)
    if (reading.displayedCents != null) {
        drawRailCursor(animatedPosition, reading, c, g)
    }
    if (pulseValue < 1f) {
        drawRailPulse(pulseValue, c, g)
    }
}

/** Geometry of the pitch-waterfall view. */
private class WaterfallGeometry(
    val centerX: Float,
    val top: Float,
    val scrollHeight: Float,
    val bandWidth: Float,
)

private fun DrawScope.waterfallGeometry(): WaterfallGeometry {
    val horizontalPadding = 12.dp.toPx()
    val left = horizontalPadding
    val right = size.width - horizontalPadding
    val top = 26.dp.toPx() // newest sample sits below the panel top, leaving breathing room
    val bottom = size.height - 10.dp.toPx()
    return WaterfallGeometry(
        centerX = size.width / 2f,
        top = top,
        scrollHeight = (bottom - top).coerceAtLeast(1f),
        bandWidth = (right - left).coerceAtLeast(1f),
    )
}

/**
 * Pitch waterfall: the live cents trace is sampled every frame at the top ("now")
 * edge and scrolls downward, fading with age — new data appears near the fixed
 * target-pitch reference line, old data flows away like a waterfall. When the
 * pitch is stable the trace collapses into a straight vertical "water flow" on
 * the center line; flat sits left of it, sharp right.
 */
private fun DrawScope.drawPitchWaterfall(
    history: List<Float>,
    inTune: Boolean,
    c: GaugeColors,
    g: WaterfallGeometry,
) {
    // Grid: vertical cent lines every 10¢ (0¢ is the reference line below).
    for (cent in -50..50 step 10) {
        if (cent == 0) continue
        val major = cent % 25 == 0
        val x = centGridX(cent.toFloat(), g.centerX, g.bandWidth)
        drawLine(
            color = if (major) c.majorTick.copy(alpha = 0.38f) else c.minorTick.copy(alpha = 0.25f),
            start = Offset(x, g.top),
            end = Offset(x, g.top + g.scrollHeight),
            strokeWidth = 1.dp.toPx(),
        )
    }
    // Grid: horizontal time lines at quarter-height steps so the scrolling flow
    // has reference points.
    for (quarter in 1..3) {
        val y = g.top + g.scrollHeight * quarter / 4f
        drawLine(
            color = c.minorTick.copy(alpha = 0.25f),
            start = Offset(g.centerX - g.bandWidth / 2f, y),
            end = Offset(g.centerX + g.bandWidth / 2f, y),
            strokeWidth = 1.dp.toPx(),
        )
    }
    // Cent labels in the top padding, above the "now" edge.
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = c.label.toArgbInt()
        textSize = 10.dp.toPx()
        textAlign = Paint.Align.CENTER
    }
    val labelY = g.top - 14.dp.toPx()
    listOf(-50f, -25f, 0f, 25f, 50f).forEach { value ->
        val x = centGridX(value, g.centerX, g.bandWidth)
        drawContext.canvas.nativeCanvas.drawText(
            if (value > 0) "+${value.toInt()}" else value.toInt().toString(),
            x, labelY, textPaint,
        )
    }

    // Fixed target-pitch reference line (0¢), glowing when in tune.
    drawLine(
        color = if (inTune) c.accent.copy(alpha = 0.60f) else c.majorTick.copy(alpha = 0.30f),
        start = Offset(g.centerX, g.top),
        end = Offset(g.centerX, g.top + g.scrollHeight),
        strokeWidth = if (inTune) 2.5.dp.toPx() else 1.5.dp.toPx(),
        cap = StrokeCap.Round,
    )
    // Faint "now" edge where new samples enter.
    drawLine(
        color = c.minorTick.copy(alpha = 0.45f),
        start = Offset(g.centerX - g.bandWidth / 2f, g.top),
        end = Offset(g.centerX + g.bandWidth / 2f, g.top),
        strokeWidth = 1.dp.toPx(),
    )

    val n = history.size
    for (i in 0 until n - 1) {
        val cur = history[i]
        val nxt = history[i + 1]
        if (cur.isNaN() || nxt.isNaN()) continue
        val age = i.toFloat() / MAX_PITCH_SAMPLES
        val x0 = g.centerX + (cur - 0.5f) * g.bandWidth
        val y0 = g.top + age * g.scrollHeight
        val x1 = g.centerX + (nxt - 0.5f) * g.bandWidth
        val y1 = g.top + (i + 1).toFloat() / MAX_PITCH_SAMPLES * g.scrollHeight
        val alpha = ((1f - age) * 0.85f).coerceIn(0f, 1f)
        drawLine(
            color = c.cursor.copy(alpha = alpha),
            start = Offset(x0, y0),
            end = Offset(x1, y1),
            strokeWidth = (1f + 3.5f * (1f - age)).dp.toPx(),
            cap = StrokeCap.Round,
        )
    }

    // Glowing head at the newest sample — the live "pointer" on the now edge.
    val head = history.firstOrNull()
    if (head != null && !head.isNaN()) {
        val x = g.centerX + (head - 0.5f) * g.bandWidth
        drawCircle(c.cursor.copy(alpha = 0.25f), 10.dp.toPx(), Offset(x, g.top))
        drawCircle(c.cursor, 4.5.dp.toPx(), Offset(x, g.top))
    }
}

/**
 * TRAIL style: a scrolling pitch waterfall. The live pitch is drawn as a
 * continuous glowing trajectory flowing down the panel; a fixed vertical line
 * marks the target pitch. Flat pulls the stream left of the line, sharp pulls it
 * right, and a steady in-tune note forms a straight glowing "water flow" on it.
 */
private fun DrawScope.drawWaterfallGauge(
    reading: GaugeReading,
    pulseValue: Float,
    history: List<Float>,
    c: GaugeColors,
) {
    val g = waterfallGeometry()
    drawPitchWaterfall(history, reading.tone == GaugeTone.IN_TUNE, c, g)
    if (pulseValue < 1f) {
        drawCenterPulse(pulseValue, c, Offset(g.centerX, g.top + g.scrollHeight / 2f))
    }
}

/** Geometry of the semicircular dial. */
private class DialGeometry(val cx: Float, val cy: Float, val radius: Float)

private fun DrawScope.dialGeometry(): DialGeometry {
    val cx = size.width / 2f
    val cy = size.height * 0.82f
    val radius = min(size.width * 0.42f, size.height * 0.70f).coerceAtLeast(48.dp.toPx())
    return DialGeometry(cx, cy, radius)
}

/** Semicircular dial: arc track, in-tune zone, radial ticks, labels, digital capsule. */
private fun DrawScope.drawDialBackground(
    reading: GaugeReading,
    c: GaugeColors,
    g: DialGeometry,
) {
    val cx = g.cx
    val cy = g.cy
    val radius = g.radius
    val topLeft = Offset(cx - radius, cy - radius)
    val arcSize = Size(radius * 2f, radius * 2f)

    // A restrained instrument face gives the dial visual mass without adding a
    // card border. Drawn as a 200° pie sector matching the arc, so no circular
    // mask shows below the dial in dark mode.
    val faceRadius = radius * 0.91f
    val face = Path().apply {
        moveTo(cx, cy)
        arcTo(
            rect = Rect(cx - faceRadius, cy - faceRadius, cx + faceRadius, cy + faceRadius),
            startAngleDegrees = 170f,
            sweepAngleDegrees = 200f,
            forceMoveTo = false,
        )
        close()
    }
    drawPath(face, c.track.copy(alpha = 0.30f))
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
}

/** Radial luminous needle plus the out-of-range edge arrow. */
private fun DrawScope.drawDialNeedle(
    animatedPosition: Float,
    reading: GaugeReading,
    c: GaugeColors,
    g: DialGeometry,
) {
    val cx = g.cx
    val cy = g.cy
    val radius = g.radius
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

/** Expanding in-tune pulse circle radiating from a pivot. */
private fun DrawScope.drawCenterPulse(
    pulseValue: Float,
    c: GaugeColors,
    pivot: Offset,
) {
    val cx = pivot.x
    val cy = pivot.y
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

/** Semicircular dial: shared background, needle, in-tune pulse. */
private fun DrawScope.drawDialGauge(
    reading: GaugeReading,
    animatedPosition: Float,
    pulseValue: Float,
    c: GaugeColors,
) {
    val g = dialGeometry()
    drawDialBackground(reading, c, g)
    if (reading.displayedCents != null) {
        drawDialNeedle(animatedPosition, reading, c, g)
    }
    if (pulseValue < 1f) {
        drawCenterPulse(pulseValue, c, Offset(g.cx, g.cy))
    }
}

private fun Color.toArgbInt(): Int =
    ((alpha * 255).toInt() shl 24) or
        ((red * 255).toInt() shl 16) or
        ((green * 255).toInt() shl 8) or
        (blue * 255).toInt()
