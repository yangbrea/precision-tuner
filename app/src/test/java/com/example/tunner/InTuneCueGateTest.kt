package com.example.tunner

import com.example.tunner.pitch.InTuneCueGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InTuneCueGateTest {
    @Test fun `requires three center frames and does not chatter near boundary`() {
        val gate = InTuneCueGate()
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
        val gate = InTuneCueGate()
        repeat(2) { assertFalse(gate.observe("E3", 0.0, true)) }
        assertTrue(gate.observe("E3", 0.0, true))
        repeat(3) { assertFalse(gate.observe("E3", 13.0, true)) }
        repeat(3) { assertFalse(gate.observe("E3", 0.0, true)) }
        repeat(4) { assertFalse(gate.observe("E3", 13.0, true)) }
        repeat(2) { assertFalse(gate.observe("E3", 0.0, true)) }
        assertTrue(gate.observe("E3", 0.0, true))
    }

    @Test fun `invalid frames neither rearm nor count as center confirmation`() {
        val gate = InTuneCueGate()
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
        val gate = InTuneCueGate()
        repeat(3) { gate.observe("E3", 0.0, true) }
        repeat(2) { assertFalse(gate.observe("A3", 0.0, true)) }
        assertTrue(gate.observe("A3", 0.0, true))
    }
}
