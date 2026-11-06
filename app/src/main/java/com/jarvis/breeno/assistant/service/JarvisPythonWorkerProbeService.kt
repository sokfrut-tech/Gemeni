package com.jarvis.breeno.assistant.service

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process

/**
 * Security-boundary probe only. It executes no user code and contains no Python runtime.
 * The purpose is to prove Android isolatedProcess UID/permission separation before
 * evaluating any embedded interpreter.
 */
class JarvisPythonWorkerProbeService : Service() {
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.what != MSG_PROBE) return@Handler false
        val reply = Message.obtain(null, MSG_PROBE_RESULT).apply {
            data = Bundle().apply {
                putInt(KEY_UID, Process.myUid())
                putBoolean(KEY_HAS_INTERNET, checkSelfPermission(android.Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED)
                putBoolean(KEY_HAS_RECORD_AUDIO, checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                putString(KEY_PACKAGE_NAME, packageName)
            }
        }
        runCatching { message.replyTo?.send(reply) }
        true
    })

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    companion object {
        const val MSG_PROBE = 1
        const val MSG_PROBE_RESULT = 2
        const val KEY_UID = "uid"
        const val KEY_HAS_INTERNET = "has_internet"
        const val KEY_HAS_RECORD_AUDIO = "has_record_audio"
        const val KEY_PACKAGE_NAME = "package_name"
    }
}
