package com.precisiontuner

import org.junit.Assert.assertEquals
import org.junit.Test

class TuneVisualStabilizerTest {

    // ---- entering IN_TUNE (4 consecutive frames within ±5¢) ----

    @Test fun `alternating four point nine and five point one cents never confirms`() {
        val s = TuneVisualStabilizer()
        repeat(20) {
            assertEquals(TuneVisualState.HIGH, s.observe("E4", 4.9, true))
            assertEquals(TuneVisualState.HIGH, s.observe("E4", 5.1, true))
        }
    }

    @Test fun `four consecutive frames within five cents confirm in tune`() {
        val s = TuneVisualStabilizer()
        assertEquals(TuneVisualState.HIGH, s.observe("E4", 4.9, true))
        assertEquals(TuneVisualState.HIGH, s.observe("E4", 4.9, true))
        assertEquals(TuneVisualState.HIGH, s.observe("E4", 4.9, true))
        assertEquals(TuneVisualState.IN_TUNE, s.observe("E4", 4.9, true))
        repeat(5) { assertEquals(TuneVisualState.IN_TUNE, s.observe("E4", 1.0, true)) }
    }

    @Test fun `a frame beyond five cents resets the confirmation streak`() {
        val s = TuneVisualStabilizer()
        repeat(3) { assertEquals(TuneVisualState.HIGH, s.observe("E4", 4.9, true)) }
        assertEquals(TuneVisualState.HIGH, s.observe("E4", 5.1, true)) // breaks the streak
        repeat(3) { assertEquals(TuneVisualState.HIGH, s.observe("E4", 4.9, true)) }
        assertEquals(TuneVisualState.IN_TUNE, s.observe("E4", 4.9, true)) // 4th fresh frame
    }

    @Test fun `negative cents map to low while confirming`() {
        val s = TuneVisualStabilizer()
        assertEquals(TuneVisualState.LOW, s.observe("E4", -4.9, true))
        assertEquals(TuneVisualState.LOW, s.observe("E4", -4.9, true))
        assertEquals(TuneVisualState.LOW, s.observe("E4", -4.9, true))
        assertEquals(TuneVisualState.IN_TUNE, s.observe("E4", -4.9, true))
    }

    @Test fun `far out of tune pitch shows the directional state`() {
        val s = TuneVisualStabilizer()
        assertEquals(TuneVisualState.LOW, s.observe("E4", -30.0, true))
        assertEquals(TuneVisualState.LOW, s.observe("E4", -30.0, true))
        assertEquals(TuneVisualState.HIGH, s.observe("E4", 30.0, true))
    }

    // ---- hysteresis: IN_TUNE is held through 5–8¢ jitter ----

    @Test fun `in tune holds through five to eight cent jitter`() {
        val s = TuneVisualStabilizer()
        repeat(4) { s.observe("E4", 2.0, true) }
        listOf(6.0, -6.5, 7.2, -5.5, 8.0, -8.0, 5.5).forEach { cents ->
            assertEquals(TuneVisualState.IN_TUNE, s.observe("E4", cents, true))
        }
    }

    @Test fun `leaves in tune only after three consecutive frames beyond eight cents`() {
        val s = TuneVisualStabilizer()
        repeat(4) { s.observe("E4", 2.0, true) }
        assertEquals(TuneVisualState.IN_TUNE, s.observe("E4", 8.5, true))
        assertEquals(TuneVisualState.IN_TUNE, s.observe("E4", 8.5, true))
        assertEquals(TuneVisualState.HIGH, s.observe("E4", 8.5, true))
    }

    @Test fun `leaves in tune toward low after three consecutive low far frames`() {
        val s = TuneVisualStabilizer()
        repeat(4) { s.observe("E4", 2.0, true) }
        assertEquals(TuneVisualState.IN_TUNE, s.observe("E4", -9.0, true))
        assertEquals(TuneVisualState.IN_TUNE, s.observe("E4", -9.0, true))
        assertEquals(TuneVisualState.LOW, s.observe("E4", -9.0, true))
    }

    @Test fun `an in band frame resets the far frame exit streak`() {
        val s = TuneVisualStabilizer()
        repeat(4) { s.observe("E4", 2.0, true) }
        assertEquals(TuneVisualState.IN_TUNE, s.observe("E4", 9.0, true))
        assertEquals(TuneVisualState.IN_TUNE, s.observe("E4", 9.0, true))
        assertEquals(TuneVisualState.IN_TUNE, s.observe("E4", 6.0, true)) // 5–8¢ band resets
        assertEquals(TuneVisualState.IN_TUNE, s.observe("E4", 9.0, true))
        assertEquals(TuneVisualState.IN_TUNE, s.observe("E4", 9.0, true))
        assertEquals(TuneVisualState.HIGH, s.observe("E4", 9.0, true)) // 3 fresh far frames
    }

    @Test fun `opposite side far frames reset the exit streak`() {
        val s = TuneVisualStabilizer()
        repeat(4) { s.observe("E4", 2.0, true) }
        assertEquals(TuneVisualState.IN_TUNE, s.observe("E4", 9.0, true)) // side streak: 1
        assertEquals(TuneVisualState.IN_TUNE, s.observe("E4", 9.0, true)) // 2
        assertEquals(TuneVisualState.IN_TUNE, s.observe("E4", -9.0, true)) // opposite side resets
        assertEquals(TuneVisualState.IN_TUNE, s.observe("E4", 9.0, true)) // fresh side streak: 1
        assertEquals(TuneVisualState.IN_TUNE, s.observe("E4", 9.0, true)) // 2
        assertEquals(TuneVisualState.HIGH, s.observe("E4", 9.0, true)) // 3 consecutive → exits
    }

    // ---- tolerance for brief detection failures ----

    @Test fun `one or two lost frames do not change the state`() {
        val s = TuneVisualStabilizer()
        repeat(4) { s.observe("E4", 2.0, true) }
        assertEquals(TuneVisualState.IN_TUNE, s.observeInvalid())
        assertEquals(TuneVisualState.IN_TUNE, s.observeInvalid())
        assertEquals(TuneVisualState.WAITING, s.observeInvalid()) // 3rd consecutive
        assertEquals(TuneVisualState.WAITING, s.observeInvalid())
    }

    @Test fun `lost frames during confirmation do not flicker the display`() {
        val s = TuneVisualStabilizer()
        assertEquals(TuneVisualState.HIGH, s.observe("E4", 4.9, true))
        assertEquals(TuneVisualState.HIGH, s.observeInvalid()) // brief dropout, counters frozen
        assertEquals(TuneVisualState.HIGH, s.observe("E4", 4.9, true))
        assertEquals(TuneVisualState.HIGH, s.observe("E4", 4.9, true))
        assertEquals(TuneVisualState.IN_TUNE, s.observe("E4", 4.9, true))
    }

    @Test fun `null cents count as lost input`() {
        val s = TuneVisualStabilizer()
        repeat(4) { s.observe("E4", 2.0, true) }
        assertEquals(TuneVisualState.IN_TUNE, s.observe("E4", null, true))
        assertEquals(TuneVisualState.IN_TUNE, s.observe("E4", null, true))
        assertEquals(TuneVisualState.WAITING, s.observe("E4", null, true))
    }

    @Test fun `non tracking frames behave like lost input`() {
        val s = TuneVisualStabilizer()
        repeat(4) { s.observe("E4", 2.0, true) }
        assertEquals(TuneVisualState.IN_TUNE, s.observe("E4", 30.0, false))
        assertEquals(TuneVisualState.IN_TUNE, s.observe("E4", 30.0, false))
        assertEquals(TuneVisualState.WAITING, s.observe("E4", 30.0, false))
    }

    @Test fun `input returns from waiting and re-confirms from scratch`() {
        val s = TuneVisualStabilizer()
        repeat(4) { s.observe("E4", 2.0, true) }
        repeat(3) { s.observeInvalid() } // -> WAITING
        assertEquals(TuneVisualState.HIGH, s.observe("E4", 4.9, true))
        assertEquals(TuneVisualState.HIGH, s.observe("E4", 4.9, true))
        assertEquals(TuneVisualState.HIGH, s.observe("E4", 4.9, true))
        assertEquals(TuneVisualState.IN_TUNE, s.observe("E4", 4.9, true))
    }

    // ---- target change resets ----

    @Test fun `changing target immediately resets and re-confirms`() {
        val s = TuneVisualStabilizer()
        repeat(4) { s.observe("E4", 2.0, true) }
        assertEquals(TuneVisualState.IN_TUNE, s.observe("E4", 2.0, true))
        // New target (new string / note): no stale verdict may carry over.
        assertEquals(TuneVisualState.HIGH, s.observe("A4", 3.0, true))
        assertEquals(TuneVisualState.HIGH, s.observe("A4", 3.0, true))
        assertEquals(TuneVisualState.HIGH, s.observe("A4", 3.0, true))
        assertEquals(TuneVisualState.IN_TUNE, s.observe("A4", 3.0, true))
    }

    @Test fun `changing target also clears lost frame tolerance`() {
        val s = TuneVisualStabilizer()
        repeat(4) { s.observe("E4", 2.0, true) }
        s.observeInvalid()
        s.observeInvalid()
        // Target switch mid-dropout: the stale IN_TUNE must not survive and the
        // lost-frame tolerance starts fresh.
        assertEquals(TuneVisualState.HIGH, s.observe("A4", 4.0, true))
        assertEquals(TuneVisualState.HIGH, s.observeInvalid()) // fresh streak: still tolerated
        assertEquals(TuneVisualState.HIGH, s.observeInvalid())
        assertEquals(TuneVisualState.WAITING, s.observeInvalid()) // 3 fresh lost frames
    }

    @Test fun `explicit reset returns to waiting`() {
        val s = TuneVisualStabilizer()
        repeat(4) { s.observe("E4", 2.0, true) }
        assertEquals(TuneVisualState.IN_TUNE, s.currentState)
        s.reset()
        assertEquals(TuneVisualState.WAITING, s.currentState)
        assertEquals(TuneVisualState.WAITING, s.observeInvalid())
    }
}
