package com.jarvis.breeno.assistant.ui

import android.content.Context
import com.jarvis.breeno.assistant.core.AndroidJarvisToolRegistry
import com.jarvis.breeno.assistant.core.AssistantState
import com.jarvis.breeno.assistant.core.ToolResult
import com.jarvis.breeno.assistant.core.LlamaCppRuntime
import com.jarvis.breeno.assistant.core.QwenModelInstaller
import com.jarvis.breeno.assistant.core.thermal.AndroidThermalGuard
import com.jarvis.breeno.assistant.core.ai.JarvisModelProvider
import com.jarvis.breeno.assistant.core.ai.LlamaCppJarvisModelProvider
import com.jarvis.breeno.assistant.core.ai.JarvisModelRequest
import com.jarvis.breeno.assistant.core.ai.JarvisModelResult
import com.jarvis.breeno.assistant.core.ai.LocalOnlyJarvisModelProvider
import com.jarvis.breeno.assistant.updater.ModelDownloadManager
import com.jarvis.breeno.assistant.updater.SileroVadModelManager
import com.jarvis.breeno.assistant.voice.AudioRecordPipeline
import com.jarvis.breeno.assistant.voice.JarvisSpeaker
import com.jarvis.breeno.assistant.voice.SherpaRussianAsrEngine
import com.jarvis.breeno.assistant.voice.SileroVadEngine
import com.jarvis.breeno.assistant.voice.VadCapturePolicy
import android.os.SystemClock
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile

/** Microphone -> local Russian ASR -> guarded Android tool registry -> speech. */
class JarvisRuntimeController(context: Context) {
    private val appContext = context.applicationContext
    private val models = ModelDownloadManager(appContext)
    private val vadModels = SileroVadModelManager(appContext)
    private val audio = AudioRecordPipeline(appContext)
    private val asr = SherpaRussianAsrEngine()
    private val vad = SileroVadEngine()
    private val tools = AndroidJarvisToolRegistry(appContext)
    private var reasoning: JarvisModelProvider = LocalOnlyJarvisModelProvider()
    private val qwenRuntime = LlamaCppRuntime()
    private val qwenInstaller = QwenModelInstaller(appContext)
    private var pendingConfirmationJson: String? = null
    private val speaker = JarvisSpeaker(appContext)
    private val thermalGuard = AndroidThermalGuard(appContext)

    fun hasQwenModelFile(): Boolean = qwenInstaller.installedModelFile().isFile

    suspend fun installAndActivateQwen(
        userConsent: Boolean,
        onProgress: (QwenModelInstaller.Progress) -> Unit
    ): String {
        val installed = qwenInstaller.install(userConsent, onProgress)
        return activateQwenFile(installed.file)
    }

    fun activateInstalledQwen(): String = activateQwenFile(qwenInstaller.installedModelFile())

    private fun activateQwenFile(file: java.io.File): String {
        return when (val result = qwenRuntime.loadVerifiedModel(
            file,
            QwenModelInstaller.MODEL_SHA256
        )) {
            is LlamaCppRuntime.LoadResult.Loaded -> {
                reasoning = LlamaCppJarvisModelProvider(qwenRuntime) { thermalGuard.budget() }
                "Qwen загружена и подключена. Запросы обрабатываются локально."
            }
            is LlamaCppRuntime.LoadResult.Rejected -> result.reason
        }
    }

    suspend fun removeQwenModel(): String {
        reasoning = LocalOnlyJarvisModelProvider()
        qwenRuntime.close()
        return if (qwenInstaller.removeInstalledModel()) {
            "Файл Qwen удалён. Остались офлайн-команды."
        } else {
            "Не удалось полностью удалить файл модели."
        }
    }

    suspend fun areVoiceModelsReady(): Boolean =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            models.isRussianAsrReady() && vadModels.isReady()
        }

    /** One explicit user action authorizes both local speech models; neither downloads at startup. */
    suspend fun downloadVoiceModelsAfterConsent(
        userConsent: Boolean,
        onProgress: (String) -> Unit
    ) {
        require(userConsent) { "Для загрузки голосовых моделей требуется явное согласие пользователя" }
        onProgress("Загружаю русскую модель распознавания…")
        models.ensureRussianAsr { percent -> onProgress("Русская модель: $percent%") }
        check(models.isRussianAsrReady()) { "Проверка целостности русской ASR-модели не пройдена" }

        onProgress("Загружаю компактную модель определения речи Silero VAD…")
        vadModels.ensure(userConsent = true) { percent ->
            onProgress("Модель Silero VAD: $percent%")
        }
        check(vadModels.isReady()) { "Проверка целостности Silero VAD не пройдена" }
        onProgress("Обе голосовые модели загружены и проверены")
    }

    suspend fun prepare(onProgress: (String) -> Unit) {
        onProgress("Проверяю локальные голосовые модели…")
        check(models.isRussianAsrReady()) {
            "Русская ASR-модель не установлена. Подтвердите её загрузку."
        }
        check(vadModels.isReady()) {
            "Модель Silero VAD не установлена. Подтвердите загрузку голосовых моделей."
        }
        asr.load(models.modelDir())
        vad.load(vadModels.modelFile())
        onProgress("Русский ASR и Silero VAD готовы")
    }

    suspend fun listenOnce(
        onProgress: (String) -> Unit,
        onState: (AssistantState) -> Unit = {},
        onConversation: (userText: String, assistantText: String) -> Unit = { _, _ -> }
    ): String {
        val segments = ArrayList<FloatArray>()
        val policy = VadCapturePolicy()
        val started = SystemClock.elapsedRealtime()
        var decision = VadCapturePolicy.Decision.CONTINUE
        onState(AssistantState.LISTENING)
        onProgress("Микрофон активен — говорите")
        vad.reset()
        try {
            audio.frames().takeWhile { frame ->
                val speechDetected = vad.accept(frame)
                var emittedSegment = false
                while (true) {
                    val segment = vad.pollSegment() ?: break
                    segments += segment
                    emittedSegment = true
                }
                decision = policy.onFrame(
                    elapsedMs = SystemClock.elapsedRealtime() - started,
                    speechDetected = speechDetected,
                    completedSegmentAvailable = emittedSegment
                )
                decision == VadCapturePolicy.Decision.CONTINUE
            }.collect { /* Frames are processed in the takeWhile predicate. */ }
            if (decision == VadCapturePolicy.Decision.HARD_LIMIT) {
                vad.flush()
                while (true) {
                    val segment = vad.pollSegment() ?: break
                    segments += segment
                }
            }
        } finally {
            vad.reset()
        }

        if (decision == VadCapturePolicy.Decision.NO_SPEECH_TIMEOUT || segments.isEmpty()) {
            onState(AssistantState.IDLE)
            onProgress("Речь не обнаружена. Нажмите микрофон и попробуйте ещё раз.")
            return ""
        }

        val sampleCount = segments.sumOf { it.size }
        val pcm = ShortArray(sampleCount)
        var offset = 0
        segments.forEach { segment ->
            segment.forEach { sample ->
                pcm[offset++] = (sample * 32768f)
                    .toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
        }

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
        val budget = thermalGuard.budget()
        if (budget.level != com.jarvis.breeno.assistant.core.thermal.ThermalLevel.NORMAL) onProgress("Тепловая защита: ${budget.level.name}. Нагрузка Jarvis ограничена.")
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
        onConversation(text, response)
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

    fun close() {
        pendingConfirmationJson = null
        speaker.close()
        qwenRuntime.close()
        thermalGuard.close()
        asr.close()
        vad.close()
    }
}
