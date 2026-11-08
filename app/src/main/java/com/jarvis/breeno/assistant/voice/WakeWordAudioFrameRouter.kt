package com.jarvis.breeno.assistant.voice

/**
 * Routes each microphone frame to exactly one stage of an explicitly started
 * wake-word session. The owner of AudioRecord remains AudioRecordPipeline;
 * this router never opens or owns a recorder.
 *
 * A frame that triggers the keyword is consumed by KWS only. VAD starts with
 * the next frame, preventing the wake-word tail from becoming command audio.
 */
internal class WakeWordAudioFrameRouter(
    private val session: WakeWordSessionStateMachine,
    private val detectHotword: (ShortArray) -> Boolean,
    private val captureCommandFrame: (ShortArray) -> Unit
) {
    enum class Result {
        IGNORED,
        WAITING_FOR_HOTWORD,
        HOTWORD_DETECTED,
        COMMAND_FRAME
    }

    fun accept(frame: ShortArray): Result {
        if (frame.isEmpty()) return Result.IGNORED
        return when (session.state) {
            WakeWordSessionStateMachine.State.WAITING_FOR_HOTWORD -> {
                if (detectHotword(frame)) {
                    if (session.hotwordDetected()) Result.HOTWORD_DETECTED else Result.IGNORED
                } else {
                    Result.WAITING_FOR_HOTWORD
                }
            }
            WakeWordSessionStateMachine.State.CAPTURING_COMMAND -> {
                captureCommandFrame(frame)
                Result.COMMAND_FRAME
            }
            else -> Result.IGNORED
        }
    }
}
