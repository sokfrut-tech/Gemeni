package com.jarvis.breeno.assistant.voice

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File

/** Local VITS/Piper-compatible TTS through Sherpa-ONNX. */
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
        tts = OfflineTts(
            OfflineTtsConfig(
                modelConfig = OfflineTtsVitsModelConfig(
                    model = model.absolutePath,
                    tokens = tokens.absolutePath,
                    dataDir = data.absolutePath,
                ),
                numThreads = 2,
                provider = "cpu",
            )
        )
    }

    @Synchronized
    fun synthesize(text: String, speakerId: Int = 0, speed: Float = 1.0f): FloatArray {
        require(text.isNotBlank()) { "TTS text must not be blank" }
        val engine = tts ?: error("TTS engine is not loaded")
        val audio = engine.generate(text = text, sid = speakerId, speed = speed)
        return audio.samples
    }

    @Synchronized
    fun close() {
        tts?.release()
        tts = null
    }
}
