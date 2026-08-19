package com.precisiontuner.ear

/** Quiz session mode. */
enum class PracticeMode {
    /** 无尽模式: unlimited questions; the user ends the round manually. */
    ENDLESS,

    /** 挑战模式: unlimited questions; each wrong answer costs a life and
     *  five cumulative errors end the challenge with a final score. */
    CHALLENGE,

    /** 测试模式: a fixed number of questions, then a final score. */
    TEST,
}
