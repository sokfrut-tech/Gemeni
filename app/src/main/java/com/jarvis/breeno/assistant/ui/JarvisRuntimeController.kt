package com.jarvis.breeno.assistant.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.jarvis.breeno.assistant.core.AndroidRuntimeSignalsSource
import com.jarvis.breeno.assistant.core.AuthorizedCloudReasoningProvider
import com.jarvis.breeno.assistant.core.DeviceHeatState
import com.jarvis.breeno.assistant.core.ExternalAiConfig
import com.jarvis.breeno.assistant.core.ExternalAiConfigStore
import com.jarvis.breeno.assistant.core.JarvisCognitiveKernel
import com.jarvis.breeno.assistant.core.JarvisFoldedRuntimePolicy
import com.jarvis.breeno.assistant.core.JarvisOrbMode
import com.jarvis.breeno.assistant.core.JarvisPersona
import com.jarvis.breeno.assistant.core.KernelBudget
import com.jarvis.breeno.assistant.core.KernelTurn
import com.jarvis.breeno.assistant.core.LlamaCppReasoningProvider
import com.jarvis.breeno.assistant.core.LlamaNativeBridge
import com.jarvis.breeno.assistant.core.PromptTemplateFormat
import com.jarvis.breeno.assistant.core.ResourceGuardedLocalProvider
import com.jarvis.breeno.assistant.core.RuntimeSignals
import com.jarvis.breeno.assistant.core.computeAudioRms
import com.jarvis.breeno.assistant.memory.DialogueMessage
import com.jarvis.breeno.assistant.memory.JarvisMemoryRepository
import com.jarvis.breeno.assistant.nlp.RussianIntentDispatcher
import com.jarvis.breeno.assistant.updater.LocalLlmModelManager
import com.jarvis.breeno.assistant.updater.LocalLlmPack
import com.jarvis.breeno.assistant.updater.ModelDownloadManager
import com.jarvis.breeno.assistant.updater.PiperTtsModelDownloadManager
import com.jarvis.breeno.assistant.updater.SileroVadModelManager
import com.jarvis.breeno.assistant.voice.AudioCaptureCoordinator
import com.jarvis.breeno.assistant.voice.AudioRecordPipeline
import com.jarvis.breeno.assistant.voice.JarvisSpeaker
import com.jarvis.breeno.assistant.voice.SherpaRussianAsrEngine
import com.jarvis.breeno.assistant.voice.SileroVadEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AiStackStatus(
    val asrReady: Boolean,
    val ttsVoiceReady: Boolean,
    val readyTtsVoiceIds: Set<String>,
    val selectedVoiceId: String,
    val speakerLabel: String,
    val vadReady: Boolean,
    val nativeLlamaLoaded: Boolean,
    val nativeLlamaError: String?,
    val readyLlmIds: Set<String>,
    val activeLlmId: String?,
    val activeLlmTitle: String?,
    val externalAiConfig: ExternalAiConfig,
    val allLlmPacks: List<LocalLlmPack> = LocalLlmModelManager.CATALOG,
    val selectedPersona: JarvisPersona = JarvisPersona.STARK_JARVIS,
    val selectedPromptTemplate: PromptTemplateFormat = PromptTemplateFormat.AUTO,
    val speechSpeed: Float = PiperTtsModelDownloadManager.DEFAULT_SPEECH_SPEED,
)

/**
 * Full Jarvis runtime orchestrator:
 * Microphone / Text -> Russian ASR (Sherpa-ONNX + optional Silero VAD)
 * -> Deterministic Intents OR JarvisCognitiveKernel (ResourceGuarded LlamaCpp / Opt-in Cloud)
 * -> SQLite+FTS5 Memory -> Offline Russian Piper TTS (with Android TTS fallback).
 */
class JarvisRuntimeController(context: Context) {
    private val appContext = context.applicationContext
    val models = ModelDownloadManager(appContext)
    val ttsModels = PiperTtsModelDownloadManager(appContext)
    val llmModels = LocalLlmModelManager(appContext)
    val vadModels = SileroVadModelManager(appContext)
    private val externalAiStore = ExternalAiConfigStore(appContext)
    val memory = JarvisMemoryRepository(appContext)
    private val signalsSource = AndroidRuntimeSignalsSource(appContext)

    private val audio = AudioRecordPipeline(appContext)
    private val asr = SherpaRussianAsrEngine()
    private val vad = SileroVadEngine()
    private val intents = RussianIntentDispatcher()
    private val speaker = JarvisSpeaker(
        context = appContext,
        speechSpeedProvider = { ttsModels.speechSpeed() },
        piperModelDirProvider = { ttsModels.activeVoiceDir() },
    )

    private val llamaProvider = LlamaCppReasoningProvider(
        modelManager = llmModels,
        historyProvider = { memory.recentChatTurns(limit = 6) },
        memorySearchProvider = { query -> memory.searchFacts(query, limit = 4) },
    )
    private val guardedLocalProvider = ResourceGuardedLocalProvider(llamaProvider, signalsSource)
    private val cloudProvider = AuthorizedCloudReasoningProvider(
        configProvider = { externalAiStore.load() },
        historyProvider = { memory.recentChatTurns(limit = 6) },
        memorySearchProvider = { query -> memory.searchFacts(query, limit = 4) },
        personaProvider = { llmModels.selectedPersona() },
    )
    private val cognitiveKernel = JarvisCognitiveKernel(listOf(guardedLocalProvider, cloudProvider))

    @Volatile private var vadLoaded = false
    @Volatile private var lastSpeakingState = false
    @Volatile private var userCancelledSpeech = false

    var onAudioLevelChanged: ((Float) -> Unit)? = null
    var onOrbModeChanged: ((JarvisOrbMode) -> Unit)? = null
    var onNavigateTab: ((String) -> Unit)? = null
    var onRequestMediaGeneration: ((type: String, prompt: String) -> Unit)? = null
    var onSpeakerError: ((String) -> Unit)? = null
    var onSpeakingFinished: (() -> Unit)? = null
    var onCommandCompleted: ((spoken: String, response: String) -> Unit)? = null
    var onStackStatusChanged: (() -> Unit)? = null

    init {
        speaker.onError = { message ->
            onSpeakerError?.invoke(message)
        }
    }

    fun isRussianAsrReady(): Boolean = models.isRussianAsrReady()

    fun currentStackStatus(): AiStackStatus {
        val allPacks = llmModels.allPacks()
        val activePack = llmModels.activeReadyPack()
        val readyIds = allPacks.filter { llmModels.isPackReady(it.id) }.map { it.id }.toSet()
        val readyVoices = ttsModels.catalog().filter { ttsModels.isVoiceReady(it.id) }.map { it.id }.toSet()
        return AiStackStatus(
            asrReady = models.isRussianAsrReady(),
            ttsVoiceReady = readyVoices.isNotEmpty(),
            readyTtsVoiceIds = readyVoices,
            selectedVoiceId = ttsModels.selectedVoiceId(),
            speakerLabel = speaker.activeEngineLabel(),
            vadReady = vadModels.isReady(),
            nativeLlamaLoaded = LlamaNativeBridge.isNativeLibraryLoaded,
            nativeLlamaError = LlamaNativeBridge.loadError,
            readyLlmIds = readyIds,
            activeLlmId = activePack?.id,
            activeLlmTitle = activePack?.title,
            externalAiConfig = externalAiStore.load(),
            allLlmPacks = allPacks,
            selectedPersona = llmModels.selectedPersona(),
            selectedPromptTemplate = llmModels.selectedPromptTemplate(),
            speechSpeed = ttsModels.speechSpeed(),
        )
    }

    fun setMuted(muted: Boolean) {
        speaker.isMuted = muted
        if (muted) speaker.stop()
    }

    fun isMuted(): Boolean = speaker.isMuted

    fun setSpeechStateListener(listener: ((Boolean) -> Unit)?) {
        speaker.onSpeakingChanged = { speaking ->
            val finishedNormally = lastSpeakingState && !speaking && !userCancelledSpeech
            lastSpeakingState = speaking
            onOrbModeChanged?.invoke(if (speaking) JarvisOrbMode.SPEAKING else JarvisOrbMode.IDLE)
            listener?.invoke(speaking)
            if (finishedNormally) {
                onSpeakingFinished?.invoke()
            }
        }
    }

    fun recentDialogue(limit: Int = 12): List<DialogueMessage> = memory.recentMessages(limit)

    fun testRussianVoice() {
        speaker.speak("Привет! Я Джарвис. Локальный русский голосовой движок работает отлично.")
    }

    fun selectTtsVoice(voiceId: String) {
        ttsModels.selectVoice(voiceId)
        onStackStatusChanged?.invoke()
    }

    fun setSpeechSpeed(speed: Float) {
        ttsModels.setSpeechSpeed(speed)
        onStackStatusChanged?.invoke()
    }

    fun deleteTtsVoice(voiceId: String) {
        speaker.stop()
        ttsModels.deleteVoice(voiceId)
        onStackStatusChanged?.invoke()
    }

    fun selectLlmPack(packId: String) {
        llmModels.selectPack(packId)
        onStackStatusChanged?.invoke()
    }

    fun selectPersona(persona: JarvisPersona) {
        llmModels.selectPersona(persona)
        onStackStatusChanged?.invoke()
    }

    fun selectPromptTemplate(format: PromptTemplateFormat) {
        llmModels.selectPromptTemplate(format)
        onStackStatusChanged?.invoke()
    }

    suspend fun unloadLocalLlmForMemory() {
        llamaProvider.unload()
    }

    suspend fun importCustomGguf(
        uri: Uri,
        displayName: String,
        onProgress: (Int) -> Unit,
    ): LocalLlmPack {
        val pack = llmModels.importCustomGguf(uri, displayName, onProgress)
        onStackStatusChanged?.invoke()
        return pack
    }

    suspend fun deleteLlmPack(packId: String) {
        llamaProvider.unload()
        llmModels.deletePack(packId)
        onStackStatusChanged?.invoke()
    }

    fun saveExternalAiConfig(config: ExternalAiConfig) {
        externalAiStore.save(config)
        onStackStatusChanged?.invoke()
    }

    suspend fun downloadAsr(onProgress: (Int) -> Unit) {
        models.ensureRussianAsr(onProgress)
        onStackStatusChanged?.invoke()
    }

    suspend fun downloadTtsVoice(voiceId: String, onProgress: (Int) -> Unit) {
        ttsModels.ensureVoice(voiceId, onProgress)
        onStackStatusChanged?.invoke()
    }

    suspend fun downloadVad(onProgress: (Int) -> Unit) {
        vadModels.ensure(onProgress)
        onStackStatusChanged?.invoke()
    }

    suspend fun downloadLlmPack(packId: String, onProgress: (Int, Long, Long) -> Unit) {
        llmModels.ensurePack(packId, onProgress)
        onStackStatusChanged?.invoke()
    }

    suspend fun downloadOfflineStarterBundle(onStageProgress: (stage: String, percent: Int) -> Unit) {
        if (!models.isRussianAsrReady()) {
            onStageProgress("Скачиваю русский слух (ASR)…", 0)
            models.ensureRussianAsr { p -> onStageProgress("Русский слух (ASR): $p%", p) }
            onStackStatusChanged?.invoke()
        }
        if (!ttsModels.isReady()) {
            onStageProgress("Скачиваю голос Дмитрий HD (Piper FP32, 64 МБ)…", 0)
            ttsModels.ensureVoice(PiperTtsModelDownloadManager.VOICE_DMITRI_HD) { p ->
                onStageProgress("Голос Дмитрий HD (Piper): $p%", p)
            }
            onStackStatusChanged?.invoke()
        }
        if (!vadModels.isReady()) {
            onStageProgress("Скачиваю датчик речи Silero VAD (2 МБ)…", 0)
            vadModels.ensure { p -> onStageProgress("Silero VAD: $p%", p) }
            onStackStatusChanged?.invoke()
        }
        if (llmModels.activeReadyPack() == null) {
            onStageProgress("Скачиваю Dolphin 3.0 Qwen2.5 0.5B (379 МБ)…", 0)
            llmModels.ensurePack(LocalLlmModelManager.PACK_DOLPHIN_QWEN_05B) { p, dl, total ->
                val dlMb = dl / (1024 * 1024)
                val totalMb = (total / (1024 * 1024)).coerceAtLeast(1)
                onStageProgress("Мозг Dolphin 3.0 0.5B: $dlMb/$totalMb МБ ($p%)", p)
            }
            onStackStatusChanged?.invoke()
        }
    }

    suspend fun prepare(onProgress: (String) -> Unit) = prepare(userConsentToDownload = false, onProgress = onProgress)

    suspend fun prepare(userConsentToDownload: Boolean, onProgress: (String) -> Unit) {
        onProgress("Проверяю русскую модель…")
        if (!models.isRussianAsrReady() && !userConsentToDownload) {
            error("Русская ASR-модель не установлена. Для загрузки требуется явное согласие пользователя.")
        }
        val dir = if (models.isRussianAsrReady()) {
            models.modelDir()
        } else {
            models.ensureRussianAsr { p -> onProgress("Модель ASR: $p%") }
        }
        asr.load(dir)

        if (vadModels.isReady() && !vadLoaded) {
            runCatching {
                vad.load(vadModels.modelFile())
                vadLoaded = true
            }
        }
        onStackStatusChanged?.invoke()
        onProgress("Русский ASR готов")
    }

    suspend fun listenOnce(
        onProgress: (String) -> Unit,
        onTokenStream: (String) -> Unit = {},
    ): String {
        // Barge-in: immediately interrupt ongoing speech when user activates mic.
        speaker.stop()
        llamaProvider.cancelActiveGeneration()

        val samples = java.util.Collections.synchronizedList(mutableListOf<ShortArray>())
        var speechDetected = false
        var lastSpeechTime = System.currentTimeMillis()
        val useVad = vadLoaded
        if (useVad) runCatching { vad.reset() }

        val coordinator = AudioCaptureCoordinator(
            scope = CoroutineScope(currentCoroutineContext()),
            frames = { audio.frames() },
            onFrame = { frame ->
                samples.add(frame)
                onAudioLevelChanged?.invoke(computeAudioRms(frame))
                if (useVad) {
                    val active = runCatching { vad.accept(frame) }.getOrDefault(false)
                    if (active) {
                        speechDetected = true
                        lastSpeechTime = System.currentTimeMillis()
                    }
                }
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
            val startAt = System.currentTimeMillis()
            if (useVad) {
                while (currentCoroutineContext().isActive) {
                    delay(100)
                    val now = System.currentTimeMillis()
                    val elapsed = now - startAt
                    if (elapsed >= 12_000L) break
                    if (speechDetected && (now - lastSpeechTime) >= 1_100L) break
                    if (!speechDetected && elapsed >= 6_000L) break
                }
            } else {
                delay(5_000L)
            }
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

        handleUserMessage(text, onProgress = onProgress, onTokenStream = onTokenStream)
        return text
    }

    suspend fun submitText(
        rawText: String,
        onProgress: (String) -> Unit,
        onTokenStream: (String) -> Unit = {},
    ): String {
        val clean = rawText.trim()
        if (clean.isEmpty()) return ""
        speaker.stop()
        return handleUserMessage(clean, onProgress = onProgress, onTokenStream = onTokenStream)
    }

    suspend fun handleUserMessage(
        text: String,
        onProgress: (String) -> Unit,
        onTokenStream: (String) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        userCancelledSpeech = false

        // 1. Instant deterministic commands (time, date, memory, status, tabs, media gen, system intents)
        val deterministic = tryDeterministicCommand(text)
        if (deterministic != null) {
            onOrbModeChanged?.invoke(JarvisOrbMode.EXECUTING)
            memory.saveMessage("user", text, "user")
            memory.saveMessage("assistant", deterministic, "deterministic")
            onTokenStream(deterministic)
            onCommandCompleted?.invoke(text, deterministic)
            if (!speaker.speak(deterministic)) {
                onOrbModeChanged?.invoke(JarvisOrbMode.IDLE)
            }
            onProgress(deterministic)
            return@withContext deterministic
        }

        val hasLocalLlm = llmModels.activeReadyPack() != null
        val extCfg = externalAiStore.load()

        // Only show setup hint when BOTH local GGUF AND external AI are unconfigured/disabled.
        if (!hasLocalLlm && !extCfg.enabled) {
            val guidance = "Я вас услышал: «$text». Сейчас не выбран ни локальный ИИ, ни внешний сервер. " +
                "Откройте вкладку «Модели»: скачайте офлайн-набор с Dolphin 3.0 + голос Дмитрий HD, выберите Hermes 3 / Qwen 2.5 или импортируйте свой .gguf файл."
            memory.saveMessage("user", text, "user")
            memory.saveMessage("assistant", guidance, "setup-hint")
            onTokenStream(guidance)
            onCommandCompleted?.invoke(text, guidance)
            if (!speaker.speak(guidance)) {
                onOrbModeChanged?.invoke(JarvisOrbMode.IDLE)
            }
            onProgress("Нужна модель ИИ — откройте вкладку «Модели»")
            return@withContext guidance
        }

        // 2. Real Cognitive Kernel (Local GGUF via llama.cpp -> Optional Opt-in Cloud/PC)
        onOrbModeChanged?.invoke(JarvisOrbMode.THINKING)
        onProgress("Джарвис думает…")

        llamaProvider.onTokenDelta = { partial ->
            onTokenStream(partial)
        }
        llamaProvider.onStatusUpdate = { statusMsg ->
            onProgress(statusMsg)
        }

        val turn = KernelTurn(
            taskId = "turn-${System.currentTimeMillis()}",
            userText = text,
            locale = "ru-RU",
            networkAllowed = extCfg.enabled,
            budget = KernelBudget(maxOutputTokens = 384, maxToolProposals = 0, timeoutMillis = 45_000),
        )

        return@withContext try {
            val result = try {
                cognitiveKernel.process(turn)
            } catch (kernelErr: Throwable) {
                if (kernelErr is CancellationException) throw kernelErr
                // If user has a ready local GGUF model and device is NOT critically overheating,
                // seamlessly fall back to local inference on soft policy denial or upstream cloud errors (e.g. HTTP 402).
                val heatState = JarvisFoldedRuntimePolicy().plan(signalsSource.snapshot()).heatState
                val canFallbackToLocal = hasLocalLlm &&
                    heatState != DeviceHeatState.CRITICAL &&
                    LlamaNativeBridge.isNativeLibraryLoaded
                if (canFallbackToLocal) {
                    onProgress("Переключаюсь на локальный ИИ…")
                    val direct = llamaProvider.reason(
                        turn.copy(budget = turn.budget.copy(maxOutputTokens = 256))
                    )
                    com.jarvis.breeno.assistant.core.KernelTurnResult(
                        answer = direct.answer,
                        acceptedToolProposals = emptyList(),
                        rejectedToolProposalCount = 0,
                        providerId = llamaProvider.id,
                        modelId = direct.modelId,
                    )
                } else {
                    throw kernelErr
                }
            }

            memory.saveMessage("user", text, "user")
            memory.saveMessage("assistant", result.answer, result.modelId)
            onTokenStream(result.answer)
            onCommandCompleted?.invoke(text, result.answer)
            if (!speaker.speak(result.answer)) {
                onOrbModeChanged?.invoke(JarvisOrbMode.IDLE)
            }
            onProgress("Ответ готов (${result.modelId})")
            result.answer
        } catch (cancelled: CancellationException) {
            llamaProvider.cancelActiveGeneration()
            throw cancelled
        } catch (error: Throwable) {
            val realMsg = error.message ?: error.javaClass.simpleName
            onOrbModeChanged?.invoke(JarvisOrbMode.ERROR)
            val failText = "Ошибка ИИ: $realMsg"
            memory.saveMessage("user", text, "user")
            memory.saveMessage("assistant", failText, "error")
            onTokenStream(failText)
            onCommandCompleted?.invoke(text, failText)
            onProgress(failText)
            failText
        } finally {
            llamaProvider.onTokenDelta = null
            llamaProvider.onStatusUpdate = null
        }
    }

    private fun tryDeterministicCommand(text: String): String? {
        val lower = text.lowercase(Locale("ru", "RU")).trim()

        // Time
        if (lower.contains("сколько времен") || lower.contains("который час") ||
            lower == "время" || lower.contains("подскажи время")
        ) {
            val now = SimpleDateFormat("HH:mm", Locale("ru", "RU")).format(Date())
            return "Сейчас $now."
        }

        // Date
        if (lower.contains("какое число") || lower.contains("какая дата") ||
            lower.contains("какой сегодня день") || lower.contains("сегодня дата")
        ) {
            val dateStr = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("ru", "RU")).format(Date())
            return "Сегодня $dateStr."
        }

        // Explicit Memory Save
        val rememberMatch = Regex("""^запомни[,:\s]+(?:что\s+)?(.+)$""", RegexOption.IGNORE_CASE).find(text.trim())
        if (rememberMatch != null) {
            val fact = rememberMatch.groupValues[1].trim()
            if (fact.isNotEmpty()) {
                memory.rememberFact("user_${System.currentTimeMillis()}", fact)
                return "Запомнил: $fact."
            }
        }

        // Memory Recall
        if (lower.contains("что ты помнишь") || lower.contains("моя память") || lower.contains("что я просил запомнить")) {
            val facts = memory.recentFacts(5)
            return if (facts.isEmpty()) {
                "Память пока пуста. Скажите «Запомни...», и я сохраню важный факт."
            } else {
                "Вот что я помню: " + facts.joinToString("; ")
            }
        }

        // Device Telemetry Status
        if (lower.contains("статус системы") || lower.contains("сколько памяти") || lower.contains("состояние телефона")) {
            val signals: RuntimeSignals = signalsSource.snapshot()
            val status = currentStackStatus()
            val llmState = status.activeLlmTitle ?: if (status.externalAiConfig.enabled) "внешний ИИ" else "не скачана"
            return "Свободно оперативной памяти: ${signals.availableMemoryMb} МБ. " +
                "Голос: ${status.speakerLabel}. Мозг: $llmState."
        }

        // Image & Video Generation Studio Voice Triggers
        val drawMatch = Regex(
            """^(?:(?:джарвис|пожалуйста|можешь|сделай|мне)[,\s]*)*(?:нарисуй|нарисовать|сгенерируй картинку|сгенерируй изображение|создай картинку|создай изображение|сделай картинку)\s+(.+)$""",
            RegexOption.IGNORE_CASE,
        ).find(text.trim())
        if (drawMatch != null) {
            val mediaPrompt = drawMatch.groupValues[1].trim()
            if (mediaPrompt.isNotEmpty()) {
                onRequestMediaGeneration?.invoke("IMAGE", mediaPrompt)
                return "Открываю Студию и рисую: $mediaPrompt"
            }
        }

        val videoMatch = Regex(
            """^(?:(?:джарвис|пожалуйста|можешь)[,\s]*)*(?:создай видео|сгенерируй видео|сделай видео|сними видео)\s+(.+)$""",
            RegexOption.IGNORE_CASE,
        ).find(text.trim())
        if (videoMatch != null) {
            val mediaPrompt = videoMatch.groupValues[1].trim()
            if (mediaPrompt.isNotEmpty()) {
                onRequestMediaGeneration?.invoke("VIDEO", mediaPrompt)
                return "Открываю Студию и создаю видео: $mediaPrompt"
            }
        }

        // App Tab Navigation
        if (lower.contains("студи") && (lower.contains("открой") || lower.contains("покажи"))) {
            onNavigateTab?.invoke("STUDIO")
            return "Открываю Студию ИИ."
        }
        if (lower.contains("сохрани видео") || lower.contains("очередь видео") || lower.contains("скачай видео")) {
            onNavigateTab?.invoke("SAVEVIDEO")
            return "Открываю SaveVideo."
        }
        if (lower.contains("центр управления") || lower.contains("статистик") || lower.contains("журнал событий")) {
            onNavigateTab?.invoke("CONTROL")
            return "Открываю центр управления."
        }
        if (lower.contains("модел") && (lower.contains("открой") || lower.contains("настрой") || lower.contains("скачай"))) {
            onNavigateTab?.invoke("MODELS")
            return "Открываю раздел моделей ИИ."
        }

        // Existing Deterministic Android Intents
        val command = intents.dispatch(text)
        return when {
            command.startsWith("SEARCH:") -> {
                val query = text.replaceFirst(Regex("^(найди|поиск)\\s*", RegexOption.IGNORE_CASE), "").trim()
                if (query.isNotEmpty()) {
                    launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")))
                    "Открываю поиск: $query"
                } else {
                    null
                }
            }
            command.startsWith("OPEN:") && lower.contains("навига") -> {
                val destination = Regex("""\b(?:до|к)\s+(.+)$""", RegexOption.IGNORE_CASE)
                    .find(text)?.groupValues?.get(1)?.trim()?.trimEnd('.', '!', '?')
                    ?.takeIf { it.isNotBlank() }
                openNavigator(destination)
                if (destination == null) "Открываю навигацию. Назовите пункт назначения."
                else "Открываю маршрут до $destination"
            }
            command.startsWith("OPEN:") && (lower.contains("телефон") || lower.contains("звони")) -> {
                launch(Intent(Intent.ACTION_DIAL))
                "Открываю телефон"
            }
            command.startsWith("OPEN:") && (lower.contains("браузер") || lower.contains("интернет")) -> {
                launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")))
                "Открываю браузер"
            }
            lower.startsWith("позвони") -> {
                launch(Intent(Intent.ACTION_DIAL))
                "Открываю телефон"
            }
            else -> null
        }
    }

    fun cancelActiveWork() {
        userCancelledSpeech = true
        speaker.stop()
        llamaProvider.cancelActiveGeneration()
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

    fun close() {
        speaker.close()
        llamaProvider.close()
        runCatching { vad.close() }
        asr.close()
        memory.close()
    }
}
