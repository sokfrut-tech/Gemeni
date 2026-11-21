package com.jarvis.breeno.assistant.updater

import com.jarvis.breeno.assistant.voice.PiperTtsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertEquals(2, PiperTtsModelDownloadManager.VOICES.size)
        PiperTtsModelDownloadManager.VOICES.forEach { voice ->
            assertTrue(voice.downloadUrl.startsWith("https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"))
            assertTrue(voice.archiveSizeBytes > 10_000_000L)
            assertEquals(64, voice.archiveSha256.length)
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
