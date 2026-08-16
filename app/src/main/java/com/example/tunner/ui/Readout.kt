package com.example.tunner.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tunner.TunerState
import com.example.tunner.DetectionPhase
import com.example.tunner.ui.theme.TunerFlat
import com.example.tunner.ui.theme.TunerSharp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** Formats cents as a signed, one-decimal string, locale-independent. */
fun formatCents(cents: Double): String {
    val tenths = (cents * 10).roundToInt()
    val sign = if (tenths >= 0) "+" else "-"
    val a = abs(tenths)
    return "$sign${a / 10}.${a % 10} ¢"
}

/** Formats a frequency in Hz with one decimal, locale-independent. */
fun formatFrequency(freq: Double): String {
    val tenths = (freq * 10).roundToInt()
    return "${tenths / 10}.${tenths % 10} Hz"
}

/**
 * Shared readout: big note name + octave, cents verdict, and frequency.
 */
@Composable
fun Readout(
    noteName: String?,
    octave: Int?,
    cents: Double?,
    frequency: Double?,
    detectionPhase: DetectionPhase,
    observedNoteName: String?,
    observedOctave: Int?,
    flashTick: Int = 0,
    modifier: Modifier = Modifier,
) {
    val inTune = detectionPhase == DetectionPhase.TRACKING &&
        cents != null && abs(cents) <= TunerState.IN_TUNE_CENTS
    val primary = MaterialTheme.colorScheme.primary

    // One-shot "locked" pulse: scale the note briefly on each in-tune flash.
    var pulse by remember { mutableStateOf(false) }
    LaunchedEffect(flashTick) {
        if (flashTick > 0) {
            pulse = true
            delay(130)
            pulse = false
        }
    }
    val flashScale by animateFloatAsState(
        targetValue = if (pulse) 1.14f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "noteFlash",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.heightIn(min = 104.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = noteName ?: "—",
                style = TextStyle(
                    fontSize = 78.sp,
                    lineHeight = 94.sp,
                    fontWeight = FontWeight.Bold,
                    platformStyle = PlatformTextStyle(includeFontPadding = true),
                ),
                maxLines = 1,
                modifier = Modifier.graphicsLayer {
                    scaleX = flashScale
                    scaleY = flashScale
                },
                color = if (inTune) primary else MaterialTheme.colorScheme.onBackground,
            )
            if (noteName != null && octave != null) {
                Text(
                    text = octave.toString(),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 14.dp),
                )
            }
        }

        Text(
            text = when {
                detectionPhase == DetectionPhase.WAITING || cents == null -> "等待输入…"
                detectionPhase == DetectionPhase.OUT_OF_RANGE -> {
                    val observed = if (observedNoteName != null && observedOctave != null) {
                        "$observedNoteName$observedOctave"
                    } else {
                        "其他音高"
                    }
                    if (cents < 0) "检测到 $observed · 远低于目标"
                    else "检测到 $observed · 远高于目标"
                }
                inTune -> "已调准"
                cents < 0 -> "偏低 ${formatCents(cents)}"
                else -> "偏高 ${formatCents(cents)}"
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            color = when {
                detectionPhase == DetectionPhase.WAITING || cents == null -> MaterialTheme.colorScheme.onSurfaceVariant
                inTune -> primary
                cents < 0 -> TunerFlat
                else -> TunerSharp
            },
        )

        Text(
            text = frequency?.let { formatFrequency(it) } ?: "0.0 Hz",
            fontSize = 18.sp,
            maxLines = 1,
            color = if (frequency != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
    }
}
