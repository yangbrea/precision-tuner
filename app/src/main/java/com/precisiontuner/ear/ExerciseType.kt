package com.precisiontuner.ear

/** Which ear-training exercise is active inside the 视听练耳 section. */
enum class ExerciseType {
    /** 单音识别 — identify the played note by name. */
    NOTE,

    /** 音程听辨 — identify the interval between two notes. */
    INTERVAL,

    /** 和弦听辨 — identify the chord type. */
    CHORD,

    /** 音阶听辨 — identify the scale. */
    SCALE,

    /** 五线谱识谱 — read a staff note and name it (visual only, no playback). */
    STAFF_READING,

    /** 节奏听写 — read the staff rhythm, then reproduce it by tapping. */
    RHYTHM,
}
