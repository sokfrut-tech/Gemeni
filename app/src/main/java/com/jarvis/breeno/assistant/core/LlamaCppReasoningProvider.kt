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
    @Volatile private var loadedThreads: Int = 0
    @Volatile var lastSpeedLabel: String? = null
        private set

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
        val genSettings = modelManager.generationSettings()
        val isLarge8B = pack.sizeBytes >= 3_500_000_000L
        val isMedium3B = pack.sizeBytes >= 1_500_000_000L
        val maxSafeCtx = when {
            isLarge8B -> 1536
            isMedium3B -> 2048
            else -> 3072
        }
        val requestedCtx = minOf(genSettings.contextLength, maxSafeCtx).coerceAtLeast(512)
        // Cap at 4 threads on Android big.LITTLE CPUs (Snapdragon performance cores) to prevent
        // efficiency-core thread-barrier stalls on Turn 2+ prefills.
        val requestedThreads = minOf(genSettings.cpuThreads, Runtime.getRuntime().availableProcessors()).coerceIn(2, 4)

        return withContext(inferenceDispatcher) {
            try {
                if (loadedHandle == 0L || loadedPackId != pack.id || loadedNCtx != requestedCtx || loadedThreads != requestedThreads) {
                    if (loadedHandle != 0L) {
                        LlamaNativeBridge.nativeUnload(loadedHandle)
                        loadedHandle = 0L
                        loadedPackId = null
                    }
                    onStatusUpdate?.invoke("Загружаю ${pack.title} в память…")
                    val handle = LlamaNativeBridge.nativeLoadModel(
                        modelPath = verifiedFile.absolutePath,
                        nCtx = requestedCtx,
                        nThreads = requestedThreads,
                    )
                    check(handle != 0L) {
                        "Не удалось загрузить модель ${pack.title} в память (возможно, нехватка RAM)"
                    }
                    loadedHandle = handle
                    loadedPackId = pack.id
                    loadedNCtx = requestedCtx
                    loadedThreads = requestedThreads
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
                    customSystemPrompt = genSettings.customSystemPrompt,
                )

                val streamedBuffer = StringBuilder()
                var tokenCallbacksCount = 0
                val genStartMs = System.currentTimeMillis()
                val lastTokenAtMs = AtomicLong(genStartMs)
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
                        maxTokens = maxOf(genSettings.maxTokens, turn.budget.maxOutputTokens).coerceIn(128, 1024),
                        temperature = genSettings.temperature,
                        topP = genSettings.topP,
                        callback = TokenCallback { piece ->
                            tokenCallbacksCount++
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

                val elapsedSec = ((System.currentTimeMillis() - genStartMs) / 1000.0).coerceAtLeast(0.1)
                lastSpeedLabel = if (tokenCallbacksCount > 2) {
                    val tps = tokenCallbacksCount / elapsedSec
                    String.format(java.util.Locale.US, "%.1f т/с • %.1f с", tps, elapsedSec)
                } else {
                    String.format(java.util.Locale.US, "%.1f с", elapsedSec)
                }

                val cleanAnswer = JarvisChatPromptBuilder.sanitizeOutput(rawOutput)
                if (cleanAnswer.isBlank()) {
                    // Reset context handle if native decode returned empty so next turn starts fresh.
                    if (loadedHandle != 0L) {
                        LlamaNativeBridge.nativeUnload(loadedHandle)
                        loadedHandle = 0L
                        loadedPackId = null
                    }
                    error("Локальная модель не вернула ответ")
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
            loadedThreads = 0
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
