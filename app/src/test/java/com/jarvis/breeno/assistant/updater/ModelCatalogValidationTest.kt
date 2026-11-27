package com.jarvis.breeno.assistant.updater

import com.jarvis.breeno.assistant.voice.PiperTtsEngine
import com.jarvis.breeno.assistant.voice.RussianStreamingSpeechChunker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ModelCatalogValidationTest {
    @Test
    fun qwenAndOpenCommunityModelCatalogDescriptorsAreValidAndPinned() {
        assertEquals(11, LocalLlmModelManager.CATALOG.size)
        val ids = LocalLlmModelManager.CATALOG.map { it.id }.toSet()
        assertTrue(LocalLlmModelManager.PACK_DOLPHIN_QWEN_3B in ids)
        assertTrue(LocalLlmModelManager.PACK_HERMES3_LLAMA32_3B in ids)
        assertTrue(LocalLlmModelManager.PACK_STHENO_8B_Q3KM in ids)
        assertTrue(LocalLlmModelManager.PACK_STHENO_8B_Q4KM in ids)
        assertTrue(LocalLlmModelManager.PACK_QWEN_3B in ids)
        assertTrue(LocalLlmModelManager.PACK_QWEN3_06B in ids)
        assertTrue(LocalLlmModelManager.PACK_QWEN3_4B in ids)
        LocalLlmModelManager.CATALOG.forEach { pack ->
            pack.descriptor.validate()
            assertTrue(pack.downloadUrl.contains(pack.descriptor.sourceRevision))
            assertTrue(pack.sizeBytes > 100_000_000L)
            assertEquals(64, pack.sha256.length)
        }
    }

    @Test
    fun validatesGgufMagicBytes() {
        assertTrue(LocalLlmModelManager.isValidGgufMagic(byteArrayOf(0x47, 0x47, 0x55, 0x46, 0x03, 0x00)))
        assertFalse(LocalLlmModelManager.isValidGgufMagic(byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
        assertFalse(LocalLlmModelManager.isValidGgufMagic(byteArrayOf(0x47, 0x47, 0x55)))
        assertFalse(LocalLlmModelManager.isValidGgufMagic(ByteArray(0)))
    }

    @Test
    fun sanitizesCustomGgufFilenamesSafely() {
        assertEquals(
            "Dolphin-3.0_Q4_K_M.gguf",
            LocalLlmModelManager.sanitizeCustomFileName("../../downloads/Dolphin-3.0 Q4_K_M.gguf"),
        )
        assertEquals(
            "hermes_custom.gguf",
            LocalLlmModelManager.sanitizeCustomFileName("C:\\Temp\\hermes_custom"),
        )
        assertEquals("custom_model.gguf", LocalLlmModelManager.sanitizeCustomFileName("///..."))
    }

    @Test
    fun piperVoiceCatalogEntriesArePinnedWithSha256() {
        assertEquals(4, PiperTtsModelDownloadManager.VOICES.size)
        val ids = PiperTtsModelDownloadManager.VOICES.map { it.id }.toSet()
        assertTrue(PiperTtsModelDownloadManager.VOICE_DMITRI_HD in ids)
        assertTrue(PiperTtsModelDownloadManager.VOICE_RUSLAN_HD in ids)
        assertTrue(PiperTtsModelDownloadManager.VOICE_DMITRI_INT8 in ids)
        assertTrue(PiperTtsModelDownloadManager.VOICE_RUSLAN_INT8 in ids)
        PiperTtsModelDownloadManager.VOICES.forEach { voice ->
            assertTrue(voice.downloadUrl.startsWith("https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"))
            assertTrue(voice.archiveSizeBytes > 10_000_000L)
            assertEquals(64, voice.archiveSha256.length)
        }
    }

    @Test
    fun parsesPiperInferenceParamsWithDmitriFallback() {
        val dir = Files.createTempDirectory("piper-dmitri-test").toFile()
        try {
            File(dir, "ru_RU-dmitri-medium.onnx.json").writeText(
                """{"inference":{"noise_scale":0.5,"length_scale":1.0,"noise_w":0.333}}""",
                Charsets.UTF_8,
            )
            val parsed = PiperTtsEngine.parseVoiceInferenceParams(dir)
            assertEquals(0.5f, parsed.noiseScale, 0.0001f)
            assertEquals(0.333f, parsed.noiseScaleW, 0.0001f)
        } finally {
            dir.deleteRecursively()
        }

        val emptyOtherDir = Files.createTempDirectory("piper-ruslan-empty").toFile()
        try {
            val defaults = PiperTtsEngine.parseVoiceInferenceParams(emptyOtherDir)
            assertEquals(0.667f, defaults.noiseScale, 0.0001f)
            assertEquals(0.8f, defaults.noiseScaleW, 0.0001f)
        } finally {
            emptyOtherDir.deleteRecursively()
        }
    }

    @Test
    fun splitForTtsStripsMarkdownAndChunksRussianText() {
        val raw = "**Привет!** Это `тестовый` ответ. # Важная мысль: " +
            "Джарвис должен говорить чётко и без задержек! Третье предложение завершает проверку."
        val chunks = PiperTtsEngine.splitForTts(raw, maxChunkChars = 90)
        assertTrue(chunks.size >= 2)
        assertTrue(chunks.none { it.contains('*') || it.contains('`') || it.contains('#') })
        assertTrue(chunks.all { it.length <= 90 })
    }

    @Test
    fun splitForTtsPreservesRussianAbbreviationsInitialsDecimalsAndBoundsLongTokens() {
        val abbr = "Встреча в «г. Москва» (рис. 2): книги, ручки и т. д. ждут А. С. Пушкина по 3.14 балла."
        val abbrChunks = PiperTtsEngine.splitForTts(abbr, maxChunkChars = 160)
        assertEquals(1, abbrChunks.size)
        assertEquals(abbr, abbrChunks.single())

        val twoSentences = "Мы купили блокноты, ручки и т. д. Завтра продолжим тестирование."
        val split = PiperTtsEngine.splitForTts(twoSentences, maxChunkChars = 48)
        assertEquals(listOf("Мы купили блокноты, ручки и т. д.", "Завтра продолжим тестирование."), split)

        val longUrl = "Откройте https://huggingface.co/ggml-org/Qwen3-0.6B-GGUF/resolve/afe8ad3577aa22500b3cd1c880f153b3b0e1cae8/Qwen3-0.6B-Q4_0.gguf сейчас."
        val bounded = PiperTtsEngine.splitForTts(longUrl, maxChunkChars = 40)
        assertTrue(bounded.isNotEmpty())
        assertTrue(bounded.all { it.length <= 40 })
    }

    @Test
    fun streamingSpeechChunkerHandlesRussianFragmentsAbbreviationsAndBargeInReset() {
        val chunker = RussianStreamingSpeechChunker(maxChunkChars = 160)
        val emitted = mutableListOf<String>()

        // Hold across token deltas containing spaced abbreviations and decimals
        assertTrue(chunker.append("Взяли всё, и т. ").isEmpty())
        emitted += chunker.append("д. Завтра ")
        assertEquals(listOf("Взяли всё, и т. д."), emitted)

        assertTrue(chunker.append("продолжим в (г. ").isEmpty())
        assertTrue(chunker.append("Москва) по **3.").isEmpty())
        emitted += chunker.append("14** балла! А. ")
        assertEquals(
            listOf("Взяли всё, и т. д.", "Завтра продолжим в (г. Москва) по 3.14 балла!"),
            emitted,
        )

        assertTrue(chunker.append("С. ").isEmpty())
        assertTrue(chunker.append("Пушкин писал.").isEmpty())
        emitted += chunker.flush()
        assertEquals(
            listOf(
                "Взяли всё, и т. д.",
                "Завтра продолжим в (г. Москва) по 3.14 балла!",
                "А. С. Пушкин писал.",
            ),
            emitted,
        )

        // Barge-in reset discards unfinished stale buffer
        chunker.append("Старый оборванный текст без")
        chunker.reset()
        assertEquals(listOf("Новый чистый ответ."), chunker.append("Новый чистый ответ.") + chunker.flush())

        // Long token enforcement
        val tight = RussianStreamingSpeechChunker(maxChunkChars = 40)
        val urlOut = tight.append("Откройте https://huggingface.co/Qwen/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q4_K_M.gguf пожалуйста!") + tight.flush()
        assertTrue(urlOut.size >= 2)
        assertTrue(urlOut.all { it.length <= 40 })
    }

    @Test
    fun clampsGenerationSettingsWithinSafeBounds() {
        val raw = LlmGenerationSettings(
            temperature = 4.2f,
            topP = 0.05f,
            maxTokens = 8192,
            contextLength = 128,
            cpuThreads = 32,
            customSystemPrompt = "  " + "x".repeat(2500) + "  ",
        )
        val clamped = LocalLlmModelManager.clampGenerationSettings(raw)
        assertEquals(1.40f, clamped.temperature, 0.001f)
        assertEquals(0.50f, clamped.topP, 0.001f)
        assertEquals(1024, clamped.maxTokens)
        assertEquals(512, clamped.contextLength)
        assertEquals(6, clamped.cpuThreads)
        assertEquals(2000, clamped.customSystemPrompt.length)
    }

    @Test
    fun validatesArchiveMagicReadsChunkedHeaderAndFormatsDiagnostics() {
        assertTrue(LocalLlmModelManager.isZipMagic(byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
        assertFalse(LocalLlmModelManager.isZipMagic(byteArrayOf(0x47, 0x47, 0x55, 0x46)))
        assertTrue(LocalLlmModelManager.isGzipMagic(byteArrayOf(0x1F, 0x8B.toByte(), 0x08)))
        assertFalse(LocalLlmModelManager.isGzipMagic(byteArrayOf(0x1F)))

        val payload = byteArrayOf(0x47, 0x47, 0x55, 0x46, 0x03, 0x00, 0x00, 0x00)
        val oneByteAtATime = object : InputStream() {
            private var pos = 0
            override fun read(): Int = if (pos < payload.size) payload[pos++].toInt() and 0xFF else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (pos >= payload.size) return -1
                b[off] = payload[pos++]
                return 1
            }
        }
        val dst = ByteArray(8)
        val readCount = LocalLlmModelManager.readHeaderAtLeast(oneByteAtATime, dst, 8)
        assertEquals(8, readCount)
        assertTrue(LocalLlmModelManager.isValidGgufMagic(dst))

        val htmlDiag = LocalLlmModelManager.formatHeaderDiagnostic("broken.gguf", 4096L, "<!DOCTYP".toByteArray())
        assertTrue(htmlDiag.contains("4 КБ"))
        assertTrue(htmlDiag.contains("HTML/JSON"))

        val emptyDiag = LocalLlmModelManager.formatHeaderDiagnostic("empty.gguf", 0L, ByteArray(0))
        assertTrue(emptyDiag.contains("0 Б"))
        assertTrue(emptyDiag.contains("<пусто>"))
    }

    @Test
    fun extractsGgufFromZipArchiveAndVerifiesDigest() {
        val fakeGguf = byteArrayOf(0x47, 0x47, 0x55, 0x46, 0x03, 0x00, 0x00, 0x00, 0x11, 0x22, 0x33, 0x44)
        val zipBytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("README.txt"))
                zos.write("not a model".toByteArray())
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("nested/jarvis-ru-0.5b-soup-q8_0.gguf"))
                zos.write(fakeGguf)
                zos.closeEntry()
            }
            baos.toByteArray()
        }

        val tmpDir = Files.createTempDirectory("gguf-zip-test").toFile()
        try {
            val outFile = File(tmpDir, "extracted.gguf")
            val digest = MessageDigest.getInstance("SHA-256")
            val extracted = LocalLlmModelManager.copyZipGgufEntry(
                rawStream = ByteArrayInputStream(zipBytes),
                destTmpFile = outFile,
                digest = digest,
            )
            assertEquals("nested/jarvis-ru-0.5b-soup-q8_0.gguf", extracted.entryName)
            assertEquals(fakeGguf.size.toLong(), extracted.copiedBytes)
            assertEquals(fakeGguf.toList(), outFile.readBytes().toList())
            val expectedSha = MessageDigest.getInstance("SHA-256").digest(fakeGguf)
            assertEquals(expectedSha.toList(), digest.digest().toList())
        } finally {
            tmpDir.deleteRecursively()
        }
    }
}
