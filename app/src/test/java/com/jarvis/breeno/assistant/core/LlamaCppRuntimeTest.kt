package com.jarvis.breeno.assistant.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppRuntimeTest {
    @Test
    fun sha256MatchesKnownDigest() {
        val file = File.createTempFile("jarvis-model-test", ".bin")
        try {
            file.writeText("abc")
            assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                LlamaCppRuntime.sha256(file)
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun pinnedModelDigestHasExpectedShape() {
        assertTrue(LlamaCppRuntime.QWEN_08B_Q4_0_SHA256.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun limitsAreBoundedForMobileRuntime() {
        assertTrue(LlamaCppRuntime.DEFAULT_CONTEXT_TOKENS in 512..8192)
        assertTrue(LlamaCppRuntime.DEFAULT_MAX_TOKENS in 1..LlamaCppRuntime.MAX_MAX_TOKENS)
        assertTrue(LlamaCppRuntime.MAX_PROMPT_CHARS <= 12000)
        assertFalse(LlamaCppRuntime.MAX_MAX_TOKENS > 512)
    }
}
