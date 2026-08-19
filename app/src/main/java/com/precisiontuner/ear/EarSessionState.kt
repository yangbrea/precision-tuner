package com.precisiontuner.ear

/**
 * One quiz session of one exercise. Immutable; transitions are computed by
 * [EarSessionLogic] and stored in the ViewModel per exercise.
 */
data class EarSessionState(
    val phase: Phase = Phase.SETUP,
    val mode: PracticeMode = PracticeMode.ENDLESS,
    val question: QuizQuestion? = null,
    val selectedIndex: Int? = null,
    val isCorrect: Boolean? = null, // null = not yet answered
    val answerDetail: String? = null, // e.g. rhythm score line (non-choice answers)
    val answeredCount: Int = 0,
    val correctCount: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val lives: Int = CHALLENGE_LIVES,
    val questionLimit: Int = 10,
    val endedReason: EndedReason? = null,
) {
    enum class Phase { SETUP, PRACTICE, RESULT }

    enum class EndedReason { LIVES_OVER, COMPLETED, MANUAL_END }

    /** Accuracy of the answered questions so far (0 when none answered). */
    val accuracy: Float
        get() = if (answeredCount == 0) 0f else correctCount.toFloat() / answeredCount

    companion object {
        const val CHALLENGE_LIVES = 5
    }
}

/**
 * Pure transitions of an [EarSessionState]; the ViewModel applies them and
 * stores the result. Kept free of Android dependencies so the rules are
 * unit-testable on the JVM.
 */
object EarSessionLogic {

    /** Begins a session in [mode] with the first [question]. */
    fun start(mode: PracticeMode, question: QuizQuestion, questionLimit: Int): EarSessionState =
        EarSessionState(
            phase = EarSessionState.Phase.PRACTICE,
            mode = mode,
            question = question,
            questionLimit = questionLimit,
        )

    /** Advances to the next [question], clearing the previous answer. */
    fun nextQuestion(session: EarSessionState, question: QuizQuestion): EarSessionState =
        session.copy(
            question = question,
            selectedIndex = null,
            isCorrect = null,
            answerDetail = null,
        )

    /**
     * Applies the user's [selectedIndex] choice. Returns the same session when
     * already answered or no question is active. Moves to RESULT when a
     * challenge runs out of lives or a test reaches its question limit.
     */
    fun answer(session: EarSessionState, selectedIndex: Int): EarSessionState {
        val question = session.question ?: return session
        if (session.selectedIndex != null) return session
        return settle(
            session,
            isCorrect = selectedIndex == question.answerIndex,
            selectedIndex = selectedIndex,
            detail = null,
        )
    }

    /**
     * Applies a scored, non-multiple-choice answer (e.g. rhythm performance):
     * the caller decides [isCorrect] from [com.precisiontuner.ear.RhythmScorer]
     * and may attach a human-readable [detail] shown on the feedback banner.
     * Same lifecycle rules as [answer].
     */
    fun answerScored(
        session: EarSessionState,
        isCorrect: Boolean,
        detail: String? = null,
    ): EarSessionState {
        val question = session.question ?: return session
        if (session.selectedIndex != null) return session
        // -1 marks a non-option answer so the UI can still tell "answered".
        return settle(session, isCorrect, selectedIndex = -1, detail = detail)
    }

    private fun settle(
        session: EarSessionState,
        isCorrect: Boolean,
        selectedIndex: Int?,
        detail: String?,
    ): EarSessionState {
        val answered = session.answeredCount + 1
        val correctCount = session.correctCount + if (isCorrect) 1 else 0
        val streak = if (isCorrect) session.streak + 1 else 0
        val bestStreak = maxOf(session.bestStreak, streak)
        val lives = if (!isCorrect && session.mode == PracticeMode.CHALLENGE) {
            (session.lives - 1).coerceAtLeast(0)
        } else {
            session.lives
        }

        var phase = EarSessionState.Phase.PRACTICE
        var endedReason: EarSessionState.EndedReason? = null
        if (session.mode == PracticeMode.CHALLENGE && lives == 0) {
            phase = EarSessionState.Phase.RESULT
            endedReason = EarSessionState.EndedReason.LIVES_OVER
        } else if (session.mode == PracticeMode.TEST && answered >= session.questionLimit) {
            phase = EarSessionState.Phase.RESULT
            endedReason = EarSessionState.EndedReason.COMPLETED
        }

        return session.copy(
            selectedIndex = selectedIndex,
            isCorrect = isCorrect,
            answerDetail = detail,
            answeredCount = answered,
            correctCount = correctCount,
            streak = streak,
            bestStreak = bestStreak,
            lives = lives,
            phase = phase,
            endedReason = endedReason,
        )
    }

    /** Ends an endless session manually (no-op outside PRACTICE). */
    fun end(session: EarSessionState): EarSessionState =
        if (session.phase == EarSessionState.Phase.PRACTICE) {
            session.copy(
                phase = EarSessionState.Phase.RESULT,
                endedReason = EarSessionState.EndedReason.MANUAL_END,
            )
        } else {
            session
        }

    /** Returns to the setup view, forgetting the current question. */
    fun backToSetup(session: EarSessionState): EarSessionState =
        session.copy(
            phase = EarSessionState.Phase.SETUP,
            question = null,
            selectedIndex = null,
            isCorrect = null,
            answerDetail = null,
            endedReason = null,
        )
}
