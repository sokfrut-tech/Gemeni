package com.jarvis.breeno.assistant.provider

import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONObject

/**
 * Read-only compatibility detection for Chinese ColorOS Breeno.
 * This class never modifies or hooks the host process. It supplies version-aware
 * information to the optional bridge layer.
 */
object BreenoCompatibility {
    private const val PACKAGE = "com.heytap.speechassist"

    data class Host(val installed: Boolean, val versionCode: Long?, val versionName: String?)

    fun detect(context: Context): Host {
        return try {
            val info = context.packageManager.getPackageInfo(PACKAGE, 0)
            Host(true, info.longVersionCode, info.versionName)
        } catch (_: PackageManager.NameNotFoundException) {
            Host(false, null, null)
        }
    }

    fun reference(context: Context): JSONObject? {
        return runCatching {
            context.resources.openRawResource(
                context.resources.getIdentifier("breeno_130302_reference", "raw", context.packageName)
            ).bufferedReader().use { JSONObject(it.readText()) }
        }.getOrNull()
    }
}
