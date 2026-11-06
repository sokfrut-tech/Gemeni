package com.jarvis.breeno.assistant.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRuntimeBudgetTest {
    @Test
    fun rejectsLargeModelWhenSystemRamOrStorageIsInsufficient() {
        assertEquals(
            ModelRuntimeBudget.Tier.NOT_RECOMMENDED,
            ModelRuntimeBudget.evaluate(availableRamMb = 700, freeStorageMb = 5000)
        )
        assertEquals(
            ModelRuntimeBudget.Tier.NOT_RECOMMENDED,
            ModelRuntimeBudget.evaluate(availableRamMb = 2048, freeStorageMb = 500)
        )
    }

    @Test
    fun recommendsSmallModelWhenOnlySmallBudgetIsAvailable() {
        val budget = ModelRuntimeBudget.evaluate(availableRamMb = 1500, freeStorageMb = 1500)
        assertEquals(ModelRuntimeBudget.Tier.SMALL_MODEL_ONLY, budget)
    }

    @Test
    fun allowsMediumModelCandidateOnlyWithSufficientHeadroom() {
        val budget = ModelRuntimeBudget.evaluate(availableRamMb = 4096, freeStorageMb = 4096)
        assertEquals(ModelRuntimeBudget.Tier.MEDIUM_MODEL_CANDIDATE, budget)
    }

    @Test
    fun thresholdsAreConservativeAndDeterministic() {
        assertTrue(ModelRuntimeBudget.evaluate(1023, 4096) == ModelRuntimeBudget.Tier.NOT_RECOMMENDED)
        assertTrue(ModelRuntimeBudget.evaluate(1024, 1023) == ModelRuntimeBudget.Tier.NOT_RECOMMENDED)
        assertTrue(ModelRuntimeBudget.evaluate(1024, 1024) == ModelRuntimeBudget.Tier.SMALL_MODEL_ONLY)
    }
}
