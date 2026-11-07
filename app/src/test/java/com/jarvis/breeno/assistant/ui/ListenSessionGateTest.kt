package com.jarvis.breeno.assistant.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenSessionGateTest {
    @Test
    fun rejectsOverlappingSessionsAndAllowsNextAfterRelease() {
        val gate = ListenSessionGate()

        assertTrue(gate.tryEnter())
        assertFalse(gate.tryEnter())

        gate.leave()

        assertTrue(gate.tryEnter())
        gate.leave()
    }

    @Test
    fun failedEntryDoesNotReleaseTheActiveSession() {
        val gate = ListenSessionGate()

        assertTrue(gate.tryEnter())
        assertFalse(gate.tryEnter())
        assertFalse(gate.tryEnter())

        gate.leave()
        assertTrue(gate.tryEnter())
        gate.leave()
    }
}
