package com.precisiontuner.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Maps a 0-based beat index to a ring angle (270° = top, clockwise). */
internal fun beatAngle(index: Int, count: Int): Float {
    if (count <= 0) return 270f
    return 270f + Math.floorMod(index, count) * (360f / count)
}

/** Fraction of a measure over which a passed node brightens to full. */
internal const val NODE_GLOW_SPAN = 0.06f

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
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val beatMark = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    val minorTick = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

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
    val pulse = remember { Animatable(1f) }
    var lastBeat by remember { mutableIntStateOf(0) }
    LaunchedEffect(currentBeat, isPlaying) {
        if (!isPlaying) {
            lastBeat = 0
            progress = 0f
            return@LaunchedEffect
        }
        if (currentBeat == 1 && lastBeat != 1) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(durationMillis = 320))
        }
        if (currentBeat != 0) lastBeat = currentBeat
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = min(size.width, size.height) / 2f - 24.dp.toPx()

            // Track ring.
            drawCircle(trackColor, radius, Offset(cx, cy), style = Stroke(2.dp.toPx()))

            // Progress arc filling clockwise from the top (one lap = one measure).
            drawArc(
                color = accent,
                startAngle = 270f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
            )

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

            // Measure-completion pulse (expanding ring).
            if (pulse.value < 1f) {
                val pulseProgress = pulse.value
                drawCircle(
                    color = accent.copy(alpha = (1f - pulseProgress) * 0.45f),
                    radius = radius * (0.5f + 0.4f * pulseProgress),
                    center = Offset(cx, cy),
                    style = Stroke((3.dp * (1f - pulseProgress) + 1.dp).toPx()),
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
