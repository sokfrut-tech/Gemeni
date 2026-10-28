package com.jarvis.breeno.assistant.ui

import android.content.Context
import com.jarvis.breeno.assistant.nlp.RussianIntentDispatcher
import com.jarvis.breeno.assistant.updater.ModelDownloadManager
import com.jarvis.breeno.assistant.voice.AudioRecordPipeline
import com.jarvis.breeno.assistant.voice.JarvisSpeaker
import com.jarvis.breeno.assistant.voice.SherpaRussianAsrEngine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile

/** Real runtime bridge: microphone -> local Russian ASR -> intent -> spoken response. */
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
            command.startsWith("SEARCH:") -> "Ищу: ${text.replaceFirst(Regex("^(найди|поиск)\\s*"), "")}" 
            command.startsWith("OPEN:") -> "Команда на открытие: $text"
            else -> "Вы сказали: $text"
        }
        speaker.speak(response)
        onProgress(response)
        return text
    }

    fun close() { asr.close() }
}
