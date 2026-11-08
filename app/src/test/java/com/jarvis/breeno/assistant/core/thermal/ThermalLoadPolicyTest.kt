package com.jarvis.breeno.assistant.core.thermal

import org.junit.Assert.*
import org.junit.Test

class ThermalLoadPolicyTest {
    @Test fun normalAllowsFullBudget() {
        val b = ThermalLoadPolicy.fromAndroidStatus(0)
        assertEquals(ThermalLevel.NORMAL, b.level); assertEquals(160, b.maxGenerationTokens); assertTrue(b.allowHeavyInference)
    }

    @Test fun warmAndHotReduceBudget() {
        val warm = ThermalLoadPolicy.fromAndroidStatus(1); val hot = ThermalLoadPolicy.fromAndroidStatus(2)
        assertEquals(96, warm.maxGenerationTokens); assertEquals(48, hot.maxGenerationTokens)
        assertTrue(warm.allowHeavyInference); assertTrue(hot.allowHeavyInference)
    }

    @Test fun severeAndAbovePauseHeavyInference() {
        for (status in 3..6) {
            val b = ThermalLoadPolicy.fromAndroidStatus(status)
            assertEquals(ThermalLevel.CRITICAL, b.level); assertEquals(0, b.maxGenerationTokens); assertFalse(b.allowHeavyInference)
        }
    }

    @Test fun worseningThermalStatusAppliesImmediately() {
        val policy = ThermalBudgetHysteresis(cooldownMs = 30_000)
        assertEquals(ThermalLevel.CRITICAL, policy.update(4, 1_000).level)
        assertFalse(policy.update(4, 2_000).allowHeavyInference)
    }

    @Test fun recoveryRequiresSameCoolerStatusForFullCooldown() {
        val policy = ThermalBudgetHysteresis(cooldownMs = 30_000)
        assertEquals(ThermalLevel.CRITICAL, policy.update(4, 0).level)
        assertEquals(ThermalLevel.CRITICAL, policy.update(0, 1_000).level)
        assertEquals(ThermalLevel.CRITICAL, policy.update(0, 30_999).level)
        val recovered = policy.update(0, 31_000)
        assertEquals(ThermalLevel.NORMAL, recovered.level)
        assertTrue(recovered.allowHeavyInference)
    }

    @Test fun changingCoolerStatusRestartsRecoveryWindow() {
        val policy = ThermalBudgetHysteresis(cooldownMs = 30_000)
        policy.update(4, 0)
        assertEquals(ThermalLevel.CRITICAL, policy.update(0, 1_000).level)
        assertEquals(ThermalLevel.CRITICAL, policy.update(1, 20_000).level)
        assertEquals(ThermalLevel.CRITICAL, policy.update(1, 49_999).level)
        assertEquals(ThermalLevel.WARM, policy.update(1, 50_000).level)
    }

    @Test fun renewedHeatingRestartsRecoveryWindow() {
        val policy = ThermalBudgetHysteresis(cooldownMs = 30_000)
        policy.update(4, 0)
        policy.update(0, 1_000)
        // A partial improvement to HOT starts a new stable-status window;
        // the current CRITICAL budget must remain in force during cooldown.
        assertEquals(ThermalLevel.CRITICAL, policy.update(2, 10_000).level)
        assertEquals(ThermalLevel.CRITICAL, policy.update(0, 11_000).level)
        assertEquals(ThermalLevel.CRITICAL, policy.update(0, 40_999).level)
        assertEquals(ThermalLevel.NORMAL, policy.update(0, 41_000).level)
    }

    @Test fun clockRollbackRestartsRecoveryWindow() {
        val policy = ThermalBudgetHysteresis(cooldownMs = 30_000)
        policy.update(4, 10_000)
        assertEquals(ThermalLevel.CRITICAL, policy.update(0, 20_000).level)
        assertEquals(ThermalLevel.CRITICAL, policy.update(0, 5_000).level)
        assertEquals(ThermalLevel.NORMAL, policy.update(0, 35_000).level)
    }
}
