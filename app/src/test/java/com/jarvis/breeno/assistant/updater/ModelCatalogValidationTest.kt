package com.jarvis.breeno.assistant.updater

import com.jarvis.breeno.assistant.voice.PiperTtsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ModelCatalogValidationTest {
    @Test
    fun qwenAndOpenCommunityModelCatalogDescriptorsAreValidAndPinned() {
        assertEquals(7, LocalLlmModelManager.CATALOG.size)
        val ids = LocalLlmModelManager.CATALOG.map { it.id }.toSet()
        assertTrue(LocalLlmModelManager.PACK_DOLPHIN_QWEN_3B in ids)
        assertTrue(LocalLlmModelManager.PACK_HERMES3_LLAMA32_3B in ids)
        assertTrue(LocalLlmModelManager.PACK_QWEN_3B in ids)
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
}
