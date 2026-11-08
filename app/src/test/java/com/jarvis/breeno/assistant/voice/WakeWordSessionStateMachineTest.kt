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

    @Test
    fun callSuspendsEveryActiveLifecycleStage() {
        val activeStates = listOf(
            WakeWordSessionStateMachine.State.PREPARING,
            WakeWordSessionStateMachine.State.WAITING_FOR_HOTWORD,
            WakeWordSessionStateMachine.State.CAPTURING_COMMAND,
            WakeWordSessionStateMachine.State.PROCESSING
        )
        activeStates.forEach { active ->
            val machine = WakeWordSessionStateMachine()
            assertTrue(machine.start())
            when (active) {
                WakeWordSessionStateMachine.State.PREPARING -> Unit
                WakeWordSessionStateMachine.State.WAITING_FOR_HOTWORD -> assertTrue(machine.modelsReady())
                WakeWordSessionStateMachine.State.CAPTURING_COMMAND -> {
                    assertTrue(machine.modelsReady()); assertTrue(machine.hotwordDetected())
                }
                WakeWordSessionStateMachine.State.PROCESSING -> {
                    assertTrue(machine.modelsReady()); assertTrue(machine.hotwordDetected())
                    assertTrue(machine.commandCaptured())
                }
                else -> error("Unexpected test state")
            }
            assertTrue(machine.suspendForCall())
            assertEquals(WakeWordSessionStateMachine.State.SUSPENDED_FOR_CALL, machine.state)
            assertFalse(machine.commandFinished())
        }
    }

    @Test
    fun callInterruptionDoesNotResumeWithoutExplicitOptIn() {
        val machine = WakeWordSessionStateMachine()
        assertTrue(machine.start())
        assertTrue(machine.modelsReady())
        assertTrue(machine.suspendForCall())
        assertFalse(machine.resumeAfterInterruption(userOptInStillActive = false))
        assertEquals(WakeWordSessionStateMachine.State.SUSPENDED_FOR_CALL, machine.state)
        assertTrue(machine.resumeAfterInterruption(userOptInStillActive = true))
        assertEquals(WakeWordSessionStateMachine.State.PREPARING, machine.state)
    }

    @Test
    fun systemAudioContentionRequiresExplicitResume() {
        val machine = WakeWordSessionStateMachine()
        assertTrue(machine.start())
        assertTrue(machine.modelsReady())
        assertTrue(machine.suspendForSystemAudio())
        assertFalse(machine.resumeAfterInterruption(userOptInStillActive = false))
        assertTrue(machine.resumeAfterInterruption(userOptInStillActive = true))
        assertEquals(WakeWordSessionStateMachine.State.PREPARING, machine.state)
    }

    @Test
    fun suspendedSessionCannotRestartOrReceiveFramesUntilExplicitResume() {
        val machine = WakeWordSessionStateMachine()
        assertTrue(machine.start())
        assertTrue(machine.modelsReady())
        assertTrue(machine.suspendForCall())
        assertFalse(machine.start())
        assertFalse(machine.hotwordDetected())
        assertFalse(machine.suspendForSystemAudio())
        assertEquals(WakeWordSessionStateMachine.State.SUSPENDED_FOR_CALL, machine.state)
    }
}
