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
import com.jarvis.breeno.assistant.tools.JarvisWebTools
import com.jarvis.breeno.assistant.tools.McpServerConfig
import com.jarvis.breeno.assistant.tools.McpServerStore
import com.jarvis.breeno.assistant.tools.PhoneActionIntent
import com.jarvis.breeno.assistant.ui.theme.JarvisAppearanceConfig
import com.jarvis.breeno.assistant.ui.theme.JarvisAppearanceStore
import com.jarvis.breeno.assistant.updater.LlmGenerationSettings
import com.jarvis.breeno.assistant.updater.LocalLlmModelManager
import com.jarvis.breeno.assistant.updater.LocalLlmPack
import com.jarvis.breeno.assistant.updater.ModelDownloadManager
import com.jarvis.breeno.assistant.updater.PiperTtsModelDownloadManager
import com.jarvis.breeno.assistant.updater.SileroVadModelManager
import com.jarvis.breeno.assistant.voice.AudioCaptureCoordinator
import com.jarvis.breeno.assistant.voice.AudioRecordPipeline
import com.jarvis.breeno.assistant.voice.JarvisSpeaker
import com.jarvis.breeno.assistant.voice.PiperTtsEngine
import com.jarvis.breeno.assistant.voice.RussianStreamingSpeechChunker
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
    val generationSettings: LlmGenerationSettings = LlmGenerationSettings(),
    val webToolsEnabled: Boolean = true,
    val lastGenerationSpeed: String? = null,
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
    val appearanceStore = JarvisAppearanceStore(appContext)
    private val webPrefs = appContext.getSharedPreferences("jarvis_web_tools", Context.MODE_PRIVATE)
    private val voicePrefs = appContext.getSharedPreferences("jarvis_voice_prefs", Context.MODE_PRIVATE)
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

    private val _appearance = MutableStateFlow(appearanceStore.load())
    val appearance: StateFlow<JarvisAppearanceConfig> = _appearance.asStateFlow()

    private val _webToolsEnabled = MutableStateFlow(webPrefs.getBoolean("web_tools_enabled", true))
    val webToolsEnabled: StateFlow<Boolean> = _webToolsEnabled.asStateFlow()

    @Volatile private var vadLoaded = false
    @Volatile private var lastSpeakingState = false
    @Volatile private var userCancelledSpeech = false
    @Volatile private var lastTorchEnabled = false
    @Volatile private var lastTurnWasVoice = false

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
            generationSettings = llmModels.generationSettings(),
            webToolsEnabled = _webToolsEnabled.value,
            lastGenerationSpeed = llamaProvider.lastSpeedLabel,
        )
    }

    fun updateAppearance(next: JarvisAppearanceConfig) {
        appearanceStore.save(next)
        _appearance.value = appearanceStore.load()
    }

    fun setWebToolsEnabled(enabled: Boolean) {
        webPrefs.edit().putBoolean("web_tools_enabled", enabled).apply()
        _webToolsEnabled.value = enabled
        onStackStatusChanged?.invoke()
    }

    fun saveGenerationSettings(settings: LlmGenerationSettings) {
        llmModels.saveGenerationSettings(settings)
        onStackStatusChanged?.invoke()
    }

    suspend fun addTextDocumentNote(title: String, rawText: String): Result<IndexedDocument> {
        val result = documentStore.addTextNote(title, rawText)
        refreshWorkspaceState()
        return result
    }

    fun exportActiveDialogueMarkdown(): String {
        val activeSession = _sessions.value.firstOrNull { it.id == _activeSessionId.value }
        val messages = _dialogueMessages.value
        val sb = StringBuilder()
        sb.append("# Jarvis Ai — ").append(activeSession?.title ?: "Диалог").append("\n\n")
        messages.forEach { msg ->
            val who = if (msg.role == "user") "Пользователь" else "Jarvis (${msg.provider})"
            sb.append("### ").append(who).append('\n')
            sb.append(msg.content.trim()).append("\n\n")
        }
        return sb.toString().trim()
    }

    suspend fun regenerateLastReply(
        onProgress: (String) -> Unit,
        onTokenStream: (String) -> Unit = {},
    ): String {
        val lastUserMsg = _dialogueMessages.value.lastOrNull { it.role == "user" }?.content?.trim().orEmpty()
        if (lastUserMsg.isEmpty()) return ""
        speaker.stop()
        return handleUserMessage(
            text = lastUserMsg,
            onProgress = onProgress,
            onTokenStream = onTokenStream,
            recordUserMessage = false,
        )
    }

    fun deviceDiagnosticsText(): String {
        val signals: RuntimeSignals = signalsSource.snapshot()
        val status = currentStackStatus()
        val llmState = status.activeLlmTitle ?: if (status.externalAiConfig.enabled) "внешний ИИ" else "не загружена"
        val powerState = if (signals.charging) "на зарядке" else if (signals.batterySaver) "энергосбережение" else "от батареи"
        return "Свободно RAM: ${signals.availableMemoryMb} МБ • Питание: $powerState • " +
            "Нагрев: ${if (signals.thermalStatus in 0..6) "уровень ${signals.thermalStatus}" else "норма"} • " +
            "Голос: ${status.speakerLabel} • Мозг: $llmState."
    }

    fun isContinuousDialogueEnabled(): Boolean = voicePrefs.getBoolean("continuous_dialogue_enabled", true)

    fun setContinuousDialogueEnabled(enabled: Boolean) {
        voicePrefs.edit().putBoolean("continuous_dialogue_enabled", enabled).apply()
    }

    fun wasLastTurnVoice(): Boolean = lastTurnWasVoice

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
            if (finishedNormally && lastTurnWasVoice) {
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

        handleUserMessage(text, onProgress = onProgress, onTokenStream = onTokenStream, fromVoice = true)
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
        return handleUserMessage(clean, onProgress = onProgress, onTokenStream = onTokenStream, fromVoice = false)
    }

    suspend fun handleUserMessage(
        text: String,
        onProgress: (String) -> Unit,
        onTokenStream: (String) -> Unit = {},
        recordUserMessage: Boolean = true,
        fromVoice: Boolean = false,
    ): String = withContext(Dispatchers.IO) {
        userCancelledSpeech = false
        lastTurnWasVoice = fromVoice
        val commandText = JarvisPhoneActions.stripWakeWordPrefix(text)

        fun persistTurn(assistantText: String, providerTag: String) {
            if (recordUserMessage) {
                memory.saveMessage("user", text, "user", _activeSessionId.value)
            }
            memory.saveMessage("assistant", assistantText, providerTag, _activeSessionId.value)
        }

        // 0. Hands-free stop command ("стоп", "хватит", "отбой", "пока")
        if (fromVoice && JarvisPhoneActions.isStopListeningCommand(text)) {
            userCancelledSpeech = true
            val bye = "Хорошо, отключаю микрофон."
            onOrbModeChanged?.invoke(JarvisOrbMode.IDLE)
            persistTurn(bye, "deterministic")
            refreshWorkspaceState()
            onTokenStream(bye)
            onCommandCompleted?.invoke(text, bye)
            speaker.speak(bye)
            onProgress("Микрофон отключён")
            return@withContext bye
        }

        // 0.5. Standalone wake word ("Джарвис", "жарюс", "джервис") -> instant deterministic reply, no LLM hallucination
        if (JarvisPhoneActions.isOnlyWakeWord(text)) {
            val ack = "Да, слушаю вас."
            onOrbModeChanged?.invoke(JarvisOrbMode.EXECUTING)
            persistTurn(ack, "deterministic")
            refreshWorkspaceState()
            onTokenStream(ack)
            onCommandCompleted?.invoke(text, ack)
            if (!speaker.speak(ack)) {
                onOrbModeChanged?.invoke(JarvisOrbMode.IDLE)
                if (fromVoice) onSpeakingFinished?.invoke()
            }
            onProgress(ack)
            return@withContext ack
        }

        // 1. Offline Unit Converter & Instant Deterministic Commands (time, date, memory, status, tabs, media gen, safe phone actions)
        JarvisWebTools.convertUnitsOffline(commandText)?.let { converted ->
            onOrbModeChanged?.invoke(JarvisOrbMode.EXECUTING)
            persistTurn(converted, "deterministic")
            refreshWorkspaceState()
            onTokenStream(converted)
            onCommandCompleted?.invoke(text, converted)
            if (!speaker.speak(converted)) {
                onOrbModeChanged?.invoke(JarvisOrbMode.IDLE)
                if (fromVoice) onSpeakingFinished?.invoke()
            }
            onProgress(converted)
            return@withContext converted
        }

        val webEnrichedContext = if (_webToolsEnabled.value) {
            fetchWebContextIfRequested(commandText, onProgress)
        } else {
            null
        }

        val deterministic = if (webEnrichedContext == null) tryDeterministicCommand(commandText) else null
        if (deterministic != null) {
            onOrbModeChanged?.invoke(JarvisOrbMode.EXECUTING)
            persistTurn(deterministic, "deterministic")
            refreshWorkspaceState()
            onTokenStream(deterministic)
            onCommandCompleted?.invoke(text, deterministic)
            if (!speaker.speak(deterministic)) {
                onOrbModeChanged?.invoke(JarvisOrbMode.IDLE)
                if (fromVoice) onSpeakingFinished?.invoke()
            }
            onProgress(deterministic)
            return@withContext deterministic
        }

        val hasLocalLlm = llmModels.activeReadyPack() != null
        val extCfg = externalAiStore.load()

        // If web search / URL reader returned live content and no LLM is active, return the web summary directly!
        if (webEnrichedContext != null && !hasLocalLlm && !extCfg.enabled) {
            val directWebReply = webEnrichedContext.directUserAnswer
            persistTurn(directWebReply, "web-search")
            refreshWorkspaceState()
            onTokenStream(directWebReply)
            onCommandCompleted?.invoke(text, directWebReply)
            if (!speaker.speak(webEnrichedContext.spokenSummary)) {
                onOrbModeChanged?.invoke(JarvisOrbMode.IDLE)
            }
            onProgress("Найдено в сети")
            return@withContext directWebReply
        }

        // Only show setup hint when BOTH local GGUF AND external AI are unconfigured/disabled.
        if (!hasLocalLlm && !extCfg.enabled) {
            val guidance = "Я вас услышал: «$text». Сейчас не выбран ни локальный ИИ, ни внешний сервер. " +
                "Откройте раздел «Модели ИИ» (в Настройках или через боковое меню): скачайте офлайн-набор с Dolphin 3.0 + голос Дмитрий HD, поищите GGUF на Hugging Face или импортируйте свой файл."
            persistTurn(guidance, "setup-hint")
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
        onProgress(if (webEnrichedContext != null) "Анализирую найденное в сети…" else "Джарвис думает…")

        val streamSessionId = speaker.beginStreamingSession()
        val speechChunker = if (streamSessionId != null) RussianStreamingSpeechChunker(maxChunkChars = 160) else null
        var streamedRawPrefix = ""
        var streamedSentences = 0

        llamaProvider.onTokenDelta = { partial ->
            _generationWarning.value = null
            onTokenStream(partial)
            if (streamSessionId != null &&
                speechChunker != null &&
                partial.length > streamedRawPrefix.length &&
                partial.startsWith(streamedRawPrefix)
            ) {
                val delta = partial.substring(streamedRawPrefix.length)
                if (!delta.contains("<|")) {
                    streamedRawPrefix = partial
                    for (sentence in speechChunker.append(delta)) {
                        if (speaker.enqueueStreamingSentence(streamSessionId, sentence)) {
                            streamedSentences++
                        }
                    }
                }
            }
        }
        llamaProvider.onStatusUpdate = { statusMsg ->
            onProgress(statusMsg)
        }
        llamaProvider.onStallWarning = { warning ->
            _generationWarning.value = warning
        }

        val effectiveUserText = webEnrichedContext?.promptWithContext ?: commandText
        val turn = KernelTurn(
            taskId = "turn-${System.currentTimeMillis()}",
            userText = effectiveUserText,
            locale = "ru-RU",
            networkAllowed = extCfg.enabled,
            budget = KernelBudget(
                maxOutputTokens = llmModels.generationSettings().maxTokens.coerceIn(128, 1024),
                maxToolProposals = 0,
                timeoutMillis = 45_000,
            ),
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

            persistTurn(result.answer, result.modelId)
            refreshWorkspaceState()
            onTokenStream(result.answer)
            onCommandCompleted?.invoke(text, result.answer)
            if (streamSessionId != null && speechChunker != null) {
                if (streamedSentences > 0) {
                    if (result.answer.startsWith(streamedRawPrefix)) {
                        val tail = result.answer.substring(streamedRawPrefix.length)
                        for (sentence in speechChunker.append(tail) + speechChunker.flush()) {
                            if (speaker.enqueueStreamingSentence(streamSessionId, sentence)) {
                                streamedSentences++
                            }
                        }
                    } else {
                        for (sentence in speechChunker.flush()) {
                            if (speaker.enqueueStreamingSentence(streamSessionId, sentence)) {
                                streamedSentences++
                            }
                        }
                    }
                } else {
                    for (sentence in PiperTtsEngine.splitForTts(result.answer)) {
                        if (speaker.enqueueStreamingSentence(streamSessionId, sentence)) {
                            streamedSentences++
                        }
                    }
                }
                speaker.finishStreamingSession(streamSessionId)
                if (streamedSentences == 0) {
                    onOrbModeChanged?.invoke(JarvisOrbMode.IDLE)
                    if (fromVoice) onSpeakingFinished?.invoke()
                }
            } else if (!speaker.speak(result.answer)) {
                onOrbModeChanged?.invoke(JarvisOrbMode.IDLE)
                if (fromVoice) onSpeakingFinished?.invoke()
            }
            onProgress("Ответ готов (${result.modelId})")
            result.answer
        } catch (cancelled: CancellationException) {
            speechChunker?.reset()
            if (streamSessionId != null) speaker.stop()
            llamaProvider.cancelActiveGeneration()
            throw cancelled
        } catch (error: Throwable) {
            speechChunker?.reset()
            if (streamSessionId != null) speaker.stop()
            val realMsg = error.message ?: error.javaClass.simpleName
            onOrbModeChanged?.invoke(JarvisOrbMode.ERROR)
            val failText = "Ошибка ИИ: $realMsg"
            persistTurn(failText, "error")
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
        if (lower.contains("статус системы") ||
            lower.contains("сколько памяти") ||
            lower.contains("состояние телефона") ||
            lower.contains("диагностик") ||
            lower.contains("заряд батаре")
        ) {
            return deviceDiagnosticsText()
        }

        // Image & Video Generation Studio Voice Triggers (supports wake-word-stripped commands)
        val drawMatch = Regex(
            """^(?:(?:пожалуйста|можешь|сделай|мне|давай)[,\s]*)*(?:нарисуй|нарисовать|сгенерируй\s+(?:картинку|изображение|фото|арт)|создай\s+(?:картинку|изображение|фото|арт)|сделай\s+(?:картинку|фото|арт))\s+(.+)$""",
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
            """^(?:(?:пожалуйста|можешь|давай)[,\s]*)*(?:создай\s+видео|сгенерируй\s+видео|сделай\s+видео|сними\s+видео)\s+(.+)$""",
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

        // Contextual "выключить" right after turning flashlight ON
        if (lastTorchEnabled && JarvisPhoneActions.isStandaloneTurnOffCommand(text)) {
            return phoneActionExecutor.execute(PhoneActionIntent.ToggleFlashlight(enabled = false)).fold(
                onSuccess = { message ->
                    lastTorchEnabled = false
                    message
                },
                onFailure = { err -> "Не удалось выключить фонарик: ${err.message ?: err.javaClass.simpleName}" },
            )
        }

        // Safe Deterministic Phone Actions (Timer, Alarm, Torch, System Settings, Calculator, Safe URL, Share)
        JarvisPhoneActions.parseCommand(text)?.let { phoneAction ->
            return phoneActionExecutor.execute(phoneAction).fold(
                onSuccess = { message ->
                    if (phoneAction is PhoneActionIntent.ToggleFlashlight) {
                        lastTorchEnabled = phoneAction.enabled
                    }
                    message
                },
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

    private data class WebEnrichedContext(
        val promptWithContext: String,
        val directUserAnswer: String,
        val spokenSummary: String,
    )

    private suspend fun fetchWebContextIfRequested(
        text: String,
        onProgress: (String) -> Unit,
    ): WebEnrichedContext? {
        val trimmed = text.trim()
        val lower = trimmed.lowercase(Locale("ru", "RU"))

        // 1. Read explicit https/http URL when user asks to read/summarize it
        val urlMatch = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE).find(trimmed)?.value
        val wantsReadUrl = urlMatch != null && (
            lower.contains("прочит") ||
                lower.contains("перескаж") ||
                lower.contains("разбер") ||
                lower.contains("о чём") ||
                lower.contains("о чем") ||
                lower.contains("суммар") ||
                lower.startsWith("http")
            )
        if (wantsReadUrl && urlMatch != null && !lower.startsWith("открой сайт") && !lower.startsWith("открой ссылк")) {
            onOrbModeChanged?.invoke(JarvisOrbMode.SEARCHING)
            onProgress("Читаю страницу по ссылке…")
            val pageRes = JarvisWebTools.readWebPage(urlMatch)
            return pageRes.fold(
                onSuccess = { page ->
                    val direct = "**${page.title}**\n${page.url}\n\n${page.text.take(1800)}"
                    val prompt = "${trimmed}\n\nСодержимое веб-страницы «${page.title}» (${page.url}):\n${page.text.take(2200)}\n\nОтветь на русском языке, опираясь на текст выше."
                    WebEnrichedContext(
                        promptWithContext = prompt,
                        directUserAnswer = direct,
                        spokenSummary = "Прочитал страницу «${page.title}». Текст выведен на экран.",
                    )
                },
                onFailure = { err ->
                    val msg = "Не удалось прочитать страницу: ${err.message ?: err.javaClass.simpleName}"
                    WebEnrichedContext(
                        promptWithContext = trimmed,
                        directUserAnswer = msg,
                        spokenSummary = msg,
                    )
                },
            )
        }

        // 2. Live Web Search triggers
        val searchRegex = Regex(
            """^(?:(?:джарвис|пожалуйста)[,\s]+)*(?:найди\s+в\s+интернете|поищи\s+в\s+(?:интернете|сети)|загугли|погугли|веб-поиск|поиск\s+в\s+сети|найди)\s*:?\s*(.+)$""",
            RegexOption.IGNORE_CASE,
        )
        val searchMatch = searchRegex.find(trimmed)
        if (searchMatch != null) {
            val query = searchMatch.groupValues[1].trim()
            if (query.length >= 2) {
                onOrbModeChanged?.invoke(JarvisOrbMode.SEARCHING)
                onProgress("Ищу в интернете: $query…")
                val searchRes = JarvisWebTools.searchWeb(query, maxResults = 4)
                return searchRes.fold(
                    onSuccess = { hits ->
                        if (hits.isEmpty()) {
                            val emptyMsg = "Поиск по запросу «$query» не дал результатов."
                            WebEnrichedContext(trimmed, emptyMsg, emptyMsg)
                        } else {
                            val formatted = hits.mapIndexed { idx, h ->
                                buildString {
                                    append("${idx + 1}. ${h.title}\n${h.url}")
                                    if (h.snippet.isNotBlank()) append("\n${h.snippet}")
                                }
                            }.joinToString("\n\n")
                            val prompt = "Запрос пользователя: $trimmed\n\nРезультаты свежего веб-поиска:\n$formatted\n\nДай чёткий, полезный ответ на русском языке, опираясь на найденные факты и указывая источники."
                            val spoken = "Вот что нашлось по запросу «$query»: ${hits.first().title}. Подробности со ссылками на экране."
                            WebEnrichedContext(prompt, formatted, spoken)
                        }
                    },
                    onFailure = { err ->
                        val msg = "Ошибка веб-поиска (проверьте интернет): ${err.message ?: err.javaClass.simpleName}"
                        WebEnrichedContext(trimmed, msg, msg)
                    },
                )
            }
        }
        return null
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
