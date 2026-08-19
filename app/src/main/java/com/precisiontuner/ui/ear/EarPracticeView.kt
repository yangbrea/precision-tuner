package com.precisiontuner.ui.ear

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.precisiontuner.ear.EarSessionState
import com.precisiontuner.ear.ExerciseType
import com.precisiontuner.ear.PracticeMode
import com.precisiontuner.ear.QuizQuestion
import com.precisiontuner.ear.StaffNotation

/**
 * 闯关主战场: HUD (score / lives / progress), the pulse play button, a 2×2
 * option grid with green/red feedback and a wrong-answer shake, and the
 * feedback banner with "下一题". The rhythm exercise replaces the option grid
 * with a tap-to-reproduce recorder that auto-submits once the expected number
 * of taps is reached.
 */
@Composable
fun EarPracticeView(
    exerciseType: ExerciseType,
    session: EarSessionState,
    isPlaying: Boolean,
    onReplay: () -> Unit,
    onSelectAnswer: (Int) -> Unit,
    onNext: () -> Unit,
    onEnd: () -> Unit,
    onBack: () -> Unit,
    onRhythmSubmit: (List<Long>) -> Unit,
    onRhythmTap: () -> Unit,
) {
    val question = session.question ?: return
    val answered = session.isCorrect != null
    val accent = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "退出练习")
            }
            ModeBadge(session.mode)
            Spacer(Modifier.weight(1f))
            if (session.mode == PracticeMode.ENDLESS && answered) {
                TextButton(onClick = onEnd) { Text("结束本轮") }
            }
        }

        HUD(session)

        Spacer(Modifier.height(8.dp))
        when (exerciseType) {
            ExerciseType.STAFF_READING -> {
                // Staff reading is visual: render the note on a staff instead of a
                // play button; options unlock immediately (isPlaying stays false).
                val notation = question.staffNotation ?: StaffNotation.fromMidi(question.noteMidis[0])
                AnimatedContent(
                    targetState = notation,
                    transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(90)) },
                    label = "staffQuestion",
                ) { displayedNotation ->
                    StaffView(
                        notation = displayedNotation,
                        modifier = Modifier.fillMaxWidth().height(156.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            ExerciseType.RHYTHM -> {
                // Rhythm dictation: show the staff pattern, a smaller replay
                // button, and let the user reproduce the rhythm by tapping.
                val pattern = question.rhythmPattern ?: return
                RhythmStaffView(
                    pattern = pattern,
                    modifier = Modifier.fillMaxWidth().height(170.dp),
                )
                Spacer(Modifier.height(8.dp))
                PulsePlayButton(
                    playing = isPlaying,
                    onClick = onReplay,
                    modifier = Modifier.size(64.dp),
                )
                Spacer(Modifier.height(8.dp))
            }
            else -> {
                PulsePlayButton(
                    playing = isPlaying,
                    onClick = onReplay,
                    modifier = Modifier.size(96.dp),
                )
                Spacer(Modifier.height(12.dp))
            }
        }
        Text(
            text = exerciseType.prompt(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        if (exerciseType == ExerciseType.RHYTHM) {
            val pattern = question.rhythmPattern ?: return
            // Key the recorder to the question so its tap state (taps,
            // recording, start time) resets on the next question instead of
            // leaking from the previous one.
            key(question) {
                RhythmRecorder(
                    expectedTaps = pattern.expectedTaps,
                    answered = answered,
                    onSubmit = onRhythmSubmit,
                    onTap = onRhythmTap,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in 0 until 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (col in 0 until 2) {
                            val index = row * 2 + col
                            if (index < question.options.size) {
                                AnswerOptionButton(
                                    label = question.options[index],
                                    state = optionState(index, question, session),
                                    enabled = !answered && !isPlaying,
                                    shakeTick = wrongShakeTick(index, question, session),
                                    onClick = { onSelectAnswer(index) },
                                    modifier = Modifier.weight(1f).height(56.dp),
                                )
                            } else {
                                Spacer(Modifier.weight(1f).height(56.dp))
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = answered,
            enter = fadeIn() + slideInVertically { it / 2 },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 10.dp),
            ) {
                Text(
                    text = buildString {
                        append(if (session.isCorrect == true) "✓ 正确！" else "✗ 正确答案是「${question.answerName}」")
                        session.answerDetail?.let {
                            append(" · ").append(it)
                        }
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (session.isCorrect == true) accent else error,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text("下一题", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * Tap-to-reproduce recorder for the rhythm exercise. Starts recording on
 * [开始复现], plays a piano C4 on every tap (via [onTap]) with haptic feedback,
 * logs the tap times (ms relative to the first tap), and auto-submits once
 * [expectedTaps] taps are collected.
 */
@Composable
private fun RhythmRecorder(
    expectedTaps: Int,
    answered: Boolean,
    onSubmit: (List<Long>) -> Unit,
    onTap: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val accent = MaterialTheme.colorScheme.primary
    var recording by remember { mutableStateOf(false) }
    var taps by remember { mutableStateOf<List<Long>>(emptyList()) }
    var startNanos by remember { mutableStateOf(0L) }
    // Elastic pulse on every tap.
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(taps.size) {
        if (taps.isNotEmpty()) {
            pulse.snapTo(0.88f)
            pulse.animateTo(1f, tween(140))
        }
    }

    // Once the answer has settled the whole recorder disappears: no "哒",
    // no 重录, no 开始复现 — only the feedback banner and 下一题 remain.
    if (answered) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!recording) {
            Text(
                text = "先听参考节奏，然后点开始，按谱面敲击 ${expectedTaps} 个音",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    taps = emptyList()
                    startNanos = SystemClock.elapsedRealtime()
                    recording = true
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("开始复现", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Text(
                text = "已敲 ${taps.size}/$expectedTaps 音 · 敲满自动判定",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            // Fires on ACTION_DOWN (not on release like clickable) so the piano
            // strike starts the instant the finger touches — the tap time is
            // recorded at the same moment to keep scoring in sync with audio.
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .graphicsLayer {
                        scaleX = pulse.value
                        scaleY = pulse.value
                    }
                    .clip(CircleShape)
                    .background(accent)
                    .semantics { role = Role.Button }
                    .pointerInput(expectedTaps) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            onTap()
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            val now = SystemClock.elapsedRealtime() - startNanos
                            val updated = taps + now
                            if (updated.size >= expectedTaps) {
                                onSubmit(updated)
                            } else {
                                taps = updated
                            }
                            // Consume the rest of the gesture so the parent
                            // scroll can't steal it; nothing repeats on hold.
                            var keepGoing = true
                            while (keepGoing) {
                                val event = awaitPointerEvent()
                                keepGoing = event.changes.any { it.pressed }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "哒",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { taps = emptyList() }) { Text("重录") }
        }
    }
}

@Composable
private fun HUD(session: EarSessionState) {
    val animatedScore by animateIntAsState(session.correctCount, tween(300), label = "score")
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "$animatedScore",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "答对",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        when (session.mode) {
            PracticeMode.CHALLENGE -> HeartsRow(session.lives)
            PracticeMode.TEST -> Text(
                text = "第 ${(session.answeredCount + 1).coerceAtMost(session.questionLimit)}/${session.questionLimit} 题",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            PracticeMode.ENDLESS -> Text(
                text = "已答 ${session.answeredCount} · 连对 ${session.streak}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModeBadge(mode: PracticeMode) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = mode.title(),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

private fun optionState(index: Int, question: QuizQuestion, session: EarSessionState): AnswerOptionState {
    val answered = session.selectedIndex != null
    return when {
        !answered -> AnswerOptionState.IDLE
        index == question.answerIndex -> AnswerOptionState.CORRECT
        index == session.selectedIndex -> AnswerOptionState.WRONG
        else -> AnswerOptionState.DIMMED
    }
}

private fun wrongShakeTick(index: Int, question: QuizQuestion, session: EarSessionState): Int =
    if (session.selectedIndex == index && session.isCorrect == false) session.answeredCount else 0
