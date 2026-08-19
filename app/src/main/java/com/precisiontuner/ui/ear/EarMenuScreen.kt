package com.precisiontuner.ui.ear

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.precisiontuner.ear.Difficulty
import com.precisiontuner.ear.ExerciseType

/**
 * 练习菜单: the root of the 视听练耳 section. A game-style card per exercise
 * (gradient icon tile, name, description, difficulty badge + dots, entry
 * arrow). Tapping a card enters that exercise's setup view.
 */
@Composable
fun EarMenuScreen(
    difficulties: Map<ExerciseType, Difficulty>,
    onSelectExercise: (ExerciseType) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "选择练习",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        )
        Text(
            text = "一次专注一个练习，难度各自独立设置",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        )
        ExerciseType.entries.forEach { type ->
            ExerciseMenuCard(
                type = type,
                difficulty = difficulties[type] ?: Difficulty.EASY,
                onClick = { onSelectExercise(type) },
            )
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ExerciseMenuCard(
    type: ExerciseType,
    difficulty: Difficulty,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "menuCardPress",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        // Bottom glow: a soft accent gradient rising from the card's lower edge.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(72.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, accent.copy(alpha = 0.14f)),
                    )
                ),
        )

        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                accent.copy(alpha = 0.30f),
                                accent.copy(alpha = 0.05f),
                            ),
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = type.icon(),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = type.label(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = type.description(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                DifficultyBadge(difficulty)
                Spacer(Modifier.height(6.dp))
                DifficultyDots(difficulty)
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "进入练习",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun DifficultyBadge(difficulty: Difficulty) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = difficulty.label(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** Three dots marking the difficulty level (filled up to the current one). */
@Composable
private fun DifficultyDots(difficulty: Difficulty) {
    val accent = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.outline
    Canvas(Modifier.size(width = 34.dp, height = 8.dp)) {
        val dotRadius = 3.dp.toPx()
        val step = 11.dp.toPx()
        val y = size.height / 2f
        val filledCount = difficulty.ordinal + 1
        for (i in 0 until 3) {
            val center = Offset(step * i, y)
            if (i < filledCount) {
                drawCircle(accent, dotRadius, center)
            } else {
                drawCircle(
                    color = empty,
                    radius = dotRadius,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
    }
}
