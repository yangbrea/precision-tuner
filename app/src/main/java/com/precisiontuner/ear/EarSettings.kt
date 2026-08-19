package com.precisiontuner.ear

/**
 * Persisted settings for the ear-training module. Difficulty is per-exercise
 * (kept in the ViewModel, keyed by [ExerciseType]); these fields are the
 * exercise-specific extras shown on the setup view.
 */
data class EarSettings(
    val melodicInterval: Boolean = true, // true = 旋律音程, false = 和声音程
    val noteReferenceTone: Boolean = false, // 单音识别: true = 先播 C4 基准音再出题
    val testQuestionCount: Int = 10,
)
