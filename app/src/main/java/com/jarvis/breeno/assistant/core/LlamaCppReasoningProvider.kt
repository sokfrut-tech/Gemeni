package com.jarvis.breeno.assistant.core

import com.jarvis.breeno.assistant.updater.LocalLlmModelManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Real offline LLM provider backed by pinned llama.cpp JNI and verified GGUF packs.
 * Includes a non-destructive 45s generation stall watchdog and local document RAG injection.
 */
class LlamaCppReasoningProvider(
    private val modelManager: LocalLlmModelManager,
    private val historyProvider: () -> List<ChatTurn> = { emptyList() },
    private val memorySearchProvider: (String) -> List<String> = { emptyList() },
    private val documentSearchProvider: (String) -> List<String> = { emptyList() },
    var onTokenDelta: ((String) -> Unit)? = null,
    var onStatusUpdate: ((String) -> Unit)? = null,
    var onStallWarning: ((String?) -> Unit)? = null,
    private val stallWarnDelayMs: Long = 45_000L,
) : ManagedLocalReasoningProvider {

    override val id: String = "llama-cpp-local"
    override val tier: ReasoningTier = ReasoningTier.LOCAL_PRIMARY

    private val inferenceExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "JarvisLlamaInference").apply { isDaemon = true }
    }
    private val inferenceDispatcher = inferenceExecutor.asCoroutineDispatcher()
    private val watchdogSupervisor = SupervisorJob()
    private val watchdogScope = CoroutineScope(watchdogSupervisor + Dispatchers.Default)

    @Volatile private var loadedHandle: Long = 0L
    @Volatile private var loadedPackId: String? = null
    @Volatile private var loadedNCtx: Int = 0

    override suspend fun availability(): ProviderAvailability {
        if (!LlamaNativeBridge.isNativeLibraryLoaded) {
            return ProviderAvailability.Unavailable(
                "Нативный движок llama.cpp недоступен (${LlamaNativeBridge.loadError ?: "ABI не поддерживается"})"
            )
        }
        if (modelManager.activeReadyPack() == null) {
            return ProviderAvailability.Unavailable("Локальная модель ИИ (Dolphin / Hermes / Qwen) ещё не загружена")
        }
        return ProviderAvailability.Ready
    }

    override suspend fun reason(turn: KernelTurn): ProviderTurnResult {
        check(LlamaNativeBridge.isNativeLibraryLoaded) {
            "Нативный движок llama.cpp не загружен"
        }
        val pack = modelManager.activeReadyPack()
            ?: error("Нет готовой локальной модели GGUF")
        val verifiedFile = modelManager.verifyBeforeLoad(pack)
        val isLarge8B = pack.sizeBytes >= 3_500_000_000L
        val requestedCtx = if (isLarge8B) 1024 else (turn.budget.maxOutputTokens * 2).coerceIn(1024, 2048)

        return withContext(inferenceDispatcher) {
            try {
                if (loadedHandle == 0L || loadedPackId != pack.id || loadedNCtx != requestedCtx) {
                    if (loadedHandle != 0L) {
                        LlamaNativeBridge.nativeUnload(loadedHandle)
                        loadedHandle = 0L
                        loadedPackId = null
                    }
                    val maxThreads = if (isLarge8B) 4 else 6
                    val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, maxThreads)
                    onStatusUpdate?.invoke("Загружаю ${pack.title} в память…")
                    val handle = LlamaNativeBridge.nativeLoadModel(
                        modelPath = verifiedFile.absolutePath,
                        nCtx = requestedCtx,
                        nThreads = threads,
                    )
                    check(handle != 0L) {
                        "Не удалось загрузить модель ${pack.title} в память (возможно, нехватка RAM)"
                    }
                    loadedHandle = handle
                    loadedPackId = pack.id
                    loadedNCtx = requestedCtx
                }

                onStatusUpdate?.invoke("Джарвис генерирует ответ…")
                onStallWarning?.invoke(null)
                val recentTurns = runCatching { historyProvider() }.getOrDefault(emptyList())
                val recalledFacts = runCatching { memorySearchProvider(turn.userText) }.getOrDefault(emptyList())
                val documentSnippets = runCatching { documentSearchProvider(turn.userText) }.getOrDefault(emptyList())
                val template = JarvisChatPromptBuilder.resolveTemplate(
                    selected = modelManager.selectedPromptTemplate(),
                    packId = pack.id,
                    fileName = pack.fileName,
                    architecture = pack.descriptor.architecture,
                )
                val prompt = JarvisChatPromptBuilder.buildPrompt(
                    format = template,
                    userText = turn.userText,
                    recentTurns = recentTurns,
                    recalledFacts = recalledFacts,
                    persona = modelManager.selectedPersona(),
                    documentSnippets = documentSnippets,
                )

                val streamedBuffer = StringBuilder()
                val lastTokenAtMs = AtomicLong(System.currentTimeMillis())
                val warnedAboutStall = AtomicBoolean(false)
                val watchdogJob = watchdogScope.launch {
                    val thresholdMs = stallWarnDelayMs.coerceAtLeast(10_000L)
                    while (isActive) {
                        delay(5_000L)
                        val silentMs = System.currentTimeMillis() - lastTokenAtMs.get()
                        if (silentMs >= thresholdMs && warnedAboutStall.compareAndSet(false, true)) {
                            onStallWarning?.invoke("Модель долго обдумывает запрос (>45 с). Можно подождать или нажать «Остановить».")
                        }
                    }
                }
                val rawOutput = try {
                    LlamaNativeBridge.nativeGenerate(
                        handle = loadedHandle,
                        prompt = prompt,
                        maxTokens = turn.budget.maxOutputTokens.coerceIn(64, 512),
                        temperature = 0.65f,
                        topP = 0.90f,
                        callback = TokenCallback { piece ->
                            lastTokenAtMs.set(System.currentTimeMillis())
                            if (warnedAboutStall.compareAndSet(true, false)) {
                                onStallWarning?.invoke(null)
                            }
                            streamedBuffer.append(piece)
                            val cleanSoFar = JarvisChatPromptBuilder.sanitizeOutput(streamedBuffer.toString())
                            if (cleanSoFar.isNotEmpty()) {
                                onTokenDelta?.invoke(cleanSoFar)
                            }
                        },
                    )
                } finally {
                    watchdogJob.cancel()
                    onStallWarning?.invoke(null)
                }

                val cleanAnswer = JarvisChatPromptBuilder.sanitizeOutput(rawOutput)
                check(cleanAnswer.isNotBlank()) {
                    "Локальная модель не вернула ответ"
                }
                ProviderTurnResult(
                    answer = cleanAnswer,
                    modelId = pack.id,
                )
            } catch (cancelled: CancellationException) {
                cancelActiveGeneration()
                throw cancelled
            }
        }
    }

    fun cancelActiveGeneration() {
        val handle = loadedHandle
        if (handle != 0L && LlamaNativeBridge.isNativeLibraryLoaded) {
            LlamaNativeBridge.nativeCancel(handle)
        }
    }

    override suspend fun unload() {
        cancelActiveGeneration()
        withContext(inferenceDispatcher) {
            val handle = loadedHandle
            if (handle != 0L && LlamaNativeBridge.isNativeLibraryLoaded) {
                LlamaNativeBridge.nativeUnload(handle)
            }
            loadedHandle = 0L
            loadedPackId = null
            loadedNCtx = 0
        }
    }

    fun close() {
        watchdogSupervisor.cancel()
        val handle = loadedHandle
        if (handle != 0L && LlamaNativeBridge.isNativeLibraryLoaded) {
            LlamaNativeBridge.nativeCancel(handle)
            LlamaNativeBridge.nativeUnload(handle)
        }
        loadedHandle = 0L
        loadedPackId = null
        inferenceExecutor.shutdownNow()
    }
}
