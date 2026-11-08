package com.jarvis.breeno.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordSessionStateMachineTest {
    @Test
    fun followsExplicitOptInHotwordCommandLifecycle() {
        val machine = WakeWordSessionStateMachine()
        assertEquals(WakeWordSessionStateMachine.State.STOPPED, machine.state)
        assertTrue(machine.start())
        assertEquals(WakeWordSessionStateMachine.State.PREPARING, machine.state)
        assertTrue(machine.modelsReady())
        assertEquals(WakeWordSessionStateMachine.State.WAITING_FOR_HOTWORD, machine.state)
        assertTrue(machine.hotwordDetected())
        assertEquals(WakeWordSessionStateMachine.State.CAPTURING_COMMAND, machine.state)
        assertTrue(machine.commandCaptured())
        assertEquals(WakeWordSessionStateMachine.State.PROCESSING, machine.state)
        assertTrue(machine.commandFinished())
        assertEquals(WakeWordSessionStateMachine.State.WAITING_FOR_HOTWORD, machine.state)
    }

    @Test
    fun rejectsOutOfOrderEvents() {
        val machine = WakeWordSessionStateMachine()
        assertFalse(machine.hotwordDetected())
        assertFalse(machine.modelsReady())
        assertTrue(machine.start())
        assertFalse(machine.commandCaptured())
        assertFalse(machine.commandFinished())
        assertEquals(WakeWordSessionStateMachine.State.PREPARING, machine.state)
    }

    @Test
    fun stopIsTerminalUntilAnotherExplicitStart() {
        val machine = WakeWordSessionStateMachine()
        assertTrue(machine.start())
        machine.stop()
        assertEquals(WakeWordSessionStateMachine.State.STOPPED, machine.state)
        assertFalse(machine.hotwordDetected())
        assertTrue(machine.start())
    }

    @Test
    fun failureRequiresExplicitRestart() {
        val machine = WakeWordSessionStateMachine()
        assertTrue(machine.start())
        machine.fail()
        assertEquals(WakeWordSessionStateMachine.State.ERROR, machine.state)
        assertFalse(machine.modelsReady())
        assertTrue(machine.start())
        assertEquals(WakeWordSessionStateMachine.State.PREPARING, machine.state)
    }
}
