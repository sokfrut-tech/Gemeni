package com.jarvis.breeno.assistant.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

class SileroVadModelManagerTest {
    private val payload = "silero-vad-fixture".toByteArray()
    private val hash = sha256(payload)

    @Test
    fun requiresExplicitConsentBeforeAnyNetworkRequest() {
        val root = tempDirectory()
        var connections = 0
        try {
            val manager = manager(root) { url ->
                connections++
                FakeConnection(url, 200, payload)
            }
            val failure = runCatching {
                runBlocking { manager.ensure(userConsent = false) }
            }.exceptionOrNull()

            assertTrue(failure != null)
            assertEquals(0, connections)
            assertFalse(manager.modelFile().exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun installsOnlyVerifiedModelAndReportsCompletion() {
        val root = tempDirectory()
        try {
            val manager = manager(root) { url -> FakeConnection(url, 200, payload) }
            val progress = mutableListOf<Int>()
            val installed = runBlocking { manager.ensure(userConsent = true) { progress += it } }

            assertEquals(payload.toList(), installed.readBytes().toList())
            assertTrue(manager.isReady())
            assertEquals(100, progress.last())
            assertFalse(File(root, "vad/silero_vad.onnx.part").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun blocksUntrustedRedirectBeforeOpeningTarget() {
        val root = tempDirectory()
        var openedUntrusted = false
        try {
            val manager = manager(root) { url ->
                if (url.host == "github.com") {
                    FakeConnection(url, 302, byteArrayOf(), mapOf("Location" to "https://evil.example/model.onnx"))
                } else {
                    openedUntrusted = true
                    FakeConnection(url, 200, payload)
                }
            }
            val failure = runCatching {
                runBlocking { manager.ensure(userConsent = true) }
            }.exceptionOrNull()

            assertTrue(failure != null)
            assertFalse(openedUntrusted)
            assertFalse(manager.modelFile().exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun enforcesStreamingSizeLimitForUnknownLengthResponse() {
        val root = tempDirectory()
        try {
            val manager = manager(root, maxBytes = 8L) { url ->
                FakeConnection(url, 200, payload, contentLength = -1L)
            }
            val failure = runCatching {
                runBlocking { manager.ensure(userConsent = true) }
            }.exceptionOrNull()

            assertTrue(failure?.message.orEmpty().contains("size limit"))
            assertFalse(manager.modelFile().exists())
            assertFalse(File(root, "vad/silero_vad.onnx.part").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsDigestMismatchAndCleansPartialFile() {
        val root = tempDirectory()
        try {
            val manager = manager(root, expectedHash = "0".repeat(64)) { url ->
                FakeConnection(url, 200, payload)
            }
            val failure = runCatching {
                runBlocking { manager.ensure(userConsent = true) }
            }.exceptionOrNull()

            assertTrue(failure?.message.orEmpty().contains("SHA-256"))
            assertFalse(manager.modelFile().exists())
            assertFalse(File(root, "vad/silero_vad.onnx.part").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cancellationDisconnectsReadAndNeverInstallsModel() = runBlocking {
        val root = tempDirectory()
        val reading = CountDownLatch(1)
        val disconnected = CountDownLatch(1)
        try {
            val manager = manager(root) { url ->
                FakeConnection(
                    url, 200, byteArrayOf(), contentLength = -1L,
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
            val job = async(Dispatchers.IO) { manager.ensure(userConsent = true) }
            assertTrue(reading.await(5, TimeUnit.SECONDS))
            job.cancelAndJoin()

            assertTrue(disconnected.await(5, TimeUnit.SECONDS))
            assertFalse(manager.modelFile().exists())
            assertFalse(File(root, "vad/silero_vad.onnx.part").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun manager(
        root: File,
        expectedHash: String = hash,
        maxBytes: Long = 1024L,
        availableBytes: Long = 1024L * 1024L * 1024L,
        factory: (URL) -> HttpURLConnection
    ) = SileroVadModelManager(
        target = File(root, "vad/silero_vad.onnx"),
        connectionFactory = factory,
        modelUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
        expectedSha256 = expectedHash,
        maxBytes = maxBytes,
        availableBytes = { availableBytes }
    )

    private fun tempDirectory(): File =
        File.createTempFile("silero-vad-test", "").apply { delete(); mkdirs() }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private class FakeConnection(
        url: URL,
        private val status: Int,
        private val body: ByteArray,
        private val headers: Map<String, String> = emptyMap(),
        private val contentLength: Long = body.size.toLong(),
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
        override fun getInputStream(): InputStream = inputStreamFactory()
    }
}
