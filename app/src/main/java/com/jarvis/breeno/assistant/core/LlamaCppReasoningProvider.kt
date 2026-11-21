package com.jarvis.breeno.assistant.core

import com.jarvis.breeno.assistant.updater.LocalLlmModelManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * Real offline LLM provider backed by pinned llama.cpp JNI and verified Qwen2.5 GGUF packs.
 * Never returns fabricated answers when the native runtime or model weights are absent.
 */
class LlamaCppReasoningProvider(
    private val modelManager: LocalLlmModelManager,
    private val historyProvider: () -> List<ChatTurn> = { emptyList() },
    private val memorySearchProvider: (String) -> List<String> = { emptyList() },
    var onTokenDelta: ((String) -> Unit)? = null,
) : ManagedLocalReasoningProvider {

    override val id: String = "llama-cpp-local"
    override val tier: ReasoningTier = ReasoningTier.LOCAL_PRIMARY

    private val inferenceExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "JarvisLlamaInference").apply { isDaemon = true }
    }
    private val inferenceDispatcher = inferenceExecutor.asCoroutineDispatcher()

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
            return ProviderAvailability.Unavailable("Локальная модель ИИ (Qwen 2.5) ещё не загружена")
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
        val requestedCtx = (turn.budget.maxOutputTokens * 2).coerceIn(1024, 2048)

        return withContext(inferenceDispatcher) {
            try {
                if (loadedHandle == 0L || loadedPackId != pack.id || loadedNCtx != requestedCtx) {
                    if (loadedHandle != 0L) {
                        LlamaNativeBridge.nativeUnload(loadedHandle)
                        loadedHandle = 0L
                        loadedPackId = null
                    }
                    val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
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

                val recentTurns = runCatching { historyProvider() }.getOrDefault(emptyList())
                val recalledFacts = runCatching { memorySearchProvider(turn.userText) }.getOrDefault(emptyList())
                val prompt = JarvisChatPromptBuilder.buildQwenChatMlPrompt(
                    userText = turn.userText,
                    recentTurns = recentTurns,
                    recalledFacts = recalledFacts,
                )

                val streamedBuffer = StringBuilder()
                val rawOutput = LlamaNativeBridge.nativeGenerate(
                    handle = loadedHandle,
                    prompt = prompt,
                    maxTokens = turn.budget.maxOutputTokens.coerceIn(64, 512),
                    temperature = 0.65f,
                    topP = 0.90f,
                    callback = TokenCallback { piece ->
                        streamedBuffer.append(piece)
                        val cleanSoFar = JarvisChatPromptBuilder.sanitizeOutput(streamedBuffer.toString())
                        if (cleanSoFar.isNotEmpty()) {
                            onTokenDelta?.invoke(cleanSoFar)
                        }
                    },
                )

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
