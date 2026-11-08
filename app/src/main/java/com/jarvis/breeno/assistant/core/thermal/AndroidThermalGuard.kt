package com.jarvis.breeno.assistant.core.thermal

import android.content.Context
import android.os.PowerManager

/** Observes Android thermal status and budgets Jarvis workloads; never changes CPU frequencies. */
class AndroidThermalGuard(context: Context) : AutoCloseable {
    private val power = context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    @Volatile private var latestStatus = runCatching { power.currentThermalStatus }.getOrDefault(0)
    private val listener = PowerManager.OnThermalStatusChangedListener { latestStatus = it }

    init { runCatching { power.addThermalStatusListener(context.mainExecutor, listener) } }

    fun budget(): ThermalBudget {
        latestStatus = runCatching { power.currentThermalStatus }.getOrDefault(latestStatus)
        return ThermalLoadPolicy.fromAndroidStatus(latestStatus)
    }

    override fun close() { runCatching { power.removeThermalStatusListener(listener) } }
}
