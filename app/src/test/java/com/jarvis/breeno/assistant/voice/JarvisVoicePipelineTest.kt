package com.jarvis.breeno.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisVoicePipelineTest {
    @Test
    fun wakeWordRoutesFollowingFramesThroughVadAndLocalAsr() {
        val wake = FakeWakeWordGate()
        val vad = FakeSpeechSegmenter()
        val asr = FakeTranscriber()
        val pipeline = JarvisVoicePipeline(wake, vad, asr)

        wake.nextFrameTriggers = true
        assertNull(pipeline.process(shortArrayOf(1)))
        assertFalse(wake.armed)
        assertEquals(0, vad.acceptCount) // wake frame is not reused as command audio

        vad.nextSegment = floatArrayOf(0.25f, -0.25f)
        assertEquals("привет", pipeline.process(shortArrayOf(2)))
        assertEquals(1, vad.acceptCount)
        assertEquals(16_000, asr.lastSampleRate)
        assertEquals(2, asr.lastSamples?.size)
        assertTrue(wake.armed)
    }

    @Test
    fun interruptionRejectsFramesUntilExplicitOptInResume() {
        val wake = FakeWakeWordGate()
        val vad = FakeSpeechSegmenter()
        val pipeline = JarvisVoicePipeline(wake, vad, FakeTranscriber())

        pipeline.startCommandMode()
        assertFalse(wake.armed)
        pipeline.suspendForInterruption()
        val resetCountAfterSuspend = vad.resetCount

        assertNull(pipeline.process(shortArrayOf(7)))
        assertEquals(0, vad.acceptCount)
        assertFalse(pipeline.resumeAfterInterruption(userOptInStillActive = false))
        assertFalse(wake.armed)
        assertNull(pipeline.process(shortArrayOf(8)))
        assertEquals(resetCountAfterSuspend, vad.resetCount)

        assertTrue(pipeline.resumeAfterInterruption(userOptInStillActive = true))
        assertTrue(wake.armed)
        assertNull(pipeline.process(shortArrayOf(9)))
        assertEquals(0, vad.acceptCount)
    }

    @Test
    fun blankAsrResultKeepsCommandModeActiveForFurtherSpeech() {
        val wake = FakeWakeWordGate()
        val vad = FakeSpeechSegmenter()
        val asr = FakeTranscriber().apply { result = "" }
        val pipeline = JarvisVoicePipeline(wake, vad, asr)

        pipeline.startCommandMode()
        vad.nextSegment = floatArrayOf(0.1f)
        assertNull(pipeline.process(shortArrayOf(1)))
        assertFalse(wake.armed)
        assertEquals(1, vad.acceptCount)
    }

    private class FakeWakeWordGate : WakeWordGate {
        var armed = true
        var nextFrameTriggers = false
        override fun arm() { armed = true }
        override fun disarm() { armed = false }
        override fun accepts(samples: ShortArray): Boolean {
            if (!armed || !nextFrameTriggers) return false
            nextFrameTriggers = false
            return true
        }
    }

    private class FakeSpeechSegmenter : SpeechSegmenter {
        var acceptCount = 0
        var resetCount = 0
        var nextSegment: FloatArray? = null
        override fun accept(samples: ShortArray): Boolean {
            acceptCount++
            return true
        }
        override fun pollSegment(): FloatArray? = nextSegment.also { nextSegment = null }
        override fun reset() { resetCount++; nextSegment = null }
    }

    private class FakeTranscriber : LocalSpeechTranscriber {
        var result = "привет"
        var lastSamples: ShortArray? = null
        var lastSampleRate = 0
        override fun transcribe(samples: ShortArray, sampleRate: Int): String {
            lastSamples = samples
            lastSampleRate = sampleRate
            return result
        }
    }
}
