package com.jarvis.breeno.assistant.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process

/**
 * Live Android/ColorOS runtime telemetry source.
 * Uses PowerManager.currentThermalStatus, getThermalHeadroom, and battery temperature
 * so devices where getThermalHeadroom returns NaN (e.g. OPPO ColorOS) are not falsely
 * locked into DeviceHeatState.CRITICAL while still failing safe if all sensors are unavailable.
 */
class AndroidRuntimeSignalsSource(context: Context) : RuntimeSignalsSource {
    private val app = context.applicationContext
    private val activityManager = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val powerManager = app.getSystemService(Context.POWER_SERVICE) as PowerManager

    override fun snapshot(): RuntimeSignals {
        val memory = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memory)
        val foreground = (activityManager.runningAppProcesses ?: emptyList()).any {
            it.pid == Process.myPid() &&
                it.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
        }
        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { powerManager.currentThermalStatus }.getOrDefault(Int.MAX_VALUE)
        } else Int.MAX_VALUE
        val headroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { powerManager.getThermalHeadroom(10) }.getOrDefault(Float.NaN)
        } else Float.NaN

        val battery = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val batteryTempTenthsC = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1

        val (resolvedStatus, resolvedHeadroom) = resolveThermalSignals(
            rawThermalStatus = thermalStatus,
            rawHeadroom = headroom,
            batteryTempTenthsC = batteryTempTenthsC,
        )

        return RuntimeSignals(
            availableMemoryMb = (memory.availMem / (1024L * 1024L)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            thermalHeadroom = resolvedHeadroom,
            thermalStatus = resolvedStatus,
            batterySaver = powerManager.isPowerSaveMode,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL,
            foreground = foreground,
        )
    }

    companion object {
        fun resolveThermalSignals(
            rawThermalStatus: Int,
            rawHeadroom: Float,
            batteryTempTenthsC: Int,
        ): Pair<Int, Float> {
            val validHeadroom = rawHeadroom.isFinite() && rawHeadroom in 0f..1f
            val hasBatteryTemp = batteryTempTenthsC in 1..900
            val tempC = batteryTempTenthsC / 10f

            val batteryPair: Pair<Int, Float>? = if (hasBatteryTemp) {
                when {
                    tempC < 39.0f -> 0 to 0.20f
                    tempC < 43.0f -> 2 to 0.60f
                    tempC < 47.0f -> 3 to 0.85f
                    else -> 5 to 0.98f
                }
            } else {
                null
            }

            if (rawThermalStatus in 0..6) {
                val fallbackHeadroom = when (rawThermalStatus) {
                    0 -> 0.20f
                    1 -> 0.40f
                    2 -> 0.60f
                    3 -> 0.82f
                    4 -> 0.90f
                    else -> 0.98f
                }
                var status = rawThermalStatus
                var headroom = if (validHeadroom) rawHeadroom else fallbackHeadroom

                if (batteryPair != null && tempC >= 45.0f) {
                    status = maxOf(status, batteryPair.first)
                    headroom = maxOf(headroom, batteryPair.second)
                }
                return status to headroom
            }

            if (batteryPair != null) {
                return batteryPair
            }

            if (validHeadroom) {
                val derivedStatus = when {
                    rawHeadroom >= 0.95f -> 5
                    rawHeadroom >= 0.80f -> 3
                    rawHeadroom >= 0.55f -> 2
                    else -> 0
                }
                return derivedStatus to rawHeadroom
            }

            // Conservative fail-safe only if ALL thermal & battery sensors failed.
            return Int.MAX_VALUE to 1.0f
        }
    }
}
