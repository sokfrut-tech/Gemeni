package com.jarvis.breeno.assistant.voice

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File

/** Real local Russian ASR backed by Sherpa-ONNX Zipformer2. */
class SherpaRussianAsrEngine {
    @Volatile private var recognizer: OnlineRecognizer? = null

    @Synchronized
    fun load(modelDir: File) {
        require(modelDir.isDirectory) { "Russian ASR model directory is missing" }
        val encoder = modelDir.resolve("encoder.int8.onnx")
        val decoder = modelDir.resolve("decoder.onnx")
        val joiner = modelDir.resolve("joiner.int8.onnx")
        val tokens = modelDir.resolve("tokens.txt")
        require(listOf(encoder, decoder, joiner, tokens).all(File::isFile)) {
            "Incomplete Russian Sherpa-ONNX model: ${modelDir.absolutePath}"
        }

        recognizer?.release()
        recognizer = OnlineRecognizer(
            config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = encoder.absolutePath,
                        decoder = decoder.absolutePath,
                        joiner = joiner.absolutePath,
                    ),
                    tokens = tokens.absolutePath,
                    numThreads = 2,
                    provider = "cpu",
                    modelType = "zipformer2",
                ),
                enableEndpoint = true,
                decodingMethod = "greedy_search",
            )
        )
    }

    @Synchronized
    fun transcribe(samples: ShortArray, sampleRate: Int = 16_000): String {
        val r = recognizer ?: return ""
        if (samples.isEmpty()) return ""
        val stream = r.createStream()
        try {
            val pcm = FloatArray(samples.size) { samples[it] / 32768.0f }
            stream.acceptWaveform(pcm, sampleRate)
            stream.inputFinished()
            while (r.isReady(stream)) r.decode(stream)
            return r.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    fun close() {
        synchronized(this) {
            recognizer?.release()
            recognizer = null
        }
    }
}
