package com.precisiontuner.ear

/** Scale definitions as semitone offsets from the tonic (one octave, ascending). */
object ScaleLibrary {

    data class Scale(val name: String, val intervals: List<Int>)

    /** 大小调体系: major and the three minor forms. */
    val MAJOR_MINOR: List<Scale> = listOf(
        Scale("大调", listOf(0, 2, 4, 5, 7, 9, 11, 12)),
        Scale("自然小调", listOf(0, 2, 3, 5, 7, 8, 10, 12)),
        Scale("和声小调", listOf(0, 2, 3, 5, 7, 8, 11, 12)),
        Scale("旋律小调", listOf(0, 2, 3, 5, 7, 9, 11, 12)),
    )

    /** 中古调式: the four common church modes. */
    val MODES: List<Scale> = listOf(
        Scale("多利亚", listOf(0, 2, 3, 5, 7, 9, 10, 12)),
        Scale("弗里几亚", listOf(0, 1, 3, 5, 7, 8, 10, 12)),
        Scale("利底亚", listOf(0, 2, 4, 6, 7, 9, 11, 12)),
        Scale("混合利底亚", listOf(0, 2, 4, 5, 7, 9, 10, 12)),
    )

    /** Every scale. */
    val ALL: List<Scale> = MAJOR_MINOR + MODES
}
