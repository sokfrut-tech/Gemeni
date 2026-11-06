package com.jarvis.breeno.assistant.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRuntimeBudgetTest {
    @Test
    fun rejectsLargeModelWhenHeapOrStorageIsInsufficient() {
        val budget = ModelRuntimeBudget.evaluate(appHeapLimitMb = 700, freeStorageMb = 5000)
        assertEquals(ModelRuntimeBudget.Tier.NOT_RECOMMENDED, budget)
    }

    @Test
    fun recommendsSmallModelWhenOnlySmallBudgetIsAvailable() {
        val budget = ModelRuntimeBudget.evaluate(appHeapLimitMb = 1024, freeStorageMb = 1500)
        assertEquals(ModelRuntimeBudget.Tier.SMALL_MODEL_ONLY, budget)
    }

    @Test
    fun allowsMediumModelCandidateOnlyWithSufficientHeadroom() {
        val budget = ModelRuntimeBudget.evaluate(appHeapLimitMb = 2048, freeStorageMb = 4096)
        assertEquals(ModelRuntimeBudget.Tier.MEDIUM_MODEL_CANDIDATE, budget)
    }

    @Test
    fun thresholdsAreConservativeAndDeterministic() {
        assertTrue(ModelRuntimeBudget.evaluate(767, 4096) == ModelRuntimeBudget.Tier.NOT_RECOMMENDED)
        assertTrue(ModelRuntimeBudget.evaluate(768, 1023) == ModelRuntimeBudget.Tier.NOT_RECOMMENDED)
        assertTrue(ModelRuntimeBudget.evaluate(768, 1024) == ModelRuntimeBudget.Tier.SMALL_MODEL_ONLY)
    }
}
