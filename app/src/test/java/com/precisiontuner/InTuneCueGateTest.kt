package com.precisiontuner

import com.precisiontuner.pitch.InTuneCueGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InTuneCueGateTest {
    @Test fun `requires three center frames and does not chatter near boundary`() {
        val gate = InTuneCueGate(centerFramesRequired = 3)
        assertFalse(gate.observe("E3", 4.0, true))
        assertFalse(gate.observe("E3", 6.0, true))
        assertFalse(gate.observe("E3", 4.0, true))
        assertFalse(gate.observe("E3", 4.5, true))
        assertTrue(gate.observe("E3", 3.0, true))
        repeat(8) { index ->
            assertFalse(gate.observe("E3", if (index % 2 == 0) 4.0 else 7.0, true))
        }
    }

    @Test fun `rearms only after four far frames`() {
        val gate = InTuneCueGate(centerFramesRequired = 3)
        repeat(2) { assertFalse(gate.observe("E3", 0.0, true)) }
        assertTrue(gate.observe("E3", 0.0, true))
        repeat(3) { assertFalse(gate.observe("E3", 13.0, true)) }
        repeat(3) { assertFalse(gate.observe("E3", 0.0, true)) }
        repeat(4) { assertFalse(gate.observe("E3", 13.0, true)) }
        repeat(2) { assertFalse(gate.observe("E3", 0.0, true)) }
        assertTrue(gate.observe("E3", 0.0, true))
    }

    @Test fun `invalid frames neither rearm nor count as center confirmation`() {
        val gate = InTuneCueGate(centerFramesRequired = 3)
        repeat(2) { gate.observe("E3", 0.0, true) }
        gate.observeInvalid()
        repeat(2) { assertFalse(gate.observe("E3", 0.0, true)) }
        assertTrue(gate.observe("E3", 0.0, true))
        repeat(3) { gate.observe("E3", 20.0, true) }
        gate.observeInvalid()
        repeat(3) { gate.observe("E3", 20.0, true) }
        repeat(3) { assertFalse(gate.observe("E3", 0.0, true)) }
    }

    @Test fun `changing target immediately rearms`() {
        val gate = InTuneCueGate(centerFramesRequired = 3)
        repeat(3) { gate.observe("E3", 0.0, true) }
        repeat(2) { assertFalse(gate.observe("A3", 0.0, true)) }
        assertTrue(gate.observe("A3", 0.0, true))
    }

    // ---- default debounce: a momentary pass must not trigger the cue ----

    @Test fun `momentary glide through center does not trigger with default threshold`() {
        val gate = InTuneCueGate() // 10 frames ≈ 460 ms at 21.5 fps
        // A quick pass: in-tune for only 5 frames, then drifting away.
        repeat(5) { assertFalse(gate.observe("E4", 3.0, true)) }
        repeat(8) { assertFalse(gate.observe("E4", 9.0, true)) }
        repeat(5) { assertFalse(gate.observe("E4", 4.0, true)) }
        repeat(8) { assertFalse(gate.observe("E4", 15.0, true)) }
    }

    @Test fun `sustained in-tune pitch triggers exactly once with default threshold`() {
        val gate = InTuneCueGate()
        repeat(9) { assertFalse(gate.observe("E4", 1.5, true)) }
        assertTrue(gate.observe("E4", 1.5, true)) // 10th frame
        repeat(6) { assertFalse(gate.observe("E4", 0.5, true)) } // stays in tune, no re-cue
    }

    @Test fun `interruption during confirmation resets the counter`() {
        val gate = InTuneCueGate()
        repeat(7) { assertFalse(gate.observe("E4", 2.0, true)) }
        gate.observeInvalid() // brief dropout
        repeat(9) { assertFalse(gate.observe("E4", 2.0, true)) }
        assertTrue(gate.observe("E4", 2.0, true))
    }
}
