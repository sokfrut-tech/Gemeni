package com.jarvis.breeno.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordAudioFrameRouterTest {
    @Test
    fun frameIsSentToKwsOrVadButNeverBoth() {
        val session = WakeWordSessionStateMachine()
        val calls = mutableListOf<String>()
        val router = WakeWordAudioFrameRouter(
            session = session,
            detectHotword = { calls += "kws"; true },
            captureCommandFrame = { calls += "vad" }
        )

        assertEquals(WakeWordAudioFrameRouter.Result.IGNORED, router.accept(shortArrayOf()))
        assertTrue(session.start())
        assertTrue(session.modelsReady())

        assertEquals(
            WakeWordAudioFrameRouter.Result.HOTWORD_DETECTED,
            router.accept(shortArrayOf(1, 2))
        )
        assertEquals(listOf("kws"), calls)
        assertEquals(WakeWordSessionStateMachine.State.CAPTURING_COMMAND, session.state)

        assertEquals(
            WakeWordAudioFrameRouter.Result.COMMAND_FRAME,
            router.accept(shortArrayOf(3, 4))
        )
        assertEquals(listOf("kws", "vad"), calls)
    }

    @Test
    fun noMatchKeepsFramesInKwsAndStopPreventsFurtherDispatch() {
        val session = WakeWordSessionStateMachine()
        var kwsCalls = 0
        var vadCalls = 0
        val router = WakeWordAudioFrameRouter(
            session = session,
            detectHotword = { kwsCalls++; false },
            captureCommandFrame = { vadCalls++ }
        )

        assertTrue(session.start())
        assertTrue(session.modelsReady())
        assertEquals(WakeWordAudioFrameRouter.Result.WAITING_FOR_HOTWORD, router.accept(shortArrayOf(1)))
        assertEquals(1, kwsCalls)
        assertEquals(0, vadCalls)

        session.stop()
        assertEquals(WakeWordAudioFrameRouter.Result.IGNORED, router.accept(shortArrayOf(2)))
        assertEquals(1, kwsCalls)
        assertEquals(0, vadCalls)
        assertFalse(session.hotwordDetected())
    }
}
