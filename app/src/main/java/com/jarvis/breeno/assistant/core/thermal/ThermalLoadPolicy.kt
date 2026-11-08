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
