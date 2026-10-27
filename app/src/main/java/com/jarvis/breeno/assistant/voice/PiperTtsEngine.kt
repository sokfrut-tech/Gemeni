package com.jarvis.breeno.assistant.voice

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import java.io.File

/** Local VITS/Piper-compatible TTS through the actual Sherpa-ONNX Kotlin API. */
class PiperTtsEngine {
    private var tts: OfflineTts? = null

    @Synchronized
    fun load(modelDir: File) {
        val model = modelDir.resolve("model.onnx")
        val tokens = modelDir.resolve("tokens.txt")
        val data = modelDir.resolve("espeak-ng-data")
        require(model.isFile && tokens.isFile) { "TTS model is incomplete: ${modelDir.absolutePath}" }
        require(data.isDirectory) { "espeak-ng-data is missing: ${data.absolutePath}" }

        tts?.release()
        val config = getOfflineTtsConfig(
            modelDir = modelDir.absolutePath,
            modelName = model.name,
            dataDir = data.absolutePath,
            numThreads = 2,
        )
        tts = OfflineTts(config = config)
    }

    @Synchronized
    fun synthesize(text: String, speakerId: Int = 0, speed: Float = 1.0f): FloatArray {
        require(text.isNotBlank()) { "TTS text must not be blank" }
        val engine = tts ?: error("TTS engine is not loaded")
        return engine.generate(text = text, sid = speakerId, speed = speed).samples
    }

    @Synchronized
    fun sampleRate(): Int = tts?.sampleRate() ?: error("TTS engine is not loaded")

    @Synchronized
    fun close() {
        tts?.release()
        tts = null
    }
}
