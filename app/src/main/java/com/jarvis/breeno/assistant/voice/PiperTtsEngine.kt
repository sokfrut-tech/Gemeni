package com.jarvis.breeno.assistant.voice

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File

/** Local VITS/Piper-compatible TTS through the actual Sherpa-ONNX Kotlin API. */
class PiperTtsEngine {
    private var tts: OfflineTts? = null
    private var loadedDirPath: String? = null

    @Synchronized
    fun load(modelDir: File) {
        if (tts != null && loadedDirPath == modelDir.absolutePath) return

        val model = modelDir.resolve("model.onnx").takeIf { it.isFile }
            ?: modelDir.listFiles()?.firstOrNull { it.isFile && it.extension.equals("onnx", ignoreCase = true) }
            ?: throw IllegalArgumentException("TTS model (.onnx) is missing: ${modelDir.absolutePath}")
        val tokens = modelDir.resolve("tokens.txt")
        val data = modelDir.resolve("espeak-ng-data")
        require(model.isFile && tokens.isFile) { "TTS model is incomplete: ${modelDir.absolutePath}" }
        require(data.isDirectory) { "espeak-ng-data is missing: ${data.absolutePath}" }

        tts?.release()
        // Construct OfflineTtsConfig directly: getOfflineTtsConfig() in v1.13.8 turns an
        // empty lexicon string into "$modelDir/", which breaks Piper VITS loading.
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = model.absolutePath,
                    lexicon = "",
                    tokens = tokens.absolutePath,
                    dataDir = data.absolutePath,
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu",
            ),
            maxNumSentences = 1,
        )
        tts = OfflineTts(assetManager = null, config = config)
        loadedDirPath = modelDir.absolutePath
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
        loadedDirPath = null
    }

    companion object {
        private val MARKDOWN_NOISE = Regex("""[*_`#~>]+""")
        private val SENTENCE_SPLIT = Regex("""(?<=[.!?…\n])\s+""")

        /**
         * Strips markdown formatting and splits Russian response text into
         * low-latency chunks suitable for streaming Piper TTS playback.
         */
        fun splitForTts(text: String, maxChunkChars: Int = 220): List<String> {
            val cleaned = text
                .replace(MARKDOWN_NOISE, " ")
                .replace(Regex("""\[(.*?)]\([^)]*\)"""), "$1")
                .replace(Regex("""[ \t]+"""), " ")
                .trim()
            if (cleaned.isBlank()) return emptyList()

            val rawParts = cleaned.split(SENTENCE_SPLIT)
                .map { it.trim() }
                .filter { it.isNotBlank() }

            val chunks = mutableListOf<String>()
            val current = StringBuilder()

            for (part in rawParts) {
                if (part.length > maxChunkChars) {
                    if (current.isNotEmpty()) {
                        chunks.add(current.toString().trim())
                        current.clear()
                    }
                    // Split overly long sentence by words so synthesis memory stays bounded.
                    var buf = StringBuilder()
                    for (word in part.split(Regex("\\s+"))) {
                        if (buf.isNotEmpty() && buf.length + 1 + word.length > maxChunkChars) {
                            chunks.add(buf.toString())
                            buf = StringBuilder()
                        }
                        if (buf.isNotEmpty()) buf.append(' ')
                        buf.append(word)
                    }
                    if (buf.isNotEmpty()) chunks.add(buf.toString())
                    continue
                }

                if (current.isNotEmpty() && current.length + 1 + part.length > maxChunkChars) {
                    chunks.add(current.toString().trim())
                    current.clear()
                }
                if (current.isNotEmpty()) current.append(' ')
                current.append(part)
            }
            if (current.isNotEmpty()) {
                chunks.add(current.toString().trim())
            }
            return chunks
        }
    }
}
