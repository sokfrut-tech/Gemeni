package com.jarvis.breeno.assistant.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.jarvis.breeno.assistant.nlp.RussianIntentDispatcher
import com.jarvis.breeno.assistant.updater.ModelDownloadManager
import com.jarvis.breeno.assistant.voice.AudioRecordPipeline
import com.jarvis.breeno.assistant.voice.JarvisSpeaker
import com.jarvis.breeno.assistant.voice.SherpaRussianAsrEngine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.takeWhile

/** Real runtime bridge: microphone -> local Russian ASR -> intent -> device action -> speech. */
class JarvisRuntimeController(context: Context) {
    private val appContext = context.applicationContext
    private val models = ModelDownloadManager(appContext)
    private val audio = AudioRecordPipeline(appContext)
    private val asr = SherpaRussianAsrEngine()
    private val intents = RussianIntentDispatcher()
    private val speaker = JarvisSpeaker(appContext)

    private val prepareLock = kotlinx.coroutines.sync.Mutex()
    @Volatile private var ready = false

    suspend fun prepare(onProgress: (String) -> Unit) = prepareLock.withLock {
        if (ready) {
            onProgress("Русский ASR готов")
            return@withLock
        }
        onProgress("Проверяю русскую модель…")
        val dir = models.ensureRussianAsr { p -> onProgress("Модель ASR: $p%") }
        asr.load(dir)
        ready = true
        onProgress("Русский ASR готов")
    }

    suspend fun listenOnce(onReply: (String) -> Unit = {}, onProgress: (String) -> Unit): String {
        val samples = ArrayList<ShortArray>()
        val started = System.nanoTime()
        onProgress("Микрофон активен — говорите")
        audio.frames()
            .takeWhile { (System.nanoTime() - started) / 1_000_000 < 4_500 }
            .collect { samples += it }

        val pcm = ShortArray(samples.sumOf { it.size })
        var offset = 0
        samples.forEach { frame -> frame.copyInto(pcm, offset); offset += frame.size }
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
                launch(Intent(Intent.ACTION_VIEW, Uri.parse("yandexnavi://build_route_on_map")))
                "Открываю Яндекс Навигатор"
            }
            command.startsWith("OPEN:") -> "Пытаюсь открыть: $text"
            text.contains("позвони", true) || text.contains("телефон", true) -> {
                launch(Intent(Intent.ACTION_DIAL))
                "Открываю телефон"
            }
            else -> "Вы сказали: $text"
        }
        onReply(response)
        speaker.speak(response)
        onProgress(response)
        return text
    }

    /** Озвучивает ответ через общий JarvisSpeaker (используется и текстовым вводом). */
    fun say(text: String) = speaker.speak(text)

    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(intent) }
    }

    fun close() { asr.close() }
}

/** Один общий экземпляр на процесс: экран приложения и канал AIDL используют одну модель. */
object JarvisRuntimeHolder {
    @Volatile private var instance: JarvisRuntimeController? = null
    fun get(context: Context): JarvisRuntimeController =
        instance ?: synchronized(this) {
            instance ?: JarvisRuntimeController(context.applicationContext).also { instance = it }
        }
}
