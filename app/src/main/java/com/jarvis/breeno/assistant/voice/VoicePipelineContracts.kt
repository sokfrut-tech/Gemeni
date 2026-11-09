package com.jarvis.breeno.assistant.voice

/** Small contracts keep the voice state machine testable without native ONNX models. */
interface WakeWordGate {
    fun arm()
    fun disarm()
    fun accepts(samples: ShortArray): Boolean
}

interface SpeechSegmenter {
    fun accept(samples: ShortArray): Boolean
    fun pollSegment(): FloatArray?
    fun reset()
}

interface LocalSpeechTranscriber {
    fun transcribe(samples: ShortArray, sampleRate: Int): String
}
