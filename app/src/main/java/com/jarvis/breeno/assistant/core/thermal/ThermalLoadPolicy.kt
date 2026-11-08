package com.jarvis.breeno.assistant.core.thermal

enum class ThermalLevel { NORMAL, WARM, HOT, CRITICAL }
data class ThermalBudget(val level: ThermalLevel, val maxGenerationTokens: Int, val allowHeavyInference: Boolean)

object ThermalLoadPolicy {
    fun fromAndroidStatus(status: Int): ThermalBudget = when (status) {
        0 -> ThermalBudget(ThermalLevel.NORMAL, 160, true)
        1 -> ThermalBudget(ThermalLevel.WARM, 96, true)
        2 -> ThermalBudget(ThermalLevel.HOT, 48, true)
        else -> ThermalBudget(ThermalLevel.CRITICAL, 0, false)
    }
}

/**
 * Applies worsening thermal signals immediately and delays recovery until the
 * same cooler status remains stable for [cooldownMs]. Time is injected for tests.
 */
internal class ThermalBudgetHysteresis(
    private val cooldownMs: Long = 30_000L
) {
    init { require(cooldownMs >= 0L) }

    private var current = ThermalLoadPolicy.fromAndroidStatus(0)
    private var coolingSinceMs: Long? = null
    private var coolingTarget: ThermalBudget? = null

    @Synchronized
    fun update(androidStatus: Int, nowMs: Long): ThermalBudget {
        val candidate = ThermalLoadPolicy.fromAndroidStatus(androidStatus)
        if (severity(candidate) >= severity(current)) {
            current = candidate
            resetCoolingWindow()
            return current
        }

        val started = coolingSinceMs
        if (coolingTarget != candidate || started == null || nowMs < started) {
            coolingTarget = candidate
            coolingSinceMs = nowMs
            return current
        }
        if (nowMs - started >= cooldownMs) {
            current = candidate
            resetCoolingWindow()
        }
        return current
    }

    private fun resetCoolingWindow() {
        coolingSinceMs = null
        coolingTarget = null
    }

    private fun severity(budget: ThermalBudget): Int = when (budget.level) {
        ThermalLevel.NORMAL -> 0
        ThermalLevel.WARM -> 1
        ThermalLevel.HOT -> 2
        ThermalLevel.CRITICAL -> 3
    }
}
