package com.jarvis.breeno.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VadCapturePolicyTest {
    @Test
    fun keepsListeningBeforeSpeechAndTimesOutAtEightSeconds() {
        val policy = VadCapturePolicy()
        assertEquals(
            VadCapturePolicy.Decision.CONTINUE,
            policy.onFrame(7_999, speechDetected = false, completedSegmentAvailable = false)
        )
        assertEquals(
            VadCapturePolicy.Decision.NO_SPEECH_TIMEOUT,
            policy.onFrame(8_000, speechDetected = false, completedSegmentAvailable = false)
        )
    }

    @Test
    fun doesNotApplyNoSpeechTimeoutAfterSpeechHasStarted() {
        val policy = VadCapturePolicy()
        assertEquals(
            VadCapturePolicy.Decision.CONTINUE,
            policy.onFrame(2_000, speechDetected = true, completedSegmentAvailable = false)
        )
        assertEquals(
            VadCapturePolicy.Decision.CONTINUE,
            policy.onFrame(8_000, speechDetected = false, completedSegmentAvailable = false)
        )
    }

    @Test
    fun completesOnlyWhenVadPublishesAnEndpointSegment() {
        val policy = VadCapturePolicy()
        policy.onFrame(1_000, speechDetected = true, completedSegmentAvailable = false)
        assertEquals(
            VadCapturePolicy.Decision.CONTINUE,
            policy.onFrame(1_400, speechDetected = false, completedSegmentAvailable = false)
        )
        assertEquals(
            VadCapturePolicy.Decision.ENDPOINT,
            policy.onFrame(1_600, speechDetected = false, completedSegmentAvailable = true)
        )
    }

    @Test
    fun enforcesHardThirtySecondLimitForLongSpeech() {
        val policy = VadCapturePolicy()
        policy.onFrame(1_000, speechDetected = true, completedSegmentAvailable = false)
        assertEquals(
            VadCapturePolicy.Decision.HARD_LIMIT,
            policy.onFrame(30_000, speechDetected = true, completedSegmentAvailable = false)
        )
    }

    @Test
    fun endpointWinsWhenFinalSegmentArrivesAtHardLimit() {
        val policy = VadCapturePolicy()
        policy.onFrame(1_000, speechDetected = true, completedSegmentAvailable = false)
        assertEquals(
            VadCapturePolicy.Decision.ENDPOINT,
            policy.onFrame(30_000, speechDetected = false, completedSegmentAvailable = true)
        )
    }

    @Test
    fun rejectsInvalidTimeoutConfiguration() {
        assertEquals(
            true,
            runCatching { VadCapturePolicy(noSpeechTimeoutMs = 0) }.isFailure
        )
        assertEquals(
            true,
            runCatching {
                VadCapturePolicy(noSpeechTimeoutMs = 10_000, hardCaptureLimitMs = 10_000)
            }.isFailure
        )
    }
}
