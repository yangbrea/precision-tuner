package com.precisiontuner.ear

/**
 * Maps an accuracy fraction to a result-tier label (pure, testable).
 */
object ResultTier {

    fun label(accuracy: Float): String = when {
        accuracy >= 0.90f -> "听力大师！"
        accuracy >= 0.70f -> "渐入佳境"
        accuracy >= 0.50f -> "继续练习"
        else -> "基础仍需巩固"
    }
}
