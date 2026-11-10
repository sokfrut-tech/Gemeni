package com.jarvis.breeno.assistant.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JarvisCognitiveKernelTest {
    private class FakeProvider(
        override val id: String,
        override val tier: ReasoningTier,
        private val state: ProviderAvailability,
        private val result: ProviderTurnResult,
    ) : JarvisReasoningProvider {
        override suspend fun availability() = state
        override suspend fun reason(turn: KernelTurn) = result
    }

    @Test
    fun localProviderWinsAndUnknownToolsAreRejected() = runBlocking {
        val local = FakeProvider(
            "local-qwen",
            ReasoningTier.LOCAL_PRIMARY,
            ProviderAvailability.Ready,
            ProviderTurnResult(
                "Готово",
                listOf(
                    ProposedToolCall("open_navigation"),
                    ProposedToolCall("run_shell"),
                ),
                "qwen-test",
            ),
        )
        val cloud = FakeProvider(
            "cloud",
            ReasoningTier.AUTHORIZED_CLOUD,
            ProviderAvailability.Ready,
            ProviderTurnResult("cloud", modelId = "cloud-test"),
        )

        val result = JarvisCognitiveKernel(listOf(cloud, local)).process(
            KernelTurn("t1", "Открой навигатор", allowedToolIds = setOf("open_navigation"))
        )

        assertEquals("local-qwen", result.providerId)
        assertEquals(1, result.acceptedToolProposals.size)
        assertEquals(1, result.rejectedToolProposalCount)
    }

    @Test
    fun cloudProviderIsNotUsedWithoutPerTurnNetworkConsent() = runBlocking {
        val cloud = FakeProvider(
            "cloud",
            ReasoningTier.AUTHORIZED_CLOUD,
            ProviderAvailability.Ready,
            ProviderTurnResult("cloud", modelId = "cloud-test"),
        )
        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                JarvisCognitiveKernel(listOf(cloud)).process(KernelTurn("t2", "Привет"))
            }
        }
        assertEquals(true, error.message!!.contains("No reasoning provider"))
    }

    @Test
    fun modelPackRejectsMissingShardOrInvalidHash() {
        val descriptor = ModelPackDescriptor(
            packId = "qwen-small",
            version = "1",
            runtime = "llama.cpp",
            architecture = "qwen",
            quantization = "Q4_K_M",
            licenseId = "Apache-2.0",
            sourceRevision = "immutable-revision",
            shards = listOf(ModelShard(0, "model-00001-of-00002.gguf", 100, "0".repeat(64)),
                ModelShard(2, "model-00002-of-00002.gguf", 100, "0".repeat(64))),
        )
        assertThrows(IllegalArgumentException::class.java) { descriptor.validate() }
    }
}
