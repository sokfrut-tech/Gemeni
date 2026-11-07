package com.jarvis.breeno.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QwenModelInstallerTest {
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
