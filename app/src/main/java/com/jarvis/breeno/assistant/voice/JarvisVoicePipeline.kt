package com.jarvis.breeno.assistant.voice

/**
 * Routes one already-owned microphone frame stream:
 * acoustic wake word -> VAD command segment -> local Russian ASR.
 *
 * This class does not open the microphone. Its caller must feed frames from the
 * single AudioCaptureCoordinator owner and stop feeding them on interruption.
 */
class JarvisVoicePipeline(
    private val hotword: WakeWordGate,
    private val vad: SpeechSegmenter,
    private val asr: LocalSpeechTranscriber,
) {
    @Volatile private var listeningForCommand = false
    @Volatile private var suspended = false

    @Synchronized
    fun startCommandMode() {
        if (suspended) return
        listeningForCommand = true
        vad.reset()
        hotword.disarm()
    }

    @Synchronized
    fun stopCommandMode() {
        listeningForCommand = false
        vad.reset()
        if (!suspended) hotword.arm()
    }

    /**
     * An interruption is fail-closed: clear pending speech, disarm wake-word
     * processing, and reject all frames until explicit user-authorized resume.
     */
    @Synchronized
    fun suspendForInterruption() {
        suspended = true
        listeningForCommand = false
        hotword.disarm()
        vad.reset()
    }

    /** Resume only after the app has independently revalidated permission and consent. */
    @Synchronized
    fun resumeAfterInterruption(userOptInStillActive: Boolean): Boolean {
        if (!suspended || !userOptInStillActive) return false
        vad.reset()
        suspended = false
        hotword.arm()
        return true
    }

    @Synchronized
    fun process(frame: ShortArray): String? {
        if (suspended || frame.isEmpty()) return null

        if (!listeningForCommand) {
            if (hotword.accepts(frame)) startCommandMode()
            return null
        }

        vad.accept(frame)
        val segment = vad.pollSegment() ?: return null
        val text = asr.transcribe(
            ShortArray(segment.size) {
                (segment[it] * 32768f).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            },
            SileroVadEngine.SAMPLE_RATE,
        )
        if (text.isBlank()) return null
        stopCommandMode()
        return text
    }
}
