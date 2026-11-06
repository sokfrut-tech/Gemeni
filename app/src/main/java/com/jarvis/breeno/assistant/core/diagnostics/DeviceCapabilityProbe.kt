package com.jarvis.breeno.assistant.core.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.StatFs

/** A local-only snapshot. It contains device capability data, never user content. */
data class DeviceCapabilitySnapshot(
    val manufacturer: String,
    val model: String,
    val hardware: String,
    val socModel: String?,
    val androidApi: Int,
    val supportedAbis: List<String>,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val appHeapLimitMb: Long,
    val freeAppStorageMb: Long,
    val lowMemory: Boolean,
    val thermalStatus: Int?
)

class DeviceCapabilityProbe(context: Context) {
    private val appContext = context.applicationContext

    fun capture(): DeviceCapabilitySnapshot {
        val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val stat = StatFs(appContext.filesDir.absolutePath)
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val runtime = Runtime.getRuntime()

        return DeviceCapabilitySnapshot(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            hardware = Build.HARDWARE.orEmpty(),
            socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else null,
            androidApi = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            totalRamMb = memory.totalMem / MEBIBYTE,
            availableRamMb = memory.availMem / MEBIBYTE,
            appHeapLimitMb = runtime.maxMemory() / MEBIBYTE,
            freeAppStorageMb = (stat.availableBytes / MEBIBYTE).coerceAtLeast(0L),
            lowMemory = memory.lowMemory,
            thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                powerManager.currentThermalStatus
            } else {
                null
            }
        )
    }

    private companion object {
        const val MEBIBYTE = 1024L * 1024L
    }
}
