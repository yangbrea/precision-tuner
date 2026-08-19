package com.precisiontuner.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Maps a 0-based beat index to a ring angle (270° = top, clockwise). */
internal fun beatAngle(index: Int, count: Int): Float {
    if (count <= 0) return 270f
    return 270f + Math.floorMod(index, count) * (360f / count)
}

/** Fraction of a measure over which a passed node brightens to full. */
internal const val NODE_GLOW_SPAN = 0.06f
internal const val METRONOME_MIN_BPM = 30
internal const val METRONOME_MAX_BPM = 300

internal fun bpmFromVerticalDrag(startBpm: Int, dragPixels: Float, pixelsPerStep: Float): Int =
    (startBpm - dragPixels / pixelsPerStep.coerceAtLeast(1f)).roundToInt()
        .coerceIn(METRONOME_MIN_BPM, METRONOME_MAX_BPM)

/**
 * How lit a progress node is: 0 while the sweep is still ahead of it, then a
 * smooth brighten once passed ([NODE_GLOW_SPAN] of a measure), staying lit
 * until the next measure resets the lap. Stopped → always dim.
 */
internal fun nodeLit(progress: Float, isPlaying: Boolean, nodeFraction: Float): Float {
    if (!isPlaying || progress < nodeFraction) return 0f
    return ((progress - nodeFraction) / NODE_GLOW_SPAN).coerceIn(0f, 1f)
}

/**
 * Circular metronome progress ring: **one full lap equals one measure**. The
 * accent arc fills clockwise from the top across the whole measure; when the
 * measure completes (the next downbeat arrives) an expanding pulse fires.
 * Subdivision marks and beat boundary ticks sit on the ring. BPM and the time
 * signature live in the center.
 */
@Composable
fun MetronomeRing(
    bpm: Int,
    beatsPerBar: Int,
    noteValue: Int,
    subdivision: Int,
    isPlaying: Boolean,
    currentBeat: Int, // 1-based; 0 when stopped
    onSetBpm: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val beatMark = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    val minorTick = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var lastHapticBpm by remember { mutableIntStateOf(bpm) }
    val pixelsPerStep = with(density) { 12.dp.toPx() }
    val currentBpm by rememberUpdatedState(bpm)

    val barPeriodNs = beatsPerBar * 60_000_000_000.0 / bpm
    var barStartNs by remember { mutableLongStateOf(System.nanoTime()) }
    var progress by remember { mutableFloatStateOf(0f) }

    // Re-anchor the progress clock on every downbeat (measure start); UI-side
    // nanoTime so the audio engine's scheduling is untouched. Re-anchoring
    // each measure means any sub-frame phase offset never accumulates.
    LaunchedEffect(currentBeat, isPlaying) {
        if (isPlaying && currentBeat == 1) {
            barStartNs = System.nanoTime()
        } else if (!isPlaying) {
            progress = 0f
        }
    }
    LaunchedEffect(isPlaying, bpm, beatsPerBar) {
        while (isPlaying) {
            withFrameNanos { now ->
                progress = ((now - barStartNs) / barPeriodNs).toFloat().coerceIn(0f, 1f)
            }
        }
    }

    // Expanding pulse when a measure completes (the downbeat of the next bar).
    val barPulse = remember { Animatable(1f) }
    val beatPulse = remember { Animatable(1f) }
    var lastBeat by remember { mutableIntStateOf(0) }
    LaunchedEffect(currentBeat, isPlaying) {
        if (!isPlaying) {
            lastBeat = 0
            progress = 0f
            return@LaunchedEffect
        }
        if (currentBeat == 1 && lastBeat != 1) {
            barPulse.snapTo(0f)
            barPulse.animateTo(1f, tween(durationMillis = 320))
        }
        if (currentBeat != 0) lastBeat = currentBeat
    }
    LaunchedEffect(currentBeat, isPlaying) {
        if (isPlaying && currentBeat != 0) {
            beatPulse.snapTo(0f)
            beatPulse.animateTo(1f, tween(durationMillis = 190))
        } else {
            beatPulse.snapTo(1f)
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = min(size.width, size.height) / 2f - 24.dp.toPx()

            // One ring, one meaning: a complete lap is one measure. Layered
            // strokes add presence without introducing a second BPM scale.
            drawCircle(trackColor.copy(alpha = 0.22f), radius, Offset(cx, cy), style = Stroke(14.dp.toPx()))
            drawCircle(trackColor, radius, Offset(cx, cy), style = Stroke(2.dp.toPx()))

            // Progress arc filling clockwise from the top (one lap = one measure).
            if (isPlaying && progress > 0f) {
                val sweep = 360f * progress
                drawArc(accent.copy(alpha = 0.10f), 270f, sweep, false, Offset(cx - radius, cy - radius), Size(radius * 2f, radius * 2f), style = Stroke(16.dp.toPx(), cap = StrokeCap.Round))
                drawArc(accent.copy(alpha = 0.34f), 270f, sweep, false, Offset(cx - radius, cy - radius), Size(radius * 2f, radius * 2f), style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
                drawArc(accent, 270f, sweep, false, Offset(cx - radius, cy - radius), Size(radius * 2f, radius * 2f), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            }

            // Beat boundary marks: light up as the sweep passes them.
            for (i in 0 until beatsPerBar) {
                val f = i.toFloat() / beatsPerBar
                val lit = nodeLit(progress, isPlaying, f)
                val pos = pointAt(cx, cy, radius, beatAngle(i, beatsPerBar))
                if (lit > 0f) {
                    drawCircle(accent.copy(alpha = 0.25f + 0.75f * lit), (3.5.dp + 4.dp * lit).toPx(), pos)
                } else {
                    drawCircle(beatMark, 3.5.dp.toPx(), pos)
                }
            }

            // Subdivision marks between beats: same light-up effect.
            if (subdivision > 1) {
                for (i in 0 until beatsPerBar) {
                    for (s in 1 until subdivision) {
                        val f = (i + s.toFloat() / subdivision) / beatsPerBar
                        val lit = nodeLit(progress, isPlaying, f)
                        val pos = pointAt(cx, cy, radius, 270f + f * 360f)
                        if (lit > 0f) {
                            drawCircle(accent.copy(alpha = 0.25f + 0.75f * lit), (2.5.dp + 3.dp * lit).toPx(), pos)
                        } else {
                            drawCircle(minorTick, 2.5.dp.toPx(), pos)
                        }
                    }
                }
            }

            if (isPlaying && currentBeat > 0) {
                val activePos = pointAt(cx, cy, radius, beatAngle(currentBeat - 1, beatsPerBar))
                drawCircle(
                    accent.copy(alpha = (1f - beatPulse.value) * 0.28f),
                    (7.dp + 12.dp * beatPulse.value).toPx(),
                    activePos,
                )
                drawCircle(accent, 5.5.dp.toPx(), activePos)
            }

            // Measure-completion pulse (expanding ring).
            if (barPulse.value < 1f) {
                val pulseProgress = barPulse.value
                drawCircle(
                    color = accent.copy(alpha = (1f - pulseProgress) * 0.45f),
                    radius = radius * (0.5f + 0.4f * pulseProgress),
                    center = Offset(cx, cy),
                    style = Stroke((3.dp * (1f - pulseProgress) + 1.dp).toPx()),
                )
            }
        }

        Column(
            modifier = Modifier.pointerInput(Unit) {
                var startY = 0f
                var startValue = currentBpm
                detectDragGestures(
                    onDragStart = { offset ->
                        startY = offset.y
                        startValue = currentBpm
                    },
                    onDrag = { change, _ ->
                        val next = bpmFromVerticalDrag(startValue, change.position.y - startY, pixelsPerStep)
                        if (next != currentBpm) onSetBpm(next)
                        if (next != lastHapticBpm && (next % 10 == 0 || next in listOf(60, 90, 120, 160))) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastHapticBpm = next
                        }
                        change.consume()
                    },
                )
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = bpm.toString(),
                fontSize = 54.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(text = "BPM", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = "$beatsPerBar/$noteValue",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
        }
    }
}

private fun pointAt(cx: Float, cy: Float, r: Float, angleDeg: Float): Offset {
    val rad = Math.toRadians(angleDeg.toDouble())
    return Offset(
        (cx + r * cos(rad)).toFloat(),
        (cy + r * sin(rad)).toFloat(),
    )
}
