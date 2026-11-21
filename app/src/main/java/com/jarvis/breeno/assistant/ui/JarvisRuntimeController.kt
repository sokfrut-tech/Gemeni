package com.jarvis.breeno.assistant.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.jarvis.breeno.assistant.core.JarvisOrbMode
import com.jarvis.breeno.assistant.core.computeAudioRms
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

    var onAudioLevelChanged: ((Float) -> Unit)? = null
    var onOrbModeChanged: ((JarvisOrbMode) -> Unit)? = null
    var onNavigateTab: ((String) -> Unit)? = null
    var onCommandCompleted: ((spoken: String, response: String) -> Unit)? = null

    fun isRussianAsrReady(): Boolean = models.isRussianAsrReady()

    fun setSpeechStateListener(listener: ((Boolean) -> Unit)?) {
        speaker.onSpeakingChanged = { speaking ->
            onOrbModeChanged?.invoke(if (speaking) JarvisOrbMode.SPEAKING else JarvisOrbMode.IDLE)
            listener?.invoke(speaking)
        }
    }

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
            onFrame = { frame ->
                samples.add(frame)
                onAudioLevelChanged?.invoke(computeAudioRms(frame))
            },
            onFailure = { error ->
                onOrbModeChanged?.invoke(JarvisOrbMode.ERROR)
                onProgress("Ошибка микрофона: ${error.message ?: error.javaClass.simpleName}")
            },
        )
        onOrbModeChanged?.invoke(JarvisOrbMode.LISTENING)
        onProgress("Микрофон активен — говорите")
        try {
            check(coordinator.start()) { "Microphone capture is already owned by another session" }
            delay(4_500)
        } finally {
            onAudioLevelChanged?.invoke(0f)
            // Cancellation must not skip AudioRecordPipeline's finally/release block.
            withContext(NonCancellable) { coordinator.stop() }
        }

        val snapshot = synchronized(samples) { samples.toList() }
        val pcm = ShortArray(snapshot.sumOf { it.size })
        var offset = 0
        snapshot.forEach { frame -> frame.copyInto(pcm, offset); offset += frame.size }
        onOrbModeChanged?.invoke(JarvisOrbMode.THINKING)
        onProgress("Распознаю локально…")
        val text = asr.transcribe(pcm)
        if (text.isBlank()) {
            onOrbModeChanged?.invoke(JarvisOrbMode.IDLE)
            onProgress("Речь не распознана")
            return ""
        }

        val command = intents.dispatch(text)
        onOrbModeChanged?.invoke(if (command.startsWith("SEARCH:")) JarvisOrbMode.SEARCHING else JarvisOrbMode.EXECUTING)
        val response = when {
            text.contains("видео", true) || text.contains("скачай", true) || text.contains("сохрани", true) -> {
                onNavigateTab?.invoke("SAVEVIDEO")
                "Открываю SaveVideo"
            }
            text.contains("центр", true) || text.contains("статист", true) || text.contains("статус", true) -> {
                onNavigateTab?.invoke("CONTROL")
                "Открываю центр управления"
            }
            command.startsWith("SEARCH:") -> {
                val query = text.replaceFirst(Regex("^(найди|поиск)\\s*"), "").trim()
                launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")))
                "Открываю поиск: $query"
            }
            command.startsWith("OPEN:") && text.contains("навига", true) -> {
                val destination = Regex("""\b(?:до|к)\s+(.+)$""", RegexOption.IGNORE_CASE)
                    .find(text)?.groupValues?.get(1)?.trim()?.trimEnd('.', '!', '?')
                    ?.takeIf { it.isNotBlank() }
                openNavigator(destination)
                if (destination == null) "Открываю навигацию. Назовите пункт назначения."
                else "Открываю маршрут до $destination"
            }
            command.startsWith("OPEN:") && (text.contains("телефон", true) || text.contains("звони", true)) -> {
                launch(Intent(Intent.ACTION_DIAL))
                "Открываю телефон"
            }
            command.startsWith("OPEN:") && (text.contains("браузер", true) || text.contains("интернет", true)) -> {
                launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")))
                "Открываю браузер"
            }
            command.startsWith("OPEN:") -> "Пока не знаю, как выполнить эту команду: $text"
            text.contains("позвони", true) || text.contains("телефон", true) -> {
                launch(Intent(Intent.ACTION_DIAL))
                "Открываю телефон"
            }
            else -> "Вы сказали: $text"
        }
        onCommandCompleted?.invoke(text, response)
        speaker.speak(response)
        onProgress(response)
        return text
    }

    private fun openNavigator(destination: String?) {
        val candidates = if (!destination.isNullOrBlank()) {
            listOf(
                Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(destination))),
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(destination))),
            )
        } else {
            listOf(
                Intent(Intent.ACTION_VIEW, Uri.parse("yandexnavi://build_route_on_map")),
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")),
            )
        }
        val target = candidates.firstOrNull { candidate ->
            candidate.resolveActivity(appContext.packageManager) != null
        } ?: error(
            if (destination.isNullOrBlank())
                "Не найдено приложение навигации. Установите Яндекс Навигатор или карты."
            else
                "Не найдено приложение для построения маршрута. Установите карты или навигатор."
        )
        target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(target)
    }

    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        check(intent.resolveActivity(appContext.packageManager) != null) {
            "Не найдено приложение для выполнения команды"
        }
        appContext.startActivity(intent)
    }

    fun close() { speaker.close(); asr.close() }
}
