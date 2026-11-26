package com.jarvis.breeno.assistant.documents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisDocumentChunkerTest {
    @Test
    fun splitsLongDocumentIntoOverlappingChunks() {
        val text = (1..40).joinToString("\n") { "Абзац номер $it: локальный поиск Jarvis Ai работает полностью автономно без сети." }
        val chunks = JarvisDocumentStore.chunkDocumentText(text, chunkSizeChars = 300, overlapChars = 60)
        assertTrue(chunks.size >= 4)
        assertTrue(chunks.all { it.isNotBlank() && it.length <= 360 })
    }

    @Test
    fun ranksRussianChunksWithYoNormalization() {
        val chunks = listOf(
            "manual.txt" to "Для запуска локальной модели используйте Dolphin 3.0 или Qwen 2.5.",
            "recipes.txt" to "Ежик идет по лесу и собирает теплый мед летом.",
            "guide.txt" to "Включение ночного режима экрана уменьшает нагрузку на глаза.",
        )
        val matches = JarvisDocumentStore.scoreChunks("ёжик мёд", chunks, limit = 2)
        assertEquals(1, matches.size)
        assertEquals("recipes.txt", matches.first().documentName)
    }

    @Test
    fun extractsTextFromSimpleUncompressedPdfStream() {
        val fakePdf = "%PDF-1.4\nBT (Jarvis Offline Document) Tj ET\n%%EOF".toByteArray(Charsets.ISO_8859_1)
        val extracted = JarvisDocumentStore.extractPrintablePdfText(fakePdf)
        assertTrue(extracted.contains("Jarvis Offline Document"))
    }
}
