package com.jarvis.breeno.assistant.ui

import android.content.Context
import com.jarvis.breeno.assistant.core.AndroidJarvisToolRegistry
import com.jarvis.breeno.assistant.core.AssistantState
import com.jarvis.breeno.assistant.core.ToolResult
import com.jarvis.breeno.assistant.core.ai.JarvisModelRequest
import com.jarvis.breeno.assistant.core.ai.JarvisModelResult
import com.jarvis.breeno.assistant.core.ai.LocalOnlyJarvisModelProvider
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
    private val reasoning = LocalOnlyJarvisModelProvider()
    private var pendingConfirmationJson: String? = null
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
        onProgress("Формирую безопасное предложение действия…")
        val modelResult = reasoning.propose(JarvisModelRequest(userText = text))
        onState(AssistantState.EXECUTING)

        var proposalJson: String? = null
        val toolResult = when (modelResult) {
            is JarvisModelResult.Proposal -> {
                proposalJson = modelResult.json
                tools.executeProposalJson(modelResult.json)
            }
            is JarvisModelResult.NoAction -> ToolResult.Failure(modelResult.reason)
            is JarvisModelResult.Failure -> ToolResult.Failure(modelResult.reason)
        }
        pendingConfirmationJson = if (toolResult is ToolResult.NeedsConfirmation) proposalJson else null

        val response = when (toolResult) {
            is ToolResult.Success -> toolResult.message
            is ToolResult.NeedsConfirmation -> "Требуется подтверждение: ${toolResult.description}"
            is ToolResult.Failure -> toolResult.message
        }

        onState(if (pendingConfirmationJson != null) AssistantState.WAITING_FOR_APPROVAL else AssistantState.SPEAKING)
        speaker.speak(response)
        onProgress(response)
        if (pendingConfirmationJson == null) onState(AssistantState.IDLE)
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

    fun confirmPendingAction(): String {
        val proposal = pendingConfirmationJson
            ?: return "Нет действия, ожидающего подтверждения"
        pendingConfirmationJson = null
        val response = when (val result = tools.executeProposalJson(proposal, confirmed = true)) {
            is ToolResult.Success -> result.message
            is ToolResult.NeedsConfirmation -> "Действие всё ещё требует подтверждения: ${result.description}"
            is ToolResult.Failure -> result.message
        }
        speaker.speak(response)
        return response
    }

    fun cancelPendingAction(): String {
        if (pendingConfirmationJson == null) return "Нет ожидающего действия"
        pendingConfirmationJson = null
        val response = "Действие отменено"
        speaker.speak(response)
        return response
    }

    fun close() { pendingConfirmationJson = null; asr.close() }
}
