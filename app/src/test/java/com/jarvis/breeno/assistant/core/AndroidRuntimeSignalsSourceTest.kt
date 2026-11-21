package com.jarvis.breeno.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidRuntimeSignalsSourceTest {
    @Test
    fun colorOsNanHeadroomWithCoolThermalStatusDoesNotBlockLocalLlm() {
        val (status, headroom) = AndroidRuntimeSignalsSource.resolveThermalSignals(
            rawThermalStatus = 0,
            rawHeadroom = Float.NaN,
            batteryTempTenthsC = 340, // 34.0 C
        )
        assertEquals(0, status)
        assertTrue(headroom < 0.5f)

        val plan = JarvisFoldedRuntimePolicy().plan(
            RuntimeSignals(
                availableMemoryMb = 4500,
                thermalHeadroom = headroom,
                thermalStatus = status,
                batterySaver = false,
                charging = false,
                foreground = true,
            )
        )
        assertEquals(DeviceHeatState.COOL, plan.heatState)
        assertTrue(plan.allowLocalLlm)
        assertEquals(2048, plan.maxContextTokens)
    }

    @Test
    fun batteryTemperatureFallbackWorksWhenPowerManagerThermalUnavailable() {
        val (coolStatus, coolHeadroom) = AndroidRuntimeSignalsSource.resolveThermalSignals(
            rawThermalStatus = Int.MAX_VALUE,
            rawHeadroom = Float.NaN,
            batteryTempTenthsC = 355, // 35.5 C
        )
        assertEquals(0, coolStatus)
        assertEquals(0.20f, coolHeadroom, 0.01f)

        val (hotStatus, hotHeadroom) = AndroidRuntimeSignalsSource.resolveThermalSignals(
            rawThermalStatus = Int.MAX_VALUE,
            rawHeadroom = Float.NaN,
            batteryTempTenthsC = 480, // 48.0 C
        )
        assertEquals(5, hotStatus)
        assertTrue(hotHeadroom >= 0.95f)
    }

    @Test
    fun missingAllSensorsRemainsFailSafeCritical() {
        val (status, headroom) = AndroidRuntimeSignalsSource.resolveThermalSignals(
            rawThermalStatus = Int.MAX_VALUE,
            rawHeadroom = Float.NaN,
            batteryTempTenthsC = -1,
        )
        assertEquals(Int.MAX_VALUE, status)
        assertEquals(1.0f, headroom, 0.001f)
    }
}
