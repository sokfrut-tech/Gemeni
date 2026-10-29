package com.jarvis.breeno.assistant.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.jarvis.breeno.assistant.IJarvisCallback
import com.jarvis.breeno.assistant.IJarvisService
import com.jarvis.breeno.assistant.core.TextCommandRouter
import com.jarvis.breeno.assistant.ui.JarvisRuntimeHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Канал AIDL. submitText выполняет текстовую команду.
 * startListening запускает микрофон и распознавание (тот же путь, что и кнопка в приложении),
 * stopListening останавливает запись. Результат уходит в callback.
 */
class JarvisRemoteService : Service() {
    private val callbacks = mutableSetOf<IJarvisCallback>()
    private val router by lazy { TextCommandRouter(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val runtime by lazy { JarvisRuntimeHolder.get(this) }
    private var listenJob: Job? = null

    private fun reply(text: String) {
        callbacks.forEach { it.onText(text) }
    }

    private fun startVoice() {
        if (listenJob?.isActive == true) return
        listenJob = scope.launch {
            try {
                withContext(Dispatchers.IO) { runtime.prepare { } }
                val text = withContext(Dispatchers.IO) { runtime.listenOnce { } }
                reply(if (text.isBlank()) "Речь не распознана" else "Распознано: $text")
            } catch (e: Exception) {
                reply("Ошибка микрофона: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun stopVoice() {
        listenJob?.cancel()
        listenJob = null
    }

    private val binder = object : IJarvisService.Stub() {
        override fun submitText(text: String) {
            reply(router.handle(text))
        }

        override fun startListening() {
            startVoice()
        }

        override fun stopListening() {
            stopVoice()
        }

        override fun registerCallback(c: IJarvisCallback) {
            callbacks.add(c)
        }

        override fun unregisterCallback(c: IJarvisCallback) {
            callbacks.remove(c)
        }
    }

    override fun onBind(i: Intent?): IBinder = binder

    override fun onDestroy() {
        stopVoice()
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }
}
