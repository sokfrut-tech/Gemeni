package com.jarvis.breeno.assistant.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ResourceGuardedLocalProviderTest {
    private class FakeProvider : ManagedLocalReasoningProvider {
        override val id = "fake-local"
        override val tier = ReasoningTier.LOCAL_PRIMARY
        var availabilityCalls = 0
        var reasonCalls = 0
        var unloadCalls = 0
        var receivedTurn: KernelTurn? = null
        override suspend fun availability(): ProviderAvailability {
            availabilityCalls++
            return ProviderAvailability.Ready
        }
        override suspend fun reason(turn: KernelTurn): ProviderTurnResult {
            reasonCalls++
            receivedTurn = turn
            return ProviderTurnResult("ok", modelId = "fake")
        }
        override suspend fun unload() { unloadCalls++ }
    }

    @Test fun criticalPolicyBlocksProviderAndUnloads() = runBlocking {
        val fake = FakeProvider()
        val signals = RuntimeSignals(4000, 0.99f, 5, false, false, true)
        val guarded = ResourceGuardedLocalProvider(fake, RuntimeSignalsSource { signals })
        assertTrue(guarded.availability() is ProviderAvailability.Unavailable)
        assertEquals(0, fake.availabilityCalls)
        assertEquals(1, fake.unloadCalls)
        assertEquals(0, fake.reasonCalls)
    }

    @Test fun allowedPolicyCapsOutputBudget() = runBlocking {
        val fake = FakeProvider()
        // 2200 MB -> maxContextTokens = 1024 -> output budget capped to 512
        val signals = RuntimeSignals(2200, 0.1f, 0, false, true, true)
        val guarded = ResourceGuardedLocalProvider(fake, RuntimeSignalsSource { signals })
        assertEquals(ProviderAvailability.Ready, guarded.availability())
        guarded.reason(KernelTurn("task", "Привет", budget = KernelBudget(maxOutputTokens = 700)))
        assertEquals(1, fake.reasonCalls)
        assertNotNull(fake.receivedTurn)
        assertEquals(512, fake.receivedTurn!!.budget.maxOutputTokens)
    }

    @Test fun coolMidMemoryPolicyCapsOutputBudgetAtHalfOf2048() = runBlocking {
        val fake = FakeProvider()
        // 3500 MB + COOL -> maxContextTokens = 2048 -> output budget capped to 1024
        val signals = RuntimeSignals(3500, 0.1f, 0, false, true, true)
        val guarded = ResourceGuardedLocalProvider(fake, RuntimeSignalsSource { signals })
        assertEquals(ProviderAvailability.Ready, guarded.availability())
        guarded.reason(KernelTurn("task", "Привет", budget = KernelBudget(maxOutputTokens = 1500)))
        assertEquals(1, fake.reasonCalls)
        assertNotNull(fake.receivedTurn)
        assertEquals(1024, fake.receivedTurn!!.budget.maxOutputTokens)
    }
}
