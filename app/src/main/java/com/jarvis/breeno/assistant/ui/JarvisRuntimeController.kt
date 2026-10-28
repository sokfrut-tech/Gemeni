package com.jarvis.breeno.assistant.ui

import android.content.Context
import com.jarvis.breeno.assistant.nlp.RussianIntentDispatcher
import com.jarvis.breeno.assistant.updater.ModelDownloadManager
import com.jarvis.breeno.assistant.voice.AudioRecordPipeline
import com.jarvis.breeno.assistant.voice.JarvisSpeaker
import com.jarvis.breeno.assistant.voice.SherpaRussianAsrEngine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeout

/** Real runtime bridge used by the UI. It owns microphone -> local ASR -> intent -> speech. */
class JarvisRuntimeController(context: Context) {
    private val appContext = context.applicationContext
    private val models = ModelDownloadManager(appContext)
    private val audio = AudioRecordPipeline(appContext)
    private val asr = SherpaRussianAsrEngine()
    private val intents = RussianIntentDispatcher()
    private val speaker = JarvisSpeaker(appContext)

    suspend fun prepare(onProgress: (String) -> Unit) {
        onProgress("Проверяю русскую модель…")
        val dir = models.ensureRussianAsr { p -> onProgress("Модель ASR: $p%") }
        asr.load(dir)
        onProgress("Русский ASR готов")
    }

    suspend fun listenOnce(onProgress: (String) -> Unit): String {
        val samples = ArrayList<ShortArray>()
        val started = System.nanoTime()
        onProgress("Микрофон активен — говорите")
        withTimeout(7_000) {
            audio.frames().collect { frame ->
                samples += frame
                if ((System.nanoTime() - started) / 1_000_000 >= 4_500) throw StopCapture()
            }
        }
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
            command.startsWith("SEARCH:") -> "Ищу: ${text.removePrefix("найди").removePrefix("поиск").trim()}"
            command.startsWith("OPEN:") -> "Команда на открытие: $text"
            else -> "Вы сказали: $text"
        }
        speaker.speak(response)
        onProgress(response)
        return text
    }

    fun close() {
        asr.close()
    }

    private class StopCapture : RuntimeException(null, null, false, false)
}
