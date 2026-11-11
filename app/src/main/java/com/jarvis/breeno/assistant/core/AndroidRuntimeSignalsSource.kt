package com.jarvis.breeno.assistant.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process

/** Unknown thermal telemetry is treated as CRITICAL, never as a cool device. */
class AndroidRuntimeSignalsSource(context: Context) : RuntimeSignalsSource {
    private val app = context.applicationContext
    private val activityManager = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val powerManager = app.getSystemService(Context.POWER_SERVICE) as PowerManager

    override fun snapshot(): RuntimeSignals {
        val memory = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memory)
        val foreground = (activityManager.runningAppProcesses ?: emptyList()).any {
            it.pid == Process.myPid() &&
                it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { powerManager.currentThermalStatus }.getOrDefault(Int.MAX_VALUE)
        } else Int.MAX_VALUE
        val headroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { powerManager.getThermalHeadroom(10) }.getOrDefault(Float.NaN)
        } else Float.NaN
        val validHeadroom = headroom.isFinite() && headroom in 0f..1f
        val battery = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return RuntimeSignals(
            availableMemoryMb = (memory.availMem / (1024L * 1024L)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            thermalHeadroom = if (validHeadroom) headroom else 1f,
            thermalStatus = if (validHeadroom && thermalStatus != Int.MAX_VALUE) thermalStatus else Int.MAX_VALUE,
            batterySaver = powerManager.isPowerSaveMode,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL,
            foreground = foreground,
        )
    }
}
