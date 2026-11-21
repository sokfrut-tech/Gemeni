package com.jarvis.breeno.assistant.updater

import com.jarvis.breeno.assistant.voice.PiperTtsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogValidationTest {
    @Test
    fun qwenModelCatalogDescriptorsAreValidAndPinned() {
        assertEquals(3, LocalLlmModelManager.CATALOG.size)
        LocalLlmModelManager.CATALOG.forEach { pack ->
            pack.descriptor.validate()
            assertTrue(pack.downloadUrl.contains(pack.descriptor.sourceRevision))
            assertTrue(pack.sizeBytes > 100_000_000L)
            assertEquals(64, pack.sha256.length)
        }
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
