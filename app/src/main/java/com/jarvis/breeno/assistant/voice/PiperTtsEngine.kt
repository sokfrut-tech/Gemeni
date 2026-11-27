package com.jarvis.breeno.assistant.voice

import com.google.gson.JsonParser
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.util.Locale

data class PiperInferenceParams(
    val noiseScale: Float,
    val noiseScaleW: Float,
)

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

        val inference = parseVoiceInferenceParams(modelDir)

        tts?.release()
        // Construct OfflineTtsConfig directly: getOfflineTtsConfig() in v1.13.8 turns an
        // empty lexicon string into "$modelDir/", which breaks Piper VITS loading.
        // Also override sherpa-onnx's default silenceScale=0.2f so Russian pauses are preserved.
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = model.absolutePath,
                    lexicon = "",
                    tokens = tokens.absolutePath,
                    dataDir = data.absolutePath,
                    noiseScale = inference.noiseScale,
                    noiseScaleW = inference.noiseScaleW,
                    lengthScale = 1.0f,
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu",
            ),
            maxNumSentences = 1,
            silenceScale = 1.0f,
        )
        tts = OfflineTts(assetManager = null, config = config)
        loadedDirPath = modelDir.absolutePath
    }

    @Synchronized
    fun synthesize(text: String, speakerId: Int = 0, speed: Float = 1.0f): FloatArray {
        val prepared = ensureTerminalPunctuation(text)
        require(prepared.isNotBlank()) { "TTS text must not be blank" }
        val engine = tts ?: error("TTS engine is not loaded")
        return engine.generate(text = prepared, sid = speakerId, speed = speed.coerceIn(0.65f, 1.25f)).samples
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
        private val CLOSING_QUOTES_AND_BRACKETS = setOf('»', '”', '’', '"', '\'', ')', ']', '}')
        private val ABBREVIATIONS = setOf(
            "г", "ул", "д", "стр", "т.д", "т.п", "т.е", "т.к", "см", "рис",
            "им", "проф", "акад", "др", "руб", "коп", "млн", "тыс", "кг",
            "мм", "км", "мин", "сек", "ч", "mr", "mrs", "ms", "dr", "prof",
            "vs", "etc", "e.g", "i.e", "fig", "no",
        )
        private val SPACED_ABBR_PREFIX = Regex("""(?:^|\s)(?:и\s+)?т\.\s*[дпек]$""", RegexOption.IGNORE_CASE)
        private val SPACED_ABBR_SUFFIX_NEXT = Regex("""^\s*[дпек]\.""", RegexOption.IGNORE_CASE)
        private val SPACED_ABBR_AND_NEXT = Regex("""^\s*т\.\s*[дпек]\.""", RegexOption.IGNORE_CASE)

        fun parseVoiceInferenceParams(modelDir: File): PiperInferenceParams {
            val fallback = if (modelDir.name.contains("dmitri", ignoreCase = true) ||
                modelDir.listFiles()?.any { it.name.contains("dmitri", ignoreCase = true) } == true
            ) {
                PiperInferenceParams(noiseScale = 0.5f, noiseScaleW = 0.333f)
            } else {
                PiperInferenceParams(noiseScale = 0.667f, noiseScaleW = 0.8f)
            }

            val jsonFile = modelDir.listFiles()?.firstOrNull {
                it.isFile && it.name.endsWith(".onnx.json", ignoreCase = true)
            } ?: return fallback

            return runCatching {
                val root = JsonParser.parseString(jsonFile.readText(Charsets.UTF_8)).asJsonObject
                val inf = root.getAsJsonObject("inference") ?: return fallback
                val noiseScale = inf.get("noise_scale")?.asFloat?.coerceIn(0.1f, 1.5f) ?: fallback.noiseScale
                val noiseScaleW = inf.get("noise_w")?.asFloat?.coerceIn(0.1f, 1.5f) ?: fallback.noiseScaleW
                PiperInferenceParams(noiseScale = noiseScale, noiseScaleW = noiseScaleW)
            }.getOrDefault(fallback)
        }

        internal fun cleanForSpeech(text: String): String = text
            .replace(MARKDOWN_NOISE, " ")
            .replace(Regex("""\[(.*?)]\([^)]*\)"""), "$1")
            .replace(Regex("""[ \t]+"""), " ")
            .trim()

        /**
         * Ensures Piper VITS receives terminal punctuation so it completes the final
         * Russian syllable cleanly instead of clipping trailing consonants/vowels.
         */
        fun ensureTerminalPunctuation(text: String): String {
            val cleaned = cleanForSpeech(text)
            if (cleaned.isEmpty()) return ""
            val last = cleaned.last()
            return if (last == '.' || last == '!' || last == '?' || last == '…') {
                cleaned
            } else {
                "$cleaned."
            }
        }

        /**
         * Strips markdown formatting and splits Russian response text into
         * low-latency chunks suitable for streaming Piper TTS playback without
         * breaking Russian abbreviations, initials, decimals, or overflowing [maxChunkChars].
         */
        fun splitForTts(text: String, maxChunkChars: Int = 160): List<String> {
            val safeLimit = maxChunkChars.coerceAtLeast(1)
            val cleaned = cleanForSpeech(text)
            if (cleaned.isBlank()) return emptyList()

            val rawParts = splitSentencesPreservingRussianSyntax(cleaned)
                .map { it.trim() }
                .filter { it.isNotBlank() }

            val chunks = mutableListOf<String>()
            val current = StringBuilder()

            for (part in rawParts) {
                if (part.length > safeLimit) {
                    if (current.isNotEmpty()) {
                        chunks.add(current.toString().trim())
                        current.clear()
                    }
                    appendBoundedWords(chunks, part, safeLimit)
                    continue
                }

                if (current.isNotEmpty() && current.length + 1 + part.length > safeLimit) {
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

        internal fun appendBoundedWords(target: MutableList<String>, text: String, safeLimit: Int) {
            var buf = StringBuilder()
            for (word in text.split(Regex("\\s+"))) {
                if (word.isEmpty()) continue
                if (word.length > safeLimit) {
                    if (buf.isNotEmpty()) {
                        target.add(buf.toString())
                        buf = StringBuilder()
                    }
                    target.addAll(word.chunked(safeLimit))
                    continue
                }
                if (buf.isNotEmpty() && buf.length + 1 + word.length > safeLimit) {
                    target.add(buf.toString())
                    buf = StringBuilder()
                }
                if (buf.isNotEmpty()) buf.append(' ')
                buf.append(word)
            }
            if (buf.isNotEmpty()) target.add(buf.toString())
        }

        private fun splitSentencesPreservingRussianSyntax(text: String): List<String> {
            val parts = mutableListOf<String>()
            var start = 0
            var index = 0
            while (index < text.length) {
                val end = findTerminalBoundaryAt(text, index, force = true)
                if (end < 0) {
                    index++
                    continue
                }
                val segment = text.substring(start, end).trim()
                if (segment.isNotEmpty()) parts.add(segment)
                var nextStart = end
                while (nextStart < text.length && text[nextStart].isWhitespace()) nextStart++
                start = nextStart
                index = nextStart
            }
            if (start < text.length) {
                val tail = text.substring(start).trim()
                if (tail.isNotEmpty()) parts.add(tail)
            }
            return parts
        }

        internal fun findTerminalBoundaryAt(text: CharSequence, index: Int, force: Boolean): Int {
            val ch = text[index]
            if (ch == '\n' || ch == '\r') {
                var end = index + 1
                while (end < text.length && (text[end] == '\n' || text[end] == '\r')) end++
                return index
            }

            if (ch != '.' && ch != '!' && ch != '?' && ch != '…') return -1

            var end = index + 1
            while (end < text.length && (text[end] == '!' || text[end] == '?' || text[end] == '…')) end++
            while (end < text.length && text[end] in CLOSING_QUOTES_AND_BRACKETS) end++

            if (end == text.length && !force) return -1
            if (end < text.length && !text[end].isWhitespace()) return -1
            if (ch == '.' && isNonTerminalPeriod(text, index, force)) return -1
            return end
        }

        internal fun isNonTerminalPeriod(text: CharSequence, index: Int, force: Boolean = true): Boolean {
            if (index > 0 && index + 1 < text.length &&
                text[index - 1].isDigit() && text[index + 1].isDigit()
            ) return true

            val prefix = text.subSequence(0, index).toString()
            val suffix = text.subSequence(index + 1, text.length).toString()
            val lastWord = prefix.trim()
                .split(Regex("\\s+"))
                .lastOrNull()
                .orEmpty()
                .trimStart('(', '[', '{', '"', '\'', '«', '“', '‘')
            if (lastWord.equals("т", ignoreCase = true)) {
                if (SPACED_ABBR_SUFFIX_NEXT.containsMatchIn(suffix) || (!force && suffix.isBlank())) {
                    return true
                }
            }
            if (lastWord.equals("и", ignoreCase = true)) {
                if (SPACED_ABBR_AND_NEXT.containsMatchIn(suffix) || (!force && suffix.isBlank())) {
                    return true
                }
            }
            if (SPACED_ABBR_PREFIX.containsMatchIn(prefix)) {
                val nextNonWs = suffix.firstOrNull { !it.isWhitespace() }
                if (nextNonWs == null) return !force
                return !nextNonWs.isUpperCase()
            }

            var tokenStart = index - 1
            while (tokenStart >= 0 && !text[tokenStart].isWhitespace()) tokenStart--
            tokenStart++

            val rawToken = text.subSequence(tokenStart, index).toString()
            val cleanedToken = rawToken
                .trimStart('(', '[', '{', '"', '\'', '«', '“', '‘')
                .trimEnd(',', ')', ']', '}', '"', '\'', '»', '”', '’')
                .lowercase(Locale.ROOT)
            if (cleanedToken in ABBREVIATIONS) return true

            // Preserve initials like "А. С. Пушкин" or "И. Петров" inside the sentence.
            val singleLetter = cleanedToken.length == 1 && cleanedToken[0].isLetter()
            if (singleLetter) {
                var nextIdx = index + 1
                while (nextIdx < text.length && text[nextIdx].isWhitespace()) nextIdx++
                if (nextIdx >= text.length) return !force
                if (text[nextIdx].isLetter() && text[nextIdx].isUpperCase()) {
                    return true
                }
            }
            return false
        }
    }
}

/**
 * Incremental Russian sentence chunker for LLM token-delta streams.
 * Delays trailing punctuation/spaced abbreviations until the next fragment or flush
 * so Piper TTS starts speaking from the very first completed sentence.
 */
class RussianStreamingSpeechChunker(
    private val maxChunkChars: Int = 160,
) {
    private val safeLimit = maxChunkChars.coerceAtLeast(20)
    private val pending = StringBuilder()

    fun append(fragment: String): List<String> {
        if (fragment.isNotEmpty()) pending.append(fragment)
        return drain(force = false)
    }

    fun flush(): List<String> = drain(force = true)

    fun reset() {
        pending.clear()
    }

    private fun drain(force: Boolean): List<String> {
        val ready = mutableListOf<String>()
        while (pending.isNotEmpty()) {
            val boundary = findBoundary(force)
            if (boundary >= 0) {
                if (boundary > 0) {
                    emitSpan(boundary, ready)
                } else {
                    while (pending.isNotEmpty() && pending[0].isWhitespace()) {
                        pending.deleteCharAt(0)
                    }
                }
                continue
            }
            if (pending.length > safeLimit) {
                val split = findLengthSplit()
                if (split > 0) {
                    emitSpan(split, ready)
                    continue
                }
            }
            break
        }
        if (force && pending.isNotEmpty()) {
            emitSpan(pending.length, ready)
        }
        return ready
    }

    private fun findBoundary(force: Boolean): Int {
        for (index in pending.indices) {
            val end = PiperTtsEngine.findTerminalBoundaryAt(pending, index, force)
            if (end >= 0) return end
        }
        return -1
    }

    private fun findLengthSplit(): Int {
        var lastWs = -1
        for (index in 0 until minOf(safeLimit, pending.length)) {
            if (pending[index].isWhitespace()) lastWs = index
        }
        return if (lastWs > 0) lastWs else safeLimit
    }

    private fun emitSpan(endExclusive: Int, output: MutableList<String>) {
        val raw = pending.substring(0, endExclusive)
        pending.delete(0, endExclusive)
        while (pending.isNotEmpty() && pending[0].isWhitespace()) {
            pending.deleteCharAt(0)
        }
        val cleaned = PiperTtsEngine.cleanForSpeech(raw)
        if (cleaned.isNotEmpty()) {
            PiperTtsEngine.appendBoundedWords(output, cleaned, safeLimit)
        }
    }
}
