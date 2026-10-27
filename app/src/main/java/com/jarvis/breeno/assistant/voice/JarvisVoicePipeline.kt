package com.jarvis.breeno.assistant.voice

/**
 * Connects the always-on audio path without sending raw microphone audio to the
 * cloud. KWS runs continuously; after a wake event Silero segments the command
 * and the Russian Sherpa recognizer transcribes the completed segment locally.
 */
class JarvisVoicePipeline(
    private val hotword: HotwordDetector,
    private val vad: SileroVadEngine,
    private val asr: SherpaRussianAsrEngine,
) {
    @Volatile private var listeningForCommand = false

    fun startCommandMode() {
        listeningForCommand = true
        vad.reset()
        hotword.disarm()
    }

    fun stopCommandMode() {
        listeningForCommand = false
        vad.reset()
        hotword.arm()
    }

    /** Returns a completed local transcript, or null until the command is complete. */
    fun process(frame: ShortArray): String? {
        if (frame.isEmpty()) return null

        if (!listeningForCommand) {
            if (hotword.accepts(frame)) {
                startCommandMode()
            }
            return null
        }

        vad.accept(frame)
        val segment = vad.pollSegment() ?: return null
        val text = asr.transcribe(
            ShortArray(segment.size) { (segment[it] * 32768f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort() },
            SileroVadEngine.SAMPLE_RATE,
        )
        if (text.isBlank()) return null
        stopCommandMode()
        return text
    }
}
