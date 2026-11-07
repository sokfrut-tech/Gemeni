package com.jarvis.breeno.assistant.core.ai

import com.jarvis.breeno.assistant.core.JarvisTextGenerationRuntime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppJarvisModelProviderTest {
    private class FakeRuntime(
        override val isModelLoaded: Boolean,
        private val output: String
    ) : JarvisTextGenerationRuntime {
        var calls = 0
        var lastPrompt = ""

        override fun generateForPlanning(prompt: String, maxTokens: Int): String {
            calls += 1
            lastPrompt = prompt
            assertTrue(maxTokens in 1..512)
            return output
        }
    }

    @Test
    fun doesNotInvokeRuntimeWhenModelIsNotLoaded() = runBlocking {
        val runtime = FakeRuntime(false, """{"tool":"tell_time","arguments":{}}""")
        val result = LlamaCppJarvisModelProvider(runtime)
            .propose(JarvisModelRequest("Который час?"))

        assertTrue(result is JarvisModelResult.Failure)
        assertEquals(0, runtime.calls)
    }

    @Test
    fun acceptsOnlyStrictlyValidatedToolProposal() = runBlocking {
        val runtime = FakeRuntime(
            true,
            """{"tool":"open_dialer","arguments":{"number":"+49 123456"}}"""
        )
        val result = LlamaCppJarvisModelProvider(runtime)
            .propose(JarvisModelRequest("Позвони +49 123456"))

        assertTrue(result is JarvisModelResult.Proposal)
        val proposal = result as JarvisModelResult.Proposal
        assertEquals(
            """{"tool":"open_dialer","arguments":{"number":"+49 123456"}}""",
            proposal.json
        )
        assertEquals("llama.cpp-qwen-local", proposal.source)
    }

    @Test
    fun rejectsUntrustedOrMalformedModelOutput() = runBlocking {
        val runtime = FakeRuntime(
            true,
            """{"tool":"launch_shell","arguments":{"command":"rm -rf /"}}"""
        )
        val result = LlamaCppJarvisModelProvider(runtime)
            .propose(JarvisModelRequest("Выполни системную команду"))

        assertTrue(result is JarvisModelResult.NoAction)
    }

    @Test
    fun emptyAndOversizedRequestsDoNotReachModel() = runBlocking {
        val runtime = FakeRuntime(true, """{"tool":"tell_time","arguments":{}}""")
        val provider = LlamaCppJarvisModelProvider(runtime)

        assertTrue(provider.propose(JarvisModelRequest("   ")) is JarvisModelResult.NoAction)
        assertTrue(
            provider.propose(JarvisModelRequest("x".repeat(8001))) is JarvisModelResult.NoAction
        )
        assertEquals(0, runtime.calls)
    }
}
