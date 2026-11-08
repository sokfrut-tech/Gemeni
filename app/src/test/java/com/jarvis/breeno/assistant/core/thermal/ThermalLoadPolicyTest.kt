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
}
