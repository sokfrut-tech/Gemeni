package com.jarvis.breeno.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Job
import org.junit.Test

class QwenModelInstallerTest {
    @Test
    fun cancellingDownloadOwnerDisconnectsActiveHttpConnection() {
        val job = Job()
        val connection = DisconnectTrackingConnection()
        val activeConnection = AtomicReference<HttpURLConnection?>(connection)
        val handle = disconnectActiveConnectionOnCancellation(job, activeConnection)

        job.cancel()

        assertTrue("Cancellation must disconnect the active HTTP request", connection.disconnected)
        assertEquals(null, activeConnection.get())
        handle?.dispose()
    }

    @Test
    fun successfulDownloadCompletionDoesNotDisconnectConnection() {
        val job = Job()
        val connection = DisconnectTrackingConnection()
        val activeConnection = AtomicReference<HttpURLConnection?>(connection)
        val handle = disconnectActiveConnectionOnCancellation(job, activeConnection)

        assertTrue(job.complete())

        assertFalse("A normally completed download must not be treated as cancelled", connection.disconnected)
        assertEquals(connection, activeConnection.get())
        handle?.dispose()
        connection.disconnect()
    }

    private class DisconnectTrackingConnection : HttpURLConnection(URL("https://localhost/test")) {
        var disconnected = false
            private set

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy(): Boolean = false
        override fun connect() = Unit
    }

    @Test
    fun installerDigestMatchesRuntimeDigest() {
        assertEquals(LlamaCppRuntime.QWEN_08B_Q4_0_SHA256, QwenModelInstaller.MODEL_SHA256)
    }

    @Test
    fun pinnedModelSizeAndStorageHeadroomAreConsistent() {
        assertEquals(563036064L, QwenModelInstaller.MODEL_SIZE_BYTES)
        assertTrue(QwenModelInstaller.MINIMUM_FREE_SPACE_BYTES > QwenModelInstaller.MODEL_SIZE_BYTES)
    }

    @Test
    fun progressIsBoundedAndHandlesUnknownTotal() {
        assertEquals(0.5f, QwenModelInstaller.Progress(50, 100).fraction)
        assertEquals(1f, QwenModelInstaller.Progress(150, 100).fraction)
        assertEquals(0f, QwenModelInstaller.Progress(50, 0).fraction)
    }
}
