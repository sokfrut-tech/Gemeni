package com.jarvis.breeno.assistant.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisCognitiveFlowTest {
    private class StubLocalProvider(
        private val available: Boolean,
        private val reply: String = "Локальный ответ Джарвиса",
    ) : ManagedLocalReasoningProvider {
        override val id = "stub-local"
        override val tier = ReasoningTier.LOCAL_PRIMARY
        var calls = 0

        override suspend fun availability(): ProviderAvailability =
            if (available) ProviderAvailability.Ready else ProviderAvailability.Unavailable("Модель не скачана")

        override suspend fun reason(turn: KernelTurn): ProviderTurnResult {
            calls++
            return ProviderTurnResult(answer = reply, modelId = "qwen2.5-0.5b")
        }

        override suspend fun unload() = Unit
    }

    private class StubCloudProvider(
        private val available: Boolean,
        private val reply: String = "Облачный ответ",
    ) : JarvisReasoningProvider {
        override val id = "stub-cloud"
        override val tier = ReasoningTier.AUTHORIZED_CLOUD
        var calls = 0

        override suspend fun availability(): ProviderAvailability =
            if (available) ProviderAvailability.Ready else ProviderAvailability.Unavailable("Облако отключено")

        override suspend fun reason(turn: KernelTurn): ProviderTurnResult {
            calls++
            return ProviderTurnResult(answer = reply, modelId = "gemini-2.0-flash")
        }
    }

    @Test
    fun prefersLocalResourceGuardedProviderOverCloud() = runBlocking {
        val local = StubLocalProvider(available = true)
        val cloud = StubCloudProvider(available = true)
        val guarded = ResourceGuardedLocalProvider(local, RuntimeSignalsSource {
            RuntimeSignals(availableMemoryMb = 4096, thermalHeadroom = 0.1f, thermalStatus = 0, batterySaver = false, charging = true, foreground = true)
        })
        val kernel = JarvisCognitiveKernel(listOf(guarded, cloud))

        val res = kernel.process(KernelTurn("t1", "Привет, Джарвис!", networkAllowed = true))
        assertEquals("Локальный ответ Джарвиса", res.answer)
        assertEquals(1, local.calls)
        assertEquals(0, cloud.calls)
    }

    @Test
    fun fallsBackToCloudOnlyWhenNetworkAllowedAndLocalUnavailable() = runBlocking {
        val local = StubLocalProvider(available = false)
        val cloud = StubCloudProvider(available = true)
        val guarded = ResourceGuardedLocalProvider(local, RuntimeSignalsSource {
            RuntimeSignals(4096, 0.1f, 0, false, true, true)
        })
        val kernel = JarvisCognitiveKernel(listOf(guarded, cloud))

        // Without networkAllowed -> throws clear unavailable error
        val denied = runCatching {
            kernel.process(KernelTurn("t2", "Вопрос", networkAllowed = false))
        }
        assertTrue(denied.isFailure)
        assertEquals(0, cloud.calls)

        // With networkAllowed -> uses cloud
        val allowed = kernel.process(KernelTurn("t3", "Вопрос", networkAllowed = true))
        assertEquals("Облачный ответ", allowed.answer)
        assertEquals(1, cloud.calls)
    }
}
