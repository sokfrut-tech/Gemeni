package com.jarvis.breeno.assistant.voice

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File

/**
 * Always-on acoustic wake-word detector. The bundled KWS model is zh/en; the
 * wake word is encoded as the English phonemes for "Jarvis". Russian ASR is
 * deliberately not used for wake detection because that would require decoding
 * the microphone continuously and would increase CPU use and false triggers.
 */
class SherpaJarvisKwsEngine {
    companion object {
        const val SAMPLE_RATE = 16_000
        const val KEYWORD = "JARVIS"
    }

    private var spotter: KeywordSpotter? = null
    private var stream: com.k2fsa.sherpa.onnx.OnlineStream? = null

    @Synchronized
    fun load(modelDir: File) {
        val encoder = modelDir.resolve("encoder.onnx")
        val decoder = modelDir.resolve("decoder.onnx")
        val joiner = modelDir.resolve("joiner.onnx")
        val tokens = modelDir.resolve("tokens.txt")
        val keywords = modelDir.resolve("keywords.txt")
        require(listOf(encoder, decoder, joiner, tokens, keywords).all(File::isFile)) {
            "Incomplete KWS model: ${modelDir.absolutePath}"
        }

        close()
        val config = KeywordSpotterConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = encoder.absolutePath,
                    decoder = decoder.absolutePath,
                    joiner = joiner.absolutePath,
                ),
                tokens = tokens.absolutePath,
                modelType = "zipformer2",
                numThreads = 1,
                provider = "cpu",
            ),
            maxActivePaths = 4,
            keywordsFile = keywords.absolutePath,
            keywordsScore = 2.0f,
            keywordsThreshold = 0.25f,
            numTrailingBlanks = 2,
        )
        spotter = KeywordSpotter(config = config)
        stream = spotter!!.createStream(KEYWORD)
    }

    @Synchronized
    fun accept(samples: ShortArray): Boolean {
        val s = stream ?: return false
        if (samples.isEmpty()) return false
        s.acceptWaveform(FloatArray(samples.size) { samples[it] / 32768.0f }, SAMPLE_RATE)
        val detector = spotter ?: return false
        while (detector.isReady(s)) detector.decode(s)
        val result = detector.getResult(s)
        return result.keyword.equals(KEYWORD, ignoreCase = true)
    }

    @Synchronized
    fun reset() {
        val detector = spotter ?: return
        val s = stream ?: return
        detector.reset(s)
    }

    @Synchronized
    fun close() {
        stream?.release()
        stream = null
        spotter?.release()
        spotter = null
    }
}
