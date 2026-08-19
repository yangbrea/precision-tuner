package com.precisiontuner

import com.precisiontuner.ear.EarSessionLogic
import com.precisiontuner.ear.EarSessionState
import com.precisiontuner.ear.ExerciseType
import com.precisiontuner.ear.PracticeMode
import com.precisiontuner.ear.QuizQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EarSessionLogicTest {

    private fun question(answerIndex: Int = 0): QuizQuestion = QuizQuestion(
        type = ExerciseType.INTERVAL,
        answerName = "纯五度",
        options = listOf("纯五度", "大三度", "小二度", "纯八度"),
        answerIndex = answerIndex,
        noteMidis = listOf(60, 67),
    )

    private fun started(mode: PracticeMode, limit: Int = 10): EarSessionState =
        EarSessionLogic.start(mode, question(), limit)

    /** Advances to a fresh question and answers it, like the real UI flow. */
    private fun answerNext(s: EarSessionState, index: Int): EarSessionState =
        EarSessionLogic.answer(EarSessionLogic.nextQuestion(s, question()), index)

    @Test
    fun `starting a session enters practice with the question`() {
        val s = started(PracticeMode.ENDLESS)
        assertEquals(EarSessionState.Phase.PRACTICE, s.phase)
        assertEquals(PracticeMode.ENDLESS, s.mode)
        assertEquals(0, s.answeredCount)
        assertEquals(EarSessionState.CHALLENGE_LIVES, s.lives)
    }

    @Test
    fun `correct answer increments score and streak`() {
        val s = EarSessionLogic.answer(started(PracticeMode.ENDLESS), 0)
        assertTrue(s.isCorrect == true)
        assertEquals(1, s.answeredCount)
        assertEquals(1, s.correctCount)
        assertEquals(1, s.streak)
        assertEquals(1, s.bestStreak)
        assertEquals(EarSessionState.Phase.PRACTICE, s.phase)
    }

    @Test
    fun `wrong answer resets streak and does not count the score`() {
        val s = EarSessionLogic.answer(started(PracticeMode.ENDLESS), 2)
        assertTrue(s.isCorrect == false)
        assertEquals(1, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.streak)
    }

    @Test
    fun `challenge keeps running after four wrong answers`() {
        var s = started(PracticeMode.CHALLENGE)
        repeat(4) { s = answerNext(s, 2) }
        assertEquals(EarSessionState.Phase.PRACTICE, s.phase)
        assertEquals(1, s.lives)
        assertEquals(4, s.answeredCount)
        assertNull(s.endedReason)
    }

    @Test
    fun `challenge ends with lives exhausted on the fifth wrong answer`() {
        var s = started(PracticeMode.CHALLENGE)
        repeat(5) { s = answerNext(s, 2) }
        assertEquals(EarSessionState.Phase.RESULT, s.phase)
        assertEquals(EarSessionState.EndedReason.LIVES_OVER, s.endedReason)
        assertEquals(0, s.lives)
        assertEquals(5, s.answeredCount)
        assertEquals(0, s.correctCount)
        assertEquals(0f, s.accuracy)
    }

    @Test
    fun `lives never go below zero`() {
        var s = started(PracticeMode.CHALLENGE)
        repeat(7) { s = answerNext(s, 2) }
        assertEquals(0, s.lives)
        assertEquals(EarSessionState.Phase.RESULT, s.phase)
    }

    @Test
    fun `test mode ends when the question limit is reached`() {
        var s = started(PracticeMode.TEST, limit = 3)
        // 2 correct, then a wrong one: answered reaches 3 -> RESULT with score 2.
        s = answerNext(s, 0)
        s = answerNext(s, 0)
        assertEquals(EarSessionState.Phase.PRACTICE, s.phase)
        s = answerNext(s, 1)
        assertEquals(EarSessionState.Phase.RESULT, s.phase)
        assertEquals(EarSessionState.EndedReason.COMPLETED, s.endedReason)
        assertEquals(2, s.correctCount)
        assertEquals(2f / 3f, s.accuracy, 1e-6f)
    }

    @Test
    fun `endless mode never ends on its own and supports manual end`() {
        var s = started(PracticeMode.ENDLESS)
        repeat(20) { s = answerNext(s, 0) }
        assertEquals(EarSessionState.Phase.PRACTICE, s.phase)
        assertEquals(20, s.answeredCount)
        assertEquals(20, s.correctCount)
        assertEquals(20, s.bestStreak)

        s = EarSessionLogic.end(s)
        assertEquals(EarSessionState.Phase.RESULT, s.phase)
        assertEquals(EarSessionState.EndedReason.MANUAL_END, s.endedReason)
        assertEquals(1f, s.accuracy, 1e-6f)
    }

    @Test
    fun `end is a no-op outside practice`() {
        val s = EarSessionLogic.end(EarSessionState())
        assertEquals(EarSessionState.Phase.SETUP, s.phase)
        assertNull(s.endedReason)
    }

    @Test
    fun `a session cannot answer twice on the same question`() {
        val s = EarSessionLogic.answer(started(PracticeMode.ENDLESS), 0)
        val again = EarSessionLogic.answer(s, 2)
        assertEquals(s, again)
    }

    @Test
    fun `next question clears the previous answer`() {
        val s = EarSessionLogic.answer(started(PracticeMode.ENDLESS), 0)
        val next = EarSessionLogic.nextQuestion(s, question(answerIndex = 1))
        assertNull(next.selectedIndex)
        assertNull(next.isCorrect)
        assertEquals(1, next.question?.answerIndex)
        // cumulative stats survive
        assertEquals(1, next.answeredCount)
        assertEquals(1, next.correctCount)
    }

    @Test
    fun `back to setup clears the question and the ended reason`() {
        var s = started(PracticeMode.CHALLENGE)
        repeat(5) { s = answerNext(s, 2) }
        assertTrue(s.endedReason != null)
        val setup = EarSessionLogic.backToSetup(s)
        assertEquals(EarSessionState.Phase.SETUP, setup.phase)
        assertNull(setup.question)
        assertNull(setup.endedReason)
        assertFalse(setup.endedReason != null)
    }

    // ---- scored (non-choice) answers, e.g. rhythm performance -------------

    @Test
    fun `scored correct answer increments score and keeps the detail`() {
        val s = EarSessionLogic.answerScored(started(PracticeMode.ENDLESS), true, "节奏准确度 95%（4/4 音）")
        assertTrue(s.isCorrect == true)
        assertEquals(1, s.correctCount)
        assertEquals(1, s.streak)
        assertEquals("节奏准确度 95%（4/4 音）", s.answerDetail)
        assertEquals(-1, s.selectedIndex)
    }

    @Test
    fun `scored wrong answer resets the streak`() {
        val s = EarSessionLogic.answerScored(started(PracticeMode.ENDLESS), false, "节奏准确度 40%（4/4 音）")
        assertTrue(s.isCorrect == false)
        assertEquals(0, s.correctCount)
        assertEquals(0, s.streak)
        // The detail is still kept for the feedback banner.
        assertEquals("节奏准确度 40%（4/4 音）", s.answerDetail)
    }

    @Test
    fun `scored wrong answer costs a challenge life`() {
        var s = started(PracticeMode.CHALLENGE)
        repeat(5) { s = EarSessionLogic.answerScored(EarSessionLogic.nextQuestion(s, question()), false) }
        assertEquals(EarSessionState.Phase.RESULT, s.phase)
        assertEquals(EarSessionState.EndedReason.LIVES_OVER, s.endedReason)
        assertEquals(0, s.lives)
    }

    @Test
    fun `scored answers cannot be applied twice`() {
        val s = EarSessionLogic.answerScored(started(PracticeMode.ENDLESS), true)
        val again = EarSessionLogic.answerScored(s, false)
        assertEquals(s, again)
    }

    @Test
    fun `next question clears the scored detail`() {
        val s = EarSessionLogic.answerScored(started(PracticeMode.ENDLESS), true, "节奏准确度 90%（4/4 音）")
        val next = EarSessionLogic.nextQuestion(s, question())
        assertNull(next.answerDetail)
        assertNull(next.isCorrect)
    }
}
