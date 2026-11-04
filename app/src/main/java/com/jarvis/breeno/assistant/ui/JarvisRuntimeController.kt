package com.jarvis.breeno.assistant.ui

import android.content.Context
import com.jarvis.breeno.assistant.core.AndroidJarvisToolRegistry
import com.jarvis.breeno.assistant.core.AssistantState
import com.jarvis.breeno.assistant.core.ToolResult
import com.jarvis.breeno.assistant.updater.ModelDownloadManager
import com.jarvis.breeno.assistant.voice.AudioRecordPipeline
import com.jarvis.breeno.assistant.voice.JarvisSpeaker
import com.jarvis.breeno.assistant.voice.SherpaRussianAsrEngine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile

/** Microphone -> local Russian ASR -> guarded Android tool registry -> speech. */
class JarvisRuntimeController(context: Context) {
    private val appContext = context.applicationContext
    private val models = ModelDownloadManager(appContext)
    private val audio = AudioRecordPipeline(appContext)
    private val asr = SherpaRussianAsrEngine()
    private val tools = AndroidJarvisToolRegistry(appContext)
    private val speaker = JarvisSpeaker(appContext)

    suspend fun prepare(onProgress: (String) -> Unit) {
        onProgress("Проверяю русскую модель…")
        val dir = models.ensureRussianAsr { p -> onProgress("Модель ASR: $p%") }
        asr.load(dir)
        onProgress("Русский ASR готов")
    }

    suspend fun listenOnce(
        onProgress: (String) -> Unit,
        onState: (AssistantState) -> Unit = {}
    ): String {
        val samples = ArrayList<ShortArray>()
        val started = System.nanoTime()
        onState(AssistantState.LISTENING)
        onProgress("Микрофон активен — говорите")
        audio.frames()
            .takeWhile { (System.nanoTime() - started) / 1_000_000 < 4_500 }
            .collect { samples += it }

        val pcm = ShortArray(samples.sumOf { it.size })
        var offset = 0
        samples.forEach { frame -> frame.copyInto(pcm, offset); offset += frame.size }

        onState(AssistantState.TRANSCRIBING)
        onProgress("Распознаю локально…")
        val text = asr.transcribe(pcm)
        if (text.isBlank()) {
            onState(AssistantState.IDLE)
            onProgress("Речь не распознана")
            return ""
        }

        onState(AssistantState.UNDERSTANDING)
        onProgress("Понимаю команду…")
        onState(AssistantState.EXECUTING)
        val response = when (val result = tools.executeUtterance(text)) {
            is ToolResult.Success -> result.message
            is ToolResult.NeedsConfirmation -> "Требуется подтверждение: ${result.description}"
            is ToolResult.Failure -> result.message
        }

        onState(AssistantState.SPEAKING)
        speaker.speak(response)
        onProgress(response)
        onState(AssistantState.IDLE)
        return text
    }

    fun runQuickCommand(label: String): String {
        val utterance = when (label) {
            "Навигатор" -> "навигатор"
            "Поиск" -> "найди новости"
            "Телефон" -> "позвони"
            else -> return "Эта команда пока не подключена"
        }
        val response = when (val result = tools.executeUtterance(utterance)) {
            is ToolResult.Success -> result.message
            is ToolResult.NeedsConfirmation -> result.description
            is ToolResult.Failure -> result.message
        }
        speaker.speak(response)
        return response
    }

    fun close() { asr.close() }
}
