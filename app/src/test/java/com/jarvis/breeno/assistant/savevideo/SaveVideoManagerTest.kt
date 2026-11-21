package com.jarvis.breeno.assistant.savevideo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SaveVideoManagerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun urlParserDetectsPermittedSourcesAndRejectsInvalidUrls() {
        assertNull(SaveVideoUrlParser.normalizeAndValidate(""))
        assertNull(SaveVideoUrlParser.normalizeAndValidate("ftp://example.com/video.mp4"))
        assertNull(SaveVideoUrlParser.normalizeAndValidate("not a url"))

        assertEquals(SaveVideoSource.YOUTUBE, SaveVideoUrlParser.detectSource("https://youtu.be/abc123"))
        assertEquals(SaveVideoSource.INSTAGRAM, SaveVideoUrlParser.detectSource("https://www.instagram.com/reel/xyz/"))
        assertEquals(SaveVideoSource.TIKTOK, SaveVideoUrlParser.detectSource("https://www.tiktok.com/@user/video/123"))
        assertEquals(SaveVideoSource.DIRECT_MEDIA, SaveVideoUrlParser.detectSource("https://cdn.example.com/clip.mp4"))
    }

    @Test
    fun htmlTitleExtractionPrefersOgTitleAndDecodesEntities() {
        val html = """
            <html><head>
              <title>Fallback Title</title>
              <meta property="og:title" content="Jarvis &amp; SaveVideo Demo" />
            </head></html>
        """.trimIndent()
        assertEquals("Jarvis & SaveVideo Demo", SaveVideoUrlParser.extractHtmlTitle(html))
    }

    @Test
    fun queueEnqueueCancelDeleteAndHistoryPersistenceWork() {
        val downloads = tempFolder.newFolder("downloads")
        val history = File(tempFolder.root, "history.json")
        val events = mutableListOf<String>()

        val manager = SaveVideoManager(
            downloadsDir = downloads,
            historyFile = history,
            onEvent = { title, _, _ -> events.add(title) },
        )

        assertTrue(manager.enqueue("invalid", startImmediately = false).isFailure)

        val enqueued = manager.enqueue(
            rawUrl = "https://cdn.example.com/demo.mp4",
            quality = SaveVideoQuality.HD_1080,
            startImmediately = false,
        ).getOrNull()
        assertNotNull(enqueued)
        assertEquals(1, manager.tasks.value.size)
        assertEquals(SaveVideoStatus.QUEUED, manager.tasks.value.first().status)
        assertTrue(history.isFile)

        manager.cancelTask(enqueued!!.id)
        assertEquals(SaveVideoStatus.CANCELLED, manager.tasks.value.first().status)
        manager.close()

        // Reload from disk to verify persistence round-trip
        val reloaded = SaveVideoManager(downloadsDir = downloads, historyFile = history)
        assertEquals(1, reloaded.tasks.value.size)
        assertEquals(enqueued.id, reloaded.tasks.value.first().id)

        reloaded.deleteTask(enqueued.id)
        assertTrue(reloaded.tasks.value.isEmpty())
        reloaded.close()
    }
}
