package com.jarvis.breeno.assistant.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ModelDownloadManagerTest {
    private val payload = "deterministic ASR model fixture".toByteArray()
    private val payloadHash = sha256(payload)

    @Test
    fun streamsAndVerifiesPinnedFileBeforeMarkingReady() {
        val root = tempDirectory()
        try {
            val manager = manager(root) { url -> FakeConnection(url, 200, payload) }
            val progress = mutableListOf<Int>()
            val installed = runBlocking { manager.ensureRussianAsr { progress += it } }

            assertTrue(File(installed, "fixture.onnx").isFile)
            assertEquals(payload.toList(), File(installed, "fixture.onnx").readBytes().toList())
            assertTrue(progress.isNotEmpty())
            assertEquals(100, progress.last())
            assertTrue(manager.isRussianAsrReady())
            assertFalse(File(installed, "fixture.onnx.part").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsUntrustedRedirectBeforeOpeningTarget() {
        val root = tempDirectory()
        var openedUntrustedTarget = false
        try {
            val manager = manager(root) { url ->
                if (url.host == "huggingface.co") {
                    FakeConnection(url, 302, byteArrayOf(), mapOf("Location" to "https://evil.example/model.onnx"))
                } else {
                    openedUntrustedTarget = true
                    FakeConnection(url, 200, payload)
                }
            }
            val failure = runCatching { runBlocking { manager.ensureRussianAsr() } }.exceptionOrNull()
            assertNotNullFailure(failure)
            assertFalse(openedUntrustedTarget)
            assertFalse(File(root, "fixture.onnx.part").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun followsOnlyApprovedRedirectHostsOneHopAtATime() {
        val root = tempDirectory()
        val visited = mutableListOf<String>()
        try {
            val manager = manager(root) { url ->
                visited += url.host
                when (url.host) {
                    "huggingface.co" -> FakeConnection(
                        url, 302, byteArrayOf(),
                        mapOf("Location" to "https://cdn-lfs.huggingface.co/intermediate")
                    )
                    "cdn-lfs.huggingface.co" -> FakeConnection(url, 200, payload)
                    else -> throw AssertionError("Unexpected host: ${url.host}")
                }
            }
            runBlocking { manager.ensureRussianAsr() }
            assertEquals(listOf("huggingface.co", "cdn-lfs.huggingface.co"), visited)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsOversizedResponseBeforeReadingBody() {
        val root = tempDirectory()
        var bodyRequested = false
        try {
            val manager = manager(root, maxBytes = 8L) { url ->
                FakeConnection(url, 200, payload, onInputStream = { bodyRequested = true })
            }
            val failure = runCatching { runBlocking { manager.ensureRussianAsr() } }.exceptionOrNull()
            assertNotNullFailure(failure)
            assertFalse(bodyRequested)
            assertFalse(File(root, "fixture.onnx.part").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun enforcesStreamingByteLimitWhenContentLengthIsUnknown() {
        val root = tempDirectory()
        try {
            val manager = manager(root, maxBytes = 8L) { url ->
                FakeConnection(url, 200, payload, contentLength = -1L)
            }
            val failure = runCatching { runBlocking { manager.ensureRussianAsr() } }.exceptionOrNull()
            assertNotNullFailure(failure)
            assertTrue(
                "Unknown-length body must be rejected by the streaming byte cap",
                failure!!.message.orEmpty().contains("exceeded its size limit")
            )
            assertFalse(File(root, "fixture.onnx.part").exists())
            assertFalse(File(root, "fixture.onnx").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsTruncatedResponseAndCleansTemporaryFile() {
        val root = tempDirectory()
        try {
            val manager = manager(root, contentLength = payload.size.toLong() + 10L) { url ->
                FakeConnection(url, 200, payload, contentLength = payload.size.toLong() + 10L)
            }
            val failure = runCatching { runBlocking { manager.ensureRussianAsr() } }.exceptionOrNull()
            assertNotNullFailure(failure)
            assertFalse(File(root, "fixture.onnx.part").exists())
            assertFalse(File(root, "fixture.onnx").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsDigestMismatchAndCleansTemporaryFile() {
        val root = tempDirectory()
        try {
            val manager = manager(root, expectedHash = "0".repeat(64)) { url ->
                FakeConnection(url, 200, payload)
            }
            val failure = runCatching { runBlocking { manager.ensureRussianAsr() } }.exceptionOrNull()
            assertNotNullFailure(failure)
            assertFalse(File(root, "fixture.onnx.part").exists())
            assertFalse(File(root, "fixture.onnx").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsInsufficientStorageBeforeOpeningBody() {
        val root = tempDirectory()
        var bodyRequested = false
        try {
            val manager = ModelDownloadManager(
                root,
                { url -> FakeConnection(url, 200, payload, onInputStream = { bodyRequested = true }) },
                modelFiles = listOf(AsrModelFile("fixture.onnx", payloadHash, 1024L)),
                baseUrl = "https://huggingface.co/model/",
                availableBytes = { 0L }
            )
            val failure = runCatching { runBlocking { manager.ensureRussianAsr() } }.exceptionOrNull()
            assertNotNullFailure(failure)
            assertFalse(bodyRequested)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsRedirectLoopAtBoundedHopLimit() {
        val root = tempDirectory()
        val visited = mutableListOf<String>()
        try {
            val manager = manager(root) { url ->
                visited += url.toString()
                FakeConnection(url, 302, byteArrayOf(), mapOf("Location" to "https://huggingface.co/model/fixture.onnx"))
            }
            val failure = runCatching { runBlocking { manager.ensureRussianAsr() } }.exceptionOrNull()
            assertNotNullFailure(failure)
            assertEquals(6, visited.size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cancellationDisconnectsActiveConnectionAndCleansPartialFile() = runBlocking {
        val root = tempDirectory()
        val reading = CountDownLatch(1)
        val disconnected = CountDownLatch(1)
        try {
            val manager = manager(root) { url ->
                FakeConnection(
                    url,
                    200,
                    byteArrayOf(),
                    contentLength = -1L,
                    onInputStream = {},
                    inputStreamFactory = {
                        object : InputStream() {
                            override fun read(): Int {
                                reading.countDown()
                                disconnected.await(5, TimeUnit.SECONDS)
                                return -1
                            }

                            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                                reading.countDown()
                                disconnected.await(5, TimeUnit.SECONDS)
                                return -1
                            }
                        }
                    },
                    onDisconnect = { disconnected.countDown() }
                )
            }
            val job = async(Dispatchers.IO) { manager.ensureRussianAsr() }
            assertTrue("Download did not enter blocking read", reading.await(5, TimeUnit.SECONDS))
            job.cancelAndJoin()
            assertTrue("Cancellation did not disconnect the active HTTP connection", disconnected.await(5, TimeUnit.SECONDS))
            assertFalse(File(root, "fixture.onnx.part").exists())
            assertFalse("Cancelled download must never install a final model file", File(root, "fixture.onnx").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun manager(
        root: File,
        expectedHash: String = payloadHash,
        maxBytes: Long = 1024L,
        contentLength: Long = payload.size.toLong(),
        factory: (URL) -> HttpURLConnection
    ) = ModelDownloadManager(
        root,
        factory,
        modelFiles = listOf(AsrModelFile("fixture.onnx", expectedHash, maxBytes)),
        baseUrl = "https://huggingface.co/model/",
        availableBytes = { 1024L * 1024L * 1024L }
    )

    private fun tempDirectory(): File =
        File.createTempFile("asr-download-test", "").apply { delete(); mkdirs() }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun assertNotNullFailure(failure: Throwable?) {
        assertTrue("Expected downloader to reject the response", failure != null)
    }

    private class FakeConnection(
        url: URL,
        private val status: Int,
        private val body: ByteArray,
        private val headers: Map<String, String> = emptyMap(),
        private val contentLength: Long = body.size.toLong(),
        private val onInputStream: () -> Unit = {},
        private val inputStreamFactory: () -> InputStream = { ByteArrayInputStream(body) },
        private val onDisconnect: () -> Unit = {}
    ) : HttpURLConnection(url) {
        override fun connect() = Unit
        override fun disconnect() = onDisconnect()
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = status
        override fun getContentLengthLong(): Long = contentLength
        override fun getHeaderField(name: String?): String? =
            headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
        override fun getInputStream(): InputStream {
            onInputStream()
            return inputStreamFactory()
        }
    }
}
