package com.precisiontuner

import com.precisiontuner.ui.shouldTriggerEdgeSwipe
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeSwipeBackTest {
    private val edge = 24f
    private val distance = 72f

    @Test fun `right swipe from edge beyond threshold returns`() {
        assertTrue(shouldTriggerEdgeSwipe(20f, 80f, 10f, edge, distance))
    }

    @Test fun `gesture outside edge does not return`() {
        assertFalse(shouldTriggerEdgeSwipe(25f, 100f, 0f, edge, distance))
    }

    @Test fun `short or leftward gesture does not return`() {
        assertFalse(shouldTriggerEdgeSwipe(10f, 71f, 0f, edge, distance))
        assertFalse(shouldTriggerEdgeSwipe(10f, -100f, 0f, edge, distance))
    }

    @Test fun `mostly vertical gesture does not return`() {
        assertFalse(shouldTriggerEdgeSwipe(10f, 80f, 60f, edge, distance))
    }
}
