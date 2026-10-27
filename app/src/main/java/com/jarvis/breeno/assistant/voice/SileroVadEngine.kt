package com.jarvis.breeno.assistant.voice

import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.File

/** Streaming speech gate. It does not perform recognition; it only segments speech. */
class SileroVadEngine {
    companion object { const val SAMPLE_RATE = 16_000 }

    private var vad: Vad? = null

    @Synchronized
    fun load(modelFile: File) {
        require(modelFile.isFile) { "Silero VAD model is missing: ${modelFile.absolutePath}" }
        vad?.release()
        vad = Vad(
            config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = modelFile.absolutePath,
                    threshold = 0.5f,
                    minSilenceDuration = 0.25f,
                    minSpeechDuration = 0.20f,
                    windowSize = 512,
                    maxSpeechDuration = 15.0f,
                ),
                sampleRate = SAMPLE_RATE,
                numThreads = 1,
                provider = "cpu",
            )
        )
    }

    @Synchronized
    fun accept(samples: ShortArray): Boolean {
        val engine = vad ?: error("Silero VAD is not loaded")
        if (samples.isEmpty()) return false
        engine.acceptWaveform(FloatArray(samples.size) { samples[it] / 32768.0f })
        return engine.isSpeechDetected()
    }

    @Synchronized
    fun reset() { vad?.reset() }

    @Synchronized
    fun close() { vad?.release(); vad = null }
}
