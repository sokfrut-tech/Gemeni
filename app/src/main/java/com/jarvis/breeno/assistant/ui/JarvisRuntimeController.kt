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
import com.jarvis.breeno.assistant.documents.IndexedDocument
import com.jarvis.breeno.assistant.documents.JarvisDocumentStore
import com.jarvis.breeno.assistant.memory.ChatSession
import com.jarvis.breeno.assistant.memory.DialogueMessage
import com.jarvis.breeno.assistant.memory.JarvisMemoryRepository
import com.jarvis.breeno.assistant.memory.MemoryFact
import com.jarvis.breeno.assistant.nlp.RussianIntentDispatcher
import com.jarvis.breeno.assistant.tools.JarvisPhoneActionExecutor
import com.jarvis.breeno.assistant.tools.JarvisPhoneActions
import com.jarvis.breeno.assistant.tools.McpServerConfig
import com.jarvis.breeno.assistant.tools.McpServerStore
import com.jarvis.breeno.assistant.tools.PhoneActionIntent
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
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
 * -> Deterministic Intents & Phone Actions OR JarvisCognitiveKernel (ResourceGuarded LlamaCpp / Opt-in Cloud) + Local Document RAG
 * -> Multi-session SQLite+FTS5 Memory with SecretGuard -> Offline Russian Piper TTS.
 */
class JarvisRuntimeController(context: Context) {
    private val appContext = context.applicationContext
    val models = ModelDownloadManager(appContext)
    val ttsModels = PiperTtsModelDownloadManager(appContext)
    val llmModels = LocalLlmModelManager(appContext)
    val vadModels = SileroVadModelManager(appContext)
    private val externalAiStore = ExternalAiConfigStore(appContext)
    val memory = JarvisMemoryRepository(appContext)
    val documentStore = JarvisDocumentStore(appContext)
    val mcpServerStore = McpServerStore(appContext)
    private val phoneActionExecutor = JarvisPhoneActionExecutor(appContext)
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
        historyProvider = { memory.recentChatTurns(limit = 8, sessionId = memory.activeSessionId()) },
        memorySearchProvider = { query -> memory.searchFacts(query, limit = 4) },
        documentSearchProvider = { query -> documentStore.searchRelevantSnippets(query, limit = 3) },
    )
    private val guardedLocalProvider = ResourceGuardedLocalProvider(llamaProvider, signalsSource)
    private val cloudProvider = AuthorizedCloudReasoningProvider(
        configProvider = { externalAiStore.load() },
        historyProvider = { memory.recentChatTurns(limit = 8, sessionId = memory.activeSessionId()) },
        memorySearchProvider = { query -> memory.searchFacts(query, limit = 4) },
        personaProvider = { llmModels.selectedPersona() },
        documentSearchProvider = { query -> documentStore.searchRelevantSnippets(query, limit = 3) },
    )
    private val cognitiveKernel = JarvisCognitiveKernel(listOf(guardedLocalProvider, cloudProvider))

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow(memory.ensureActiveSession().id)
    val activeSessionId: StateFlow<String> = _activeSessionId.asStateFlow()

    private val _dialogueMessages = MutableStateFlow<List<DialogueMessage>>(emptyList())
    val dialogueMessages: StateFlow<List<DialogueMessage>> = _dialogueMessages.asStateFlow()

    private val _memoryFacts = MutableStateFlow<List<MemoryFact>>(emptyList())
    val memoryFacts: StateFlow<List<MemoryFact>> = _memoryFacts.asStateFlow()

    private val _memoryPaused = MutableStateFlow(memory.isMemoryPaused())
    val memoryPaused: StateFlow<Boolean> = _memoryPaused.asStateFlow()

    private val _lastDeletedFact = MutableStateFlow<MemoryFact?>(null)
    val lastDeletedFact: StateFlow<MemoryFact?> = _lastDeletedFact.asStateFlow()

    private val _documents = MutableStateFlow<List<IndexedDocument>>(emptyList())
    val documents: StateFlow<List<IndexedDocument>> = _documents.asStateFlow()

    private val _mcpServers = MutableStateFlow<List<McpServerConfig>>(emptyList())
    val mcpServers: StateFlow<List<McpServerConfig>> = _mcpServers.asStateFlow()

    private val _generationWarning = MutableStateFlow<String?>(null)
    val generationWarning: StateFlow<String?> = _generationWarning.asStateFlow()

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

    fun recentDialogue(limit: Int = 40): List<DialogueMessage> =
        memory.recentMessages(limit, _activeSessionId.value)

    fun refreshWorkspaceState() {
        val activeId = memory.activeSessionId()
        _activeSessionId.value = activeId
        _sessions.value = memory.listSessions()
        _dialogueMessages.value = memory.recentMessages(100, activeId)
        _memoryFacts.value = memory.listFacts()
        _memoryPaused.value = memory.isMemoryPaused()
        _documents.value = documentStore.listDocuments()
        _mcpServers.value = mcpServerStore.list()
    }

    fun selectSession(sessionId: String) {
        memory.setActiveSessionId(sessionId)
        refreshWorkspaceState()
    }

    fun startNewSession(): ChatSession {
        val created = memory.createSession(JarvisMemoryRepository.DEFAULT_SESSION_TITLE)
        refreshWorkspaceState()
        return created
    }

    fun deleteSession(sessionId: String) {
        memory.deleteSession(sessionId)
        refreshWorkspaceState()
    }

    fun setMemoryPaused(paused: Boolean) {
        memory.setMemoryPaused(paused)
        refreshWorkspaceState()
    }

    fun addMemoryFact(key: String, content: String): Pair<Boolean, String> {
        val outcome = memory.rememberFactChecked(key, content)
        refreshWorkspaceState()
        return outcome
    }

    fun deleteMemoryFact(factId: Long) {
        val removed = memory.deleteFactById(factId)
        if (removed != null) {
            _lastDeletedFact.value = removed
        }
        refreshWorkspaceState()
    }

    fun undoDeleteMemoryFact(): Boolean {
        val fact = _lastDeletedFact.value ?: return false
        val restored = memory.restoreFact(fact)
        _lastDeletedFact.value = null
        refreshWorkspaceState()
        return restored
    }

    fun clearLastDeletedFact() {
        _lastDeletedFact.value = null
    }

    fun exportMemoryJson(): String = memory.exportFactsJson()

    suspend fun importDocument(
        uri: Uri,
        displayName: String,
        mimeType: String = "",
    ): Result<IndexedDocument> {
        val result = documentStore.importDocument(uri, displayName, mimeType)
        refreshWorkspaceState()
        return result
    }

    fun deleteDocument(docId: String) {
        documentStore.deleteDocument(docId)
        refreshWorkspaceState()
    }

    fun saveMcpServers(servers: List<McpServerConfig>) {
        mcpServerStore.save(servers)
        refreshWorkspaceState()
    }

    fun executePhoneAction(action: PhoneActionIntent): Result<String> =
        phoneActionExecutor.execute(action)

    suspend fun downloadCustomHfGguf(
        downloadUrl: String,
        displayName: String,
        expectedSizeBytes: Long,
        onProgress: (Int, Long, Long) -> Unit,
    ): LocalLlmPack {
        val pack = llmModels.downloadCustomGgufFromUrl(
            downloadUrl = downloadUrl,
            displayName = displayName,
            expectedSizeBytes = expectedSizeBytes,
            onProgress = onProgress,
        )
        onStackStatusChanged?.invoke()
        return pack
    }

    fun calculateAppCacheBytes(): Long {
        fun dirSize(file: File?): Long {
            if (file == null || !file.exists()) return 0L
            if (file.isFile) return file.length()
            return file.listFiles()?.sumOf { dirSize(it) } ?: 0L
        }
        return dirSize(appContext.cacheDir) + dirSize(appContext.externalCacheDir)
    }

    fun clearAppCache(): Long {
        val freed = calculateAppCacheBytes()
        appContext.cacheDir?.listFiles()?.forEach { child -> runCatching { child.deleteRecursively() } }
        appContext.externalCacheDir?.listFiles()?.forEach { child -> runCatching { child.deleteRecursively() } }
        return freed
    }

    fun testRussianVoice() {
        speaker.speak("Привет! Я Джарвис. Локальный русский голосовой движок работает отлично.")
    }

    fun speakerSpeakDirect(text: String) {
        val clean = text.trim()
        if (clean.isNotEmpty()) {
            speaker.speak(clean)
        }
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

        // 1. Instant deterministic commands (time, date, memory, status, tabs, media gen, safe phone actions)
        val deterministic = tryDeterministicCommand(text)
        if (deterministic != null) {
            onOrbModeChanged?.invoke(JarvisOrbMode.EXECUTING)
            memory.saveMessage("user", text, "user", _activeSessionId.value)
            memory.saveMessage("assistant", deterministic, "deterministic", _activeSessionId.value)
            refreshWorkspaceState()
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
                "Откройте раздел «Модели ИИ» (в Настройках или через боковое меню): скачайте офлайн-набор с Dolphin 3.0 + голос Дмитрий HD, поищите GGUF на Hugging Face или импортируйте свой файл."
            memory.saveMessage("user", text, "user", _activeSessionId.value)
            memory.saveMessage("assistant", guidance, "setup-hint", _activeSessionId.value)
            refreshWorkspaceState()
            onTokenStream(guidance)
            onCommandCompleted?.invoke(text, guidance)
            if (!speaker.speak(guidance)) {
                onOrbModeChanged?.invoke(JarvisOrbMode.IDLE)
            }
            onProgress("Нужна модель ИИ — откройте «Модели» в настройках")
            return@withContext guidance
        }

        // 2. Real Cognitive Kernel (Local GGUF via llama.cpp -> Optional Opt-in Cloud/PC)
        onOrbModeChanged?.invoke(JarvisOrbMode.THINKING)
        onProgress("Джарвис думает…")

        llamaProvider.onTokenDelta = { partial ->
            _generationWarning.value = null
            onTokenStream(partial)
        }
        llamaProvider.onStatusUpdate = { statusMsg ->
            onProgress(statusMsg)
        }
        llamaProvider.onStallWarning = { warning ->
            _generationWarning.value = warning
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

            memory.saveMessage("user", text, "user", _activeSessionId.value)
            memory.saveMessage("assistant", result.answer, result.modelId, _activeSessionId.value)
            refreshWorkspaceState()
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
            memory.saveMessage("user", text, "user", _activeSessionId.value)
            memory.saveMessage("assistant", failText, "error", _activeSessionId.value)
            refreshWorkspaceState()
            onTokenStream(failText)
            onCommandCompleted?.invoke(text, failText)
            onProgress(failText)
            failText
        } finally {
            _generationWarning.value = null
            llamaProvider.onTokenDelta = null
            llamaProvider.onStatusUpdate = null
            llamaProvider.onStallWarning = null
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

        // Explicit Memory Save (enforces Memory Pause + SecretGuard)
        val rememberMatch = Regex("""^запомни[,:\s]+(?:что\s+)?(.+)$""", RegexOption.IGNORE_CASE).find(text.trim())
        if (rememberMatch != null) {
            val fact = rememberMatch.groupValues[1].trim()
            if (fact.isNotEmpty()) {
                val (saved, feedback) = addMemoryFact("user_${System.currentTimeMillis()}", fact)
                return if (saved) "Запомнил: $fact." else feedback
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

        // App Section Navigation
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
        if ((lower.contains("документ") || lower.contains("баз знаний") || lower.contains("база знаний")) &&
            (lower.contains("открой") || lower.contains("покажи"))
        ) {
            onNavigateTab?.invoke("DOCUMENTS")
            return "Открываю документы и базу знаний."
        }
        if (lower.contains("управля") && lower.contains("память") || (lower.contains("памят") && lower.contains("открой"))) {
            onNavigateTab?.invoke("MEMORY")
            return "Открываю память Джарвиса."
        }
        if ((lower.contains("инструмент") || lower.contains("mcp")) && (lower.contains("открой") || lower.contains("покажи"))) {
            onNavigateTab?.invoke("TOOLS_MCP")
            return "Открываю инструменты и MCP."
        }
        if (lower.contains("настройки джарвис") || lower == "настройки") {
            onNavigateTab?.invoke("SETTINGS")
            return "Открываю настройки Jarvis Ai."
        }

        // Safe Deterministic Phone Actions (Timer, Alarm, Torch, System Settings, Calculator, Safe URL, Share)
        JarvisPhoneActions.parseCommand(text)?.let { phoneAction ->
            return phoneActionExecutor.execute(phoneAction).fold(
                onSuccess = { message -> message },
                onFailure = { err -> "Не удалось выполнить действие: ${err.message ?: err.javaClass.simpleName}" },
            )
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
        documentStore.close()
    }
}
