package com.precisiontuner.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.precisiontuner.TuneVisualState
import com.precisiontuner.ui.theme.TunerFlat
import com.precisiontuner.ui.theme.TunerSharp

/**
 * Low-cost ambient field shared by the tuner and metronome screens. The glow
 * has no hard edge: tuner cents move it left/right, while one-shot events add
 * a short radial pulse. The theme color follows the stabilized [visualState]
 * (never raw cents), so the ±5¢ boundary cannot flicker the background.
 * Waiting/stopped screens settle to a nearly static field.
 */
@Composable
fun NeonScreenBackground(
    modifier: Modifier = Modifier,
    cents: Double? = null,
    active: Boolean = false,
    pulseTick: Int = 0,
    visualState: TuneVisualState = TuneVisualState.WAITING,
) {
    val accent = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.background
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant
    val glowColor = when {
        !active || cents == null -> accent
        visualState == TuneVisualState.LOW -> TunerFlat
        visualState == TuneVisualState.HIGH -> TunerSharp
        else -> accent
    }
    val glowX by animateFloatAsState(
        targetValue = if (active && cents != null) {
            0.5f + (cents.coerceIn(-50.0, 50.0) / 50.0 * 0.22).toFloat()
        } else {
            0.5f
        },
        animationSpec = tween(180),
        label = "ambientGlowX",
    )
    val glowStrength by animateFloatAsState(
        targetValue = if (active) 1f else 0.28f,
        animationSpec = tween(260),
        label = "ambientGlowStrength",
    )
    val pulse = remember { Animatable(1f) }
    var previousPulseTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(pulseTick) {
        if (pulseTick > 0 && pulseTick != previousPulseTick) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(430))
        }
        previousPulseTick = pulseTick
    }
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        accent.copy(alpha = 0.045f),
                        background,
                        background,
                    ),
                ),
            ),
    ) {
        val glowCenter = Offset(size.width * glowX, size.height * 0.31f)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    glowColor.copy(alpha = 0.13f * glowStrength),
                    glowColor.copy(alpha = 0.052f * glowStrength),
                    Color.Transparent,
                ),
                center = glowCenter,
                radius = size.minDimension * 0.78f,
            ),
        )

        // Sparse instrument-grid lines add depth without creating a visible
        // geometric object behind the primary readout.
        for (i in 1..7) {
            val y = size.height * (i / 8f)
            drawLine(
                color = gridColor.copy(alpha = 0.018f),
                start = Offset(size.width * 0.06f, y),
                end = Offset(size.width * 0.94f, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
        drawLine(
            color = glowColor.copy(alpha = 0.10f * glowStrength),
            start = Offset(size.width * 0.08f, size.height * 0.08f),
            end = Offset(size.width * 0.92f, size.height * 0.08f),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
        )

        if (pulse.value < 1f) {
            val progress = pulse.value
            drawCircle(
                color = glowColor.copy(alpha = (1f - progress) * 0.18f),
                radius = size.minDimension * (0.12f + progress * 0.36f),
                center = glowCenter,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = (2.5.dp * (1f - progress) + 0.5.dp).toPx(),
                ),
            )
        }
    }
}

@Composable
fun NeonPanel(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.78f),
                    ),
                ),
                shape = shape,
            )
            .border(
                width = if (highlighted) 1.5.dp else 1.dp,
                color = if (highlighted) accent.copy(alpha = 0.66f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f),
                shape = shape,
            ),
        content = content,
    )
}
