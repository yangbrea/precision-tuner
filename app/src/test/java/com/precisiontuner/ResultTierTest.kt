package com.precisiontuner

import com.precisiontuner.ear.ResultTier
import org.junit.Assert.assertEquals
import org.junit.Test

class ResultTierTest {

    @Test
    fun `tiers follow the accuracy boundaries`() {
        assertEquals("听力大师！", ResultTier.label(1.0f))
        assertEquals("听力大师！", ResultTier.label(0.90f))
        assertEquals("渐入佳境", ResultTier.label(0.89f))
        assertEquals("渐入佳境", ResultTier.label(0.70f))
        assertEquals("继续练习", ResultTier.label(0.69f))
        assertEquals("继续练习", ResultTier.label(0.50f))
        assertEquals("基础仍需巩固", ResultTier.label(0.49f))
        assertEquals("基础仍需巩固", ResultTier.label(0.0f))
    }
}
