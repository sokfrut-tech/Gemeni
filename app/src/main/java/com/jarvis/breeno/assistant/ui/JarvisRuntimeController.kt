package com.jarvis.breeno.assistant.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.jarvis.breeno.assistant.nlp.RussianIntentDispatcher
import com.jarvis.breeno.assistant.updater.ModelDownloadManager
import com.jarvis.breeno.assistant.voice.AudioRecordPipeline
import com.jarvis.breeno.assistant.voice.AudioCaptureCoordinator
import com.jarvis.breeno.assistant.voice.JarvisSpeaker
import com.jarvis.breeno.assistant.voice.SherpaRussianAsrEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Real runtime bridge: microphone -> local Russian ASR -> intent -> device action -> speech. */
class JarvisRuntimeController(context: Context) {
    private val appContext = context.applicationContext
    private val models = ModelDownloadManager(appContext)
    private val audio = AudioRecordPipeline(appContext)
    private val asr = SherpaRussianAsrEngine()
    private val intents = RussianIntentDispatcher()
    private val speaker = JarvisSpeaker(appContext)

    fun isRussianAsrReady(): Boolean = models.isRussianAsrReady()

    suspend fun prepare(onProgress: (String) -> Unit) = prepare(userConsentToDownload = false, onProgress = onProgress)

    suspend fun prepare(userConsentToDownload: Boolean, onProgress: (String) -> Unit) {
        onProgress("Проверяю русскую модель…")
        if (!models.isRussianAsrReady() && !userConsentToDownload) {
            error("Русская ASR-модель не установлена. Для загрузки требуется явное согласие пользователя.")
        }
        val dir = if (models.isRussianAsrReady()) models.modelDir()
        else models.ensureRussianAsr { p -> onProgress("Модель ASR: $p%") }
        asr.load(dir)
        onProgress("Русский ASR готов")
    }

    suspend fun listenOnce(onProgress: (String) -> Unit): String {
        val samples = java.util.Collections.synchronizedList(mutableListOf<ShortArray>())
        val coordinator = AudioCaptureCoordinator(
            scope = CoroutineScope(currentCoroutineContext()),
            frames = { audio.frames() },
            onFrame = { frame -> samples.add(frame) },
            onFailure = { error -> onProgress("Ошибка микрофона: ${error.message ?: error.javaClass.simpleName}") },
        )
        onProgress("Микрофон активен — говорите")
        try {
            check(coordinator.start()) { "Microphone capture is already owned by another session" }
            delay(4_500)
        } finally {
            // Cancellation must not skip AudioRecordPipeline's finally/release block.
            withContext(NonCancellable) { coordinator.stop() }
        }

        val snapshot = synchronized(samples) { samples.toList() }
        val pcm = ShortArray(snapshot.sumOf { it.size })
        var offset = 0
        snapshot.forEach { frame -> frame.copyInto(pcm, offset); offset += frame.size }
        onProgress("Распознаю локально…")
        val text = asr.transcribe(pcm)
        if (text.isBlank()) {
            onProgress("Речь не распознана")
            return ""
        }

        val command = intents.dispatch(text)
        val response = when {
            command.startsWith("SEARCH:") -> {
                val query = text.replaceFirst(Regex("^(найди|поиск)\\s*"), "").trim()
                launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")))
                "Открываю поиск: $query"
            }
            command.startsWith("OPEN:") && text.contains("навига", true) -> {
                openNavigator()
                "Открываю навигацию"
            }
            command.startsWith("OPEN:") -> "Пытаюсь открыть: $text"
            text.contains("позвони", true) || text.contains("телефон", true) -> {
                launch(Intent(Intent.ACTION_DIAL))
                "Открываю телефон"
            }
            else -> "Вы сказали: $text"
        }
        speaker.speak(response)
        onProgress(response)
        return text
    }

    private fun openNavigator() {
        val yandex = Intent(Intent.ACTION_VIEW, Uri.parse("yandexnavi://build_route_on_map"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val target = if (yandex.resolveActivity(appContext.packageManager) != null) yandex else fallback
        check(target.resolveActivity(appContext.packageManager) != null) {
            "Не найдено приложение навигации. Установите Яндекс Навигатор или карты."
        }
        appContext.startActivity(target)
    }

    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        check(intent.resolveActivity(appContext.packageManager) != null) {
            "Не найдено приложение для выполнения команды"
        }
        appContext.startActivity(intent)
    }

    fun close() { asr.close() }
}
