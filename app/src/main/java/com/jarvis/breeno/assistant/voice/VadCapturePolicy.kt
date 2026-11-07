package com.jarvis.breeno.assistant.voice

/**
 * Pure Kotlin endpoint policy. Elapsed time is supplied by the caller so this
 * state machine can be tested deterministically without microphone or ONNX.
 */
internal class VadCapturePolicy(
    private val noSpeechTimeoutMs: Long = DEFAULT_NO_SPEECH_TIMEOUT_MS,
    private val hardCaptureLimitMs: Long = DEFAULT_HARD_CAPTURE_LIMIT_MS
) {
    enum class Decision {
        CONTINUE,
        NO_SPEECH_TIMEOUT,
        ENDPOINT,
        HARD_LIMIT
    }

    private var speechSeen = false

    init {
        require(noSpeechTimeoutMs > 0)
        require(hardCaptureLimitMs > noSpeechTimeoutMs)
    }

    fun onFrame(
        elapsedMs: Long,
        speechDetected: Boolean,
        completedSegmentAvailable: Boolean
    ): Decision {
        require(elapsedMs >= 0)
        if (speechDetected) speechSeen = true
        if (completedSegmentAvailable && speechSeen) return Decision.ENDPOINT
        if (!speechSeen && elapsedMs >= noSpeechTimeoutMs) return Decision.NO_SPEECH_TIMEOUT
        if (elapsedMs >= hardCaptureLimitMs) return Decision.HARD_LIMIT
        return Decision.CONTINUE
    }

    companion object {
        const val DEFAULT_NO_SPEECH_TIMEOUT_MS = 8_000L
        const val DEFAULT_HARD_CAPTURE_LIMIT_MS = 30_000L
    }
}
