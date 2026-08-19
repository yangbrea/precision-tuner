package com.precisiontuner.ui.ear

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.precisiontuner.ear.EarSessionState

/**
 * Shared visual primitives for the ear-training section. Everything is drawn
 * with Compose primitives + Canvas, matching the app's flat accent-driven look
 * (see MetronomeRing / TunerGauge).
 */

/** Visual state of one answer option. */
enum class AnswerOptionState { IDLE, CORRECT, WRONG, DIMMED }

/** Breathing circular play button with an expanding glow ring. */
@Composable
fun PulsePlayButton(
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val infinite = rememberInfiniteTransition(label = "playPulse")
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "playPulseValue",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f * (0.72f + 0.30f * pulse)
            drawCircle(
                color = accent.copy(alpha = (1f - pulse) * 0.35f),
                radius = radius,
                center = Offset(size.width / 2f, size.height / 2f),
                style = Stroke(width = 3.dp.toPx()),
            )
        }
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(accent)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (playing) Icons.AutoMirrored.Filled.VolumeUp else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "播放中" else "播放",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(38.dp),
            )
        }
    }
}

/** Challenge lives as hearts; lost lives are hollow and dimmed. */
@Composable
fun HeartsRow(
    lives: Int,
    total: Int = EarSessionState.CHALLENGE_LIVES,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 0 until total) {
            val alive = i < lives
            Icon(
                imageVector = if (alive) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = null,
                tint = if (alive) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** Round progress ring showing accuracy; center content is layered on top. */
@Composable
fun AccuracyRing(accuracy: Float, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier) {
        val stroke = 12.dp.toPx()
        val inset = stroke / 2f
        drawArc(
            color = track,
            startAngle = 270f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawArc(
            color = accent,
            startAngle = 270f,
            sweepAngle = 360f * accuracy.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/** One multiple-choice option with feedback states and a wrong-answer shake. */
@Composable
fun AnswerOptionButton(
    label: String,
    state: AnswerOptionState,
    enabled: Boolean,
    shakeTick: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = when (state) {
        AnswerOptionState.IDLE -> MaterialTheme.colorScheme.surfaceVariant
        AnswerOptionState.CORRECT -> MaterialTheme.colorScheme.primary
        AnswerOptionState.WRONG -> MaterialTheme.colorScheme.error
        AnswerOptionState.DIMMED -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (state) {
        AnswerOptionState.CORRECT -> MaterialTheme.colorScheme.onPrimary
        AnswerOptionState.WRONG -> MaterialTheme.colorScheme.onError
        else -> MaterialTheme.colorScheme.onSurface
    }

    val shake = remember { Animatable(0f) }
    var lastShake by remember { mutableIntStateOf(0) }
    LaunchedEffect(shakeTick) {
        if (shakeTick != 0 && shakeTick != lastShake) {
            lastShake = shakeTick
            repeat(4) { i ->
                val amp = (6f - i * 1.2f).coerceAtLeast(1.5f)
                shake.animateTo(amp, tween(40))
                shake.animateTo(-amp, tween(40))
            }
            shake.animateTo(0f, tween(40))
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .graphicsLayer {
                translationX = shake.value
                alpha = if (state == AnswerOptionState.DIMMED) 0.45f else 1f
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            textAlign = TextAlign.Center,
        )
    }
}
