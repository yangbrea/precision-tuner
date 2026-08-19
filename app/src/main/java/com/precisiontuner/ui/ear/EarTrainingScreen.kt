package com.precisiontuner.ui.ear

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.precisiontuner.ear.Difficulty
import com.precisiontuner.ear.EarSessionState
import com.precisiontuner.ear.EarSettings
import com.precisiontuner.ear.ExerciseType
import com.precisiontuner.ear.PracticeMode

/**
 * The 视听练耳 section: a menu of exercise cards ([EarMenuScreen]) at the
 * root; tapping a card enters that exercise's setup / practice / result views.
 */
@Composable
fun EarTrainingScreen(
    atMenu: Boolean,
    activeExercise: ExerciseType,
    difficulties: Map<ExerciseType, Difficulty>,
    session: EarSessionState,
    settings: EarSettings,
    isPlaying: Boolean,
    onSelectExercise: (ExerciseType) -> Unit,
    onBackToMenu: () -> Unit,
    onStart: (PracticeMode) -> Unit,
    onReplay: () -> Unit,
    onSelectAnswer: (Int) -> Unit,
    onNext: () -> Unit,
    onEnd: () -> Unit,
    onBackToSetup: () -> Unit,
    onRestart: () -> Unit,
    onDifficulty: (Difficulty) -> Unit,
    onMelodic: (Boolean) -> Unit,
    onNoteReferenceTone: (Boolean) -> Unit,
    onTestCount: (Int) -> Unit,
    onRhythmSubmit: (List<Long>) -> Unit,
    onRhythmTap: () -> Unit,
) {
    // Inside an exercise, the system back gesture (edge swipe / predictive
    // back) steps up the navigation chain instead of exiting the app: from the
    // setup view it goes to the menu; from practice or the result it returns
    // to the setup view.
    BackHandler(enabled = !atMenu) {
        if (session.phase == EarSessionState.Phase.SETUP) onBackToMenu() else onBackToSetup()
    }

    Column(Modifier.fillMaxSize()) {
        if (atMenu) {
            EarMenuScreen(
                difficulties = difficulties,
                onSelectExercise = onSelectExercise,
            )
        } else {
            when (session.phase) {
                EarSessionState.Phase.SETUP -> EarSetupView(
                    exerciseType = activeExercise,
                    settings = settings,
                    difficulty = difficulties[activeExercise] ?: Difficulty.EASY,
                    onStart = onStart,
                    onBackToMenu = onBackToMenu,
                    onDifficulty = onDifficulty,
                    onMelodic = onMelodic,
                    onNoteReferenceTone = onNoteReferenceTone,
                    onTestCount = onTestCount,
                )
                EarSessionState.Phase.PRACTICE -> EarPracticeView(
                    exerciseType = activeExercise,
                    session = session,
                    isPlaying = isPlaying,
                    onReplay = onReplay,
                    onSelectAnswer = onSelectAnswer,
                    onNext = onNext,
                    onEnd = onEnd,
                    onBack = onBackToSetup,
                    onRhythmSubmit = onRhythmSubmit,
                    onRhythmTap = onRhythmTap,
                )
                EarSessionState.Phase.RESULT -> EarResultView(
                    session = session,
                    onRestart = onRestart,
                    onBack = onBackToSetup,
                )
            }
        }
    }
}
