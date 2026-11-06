package com.jarvis.breeno.assistant.core.diagnostics

/**
 * Conservative preflight hint, not a performance guarantee. Real model selection
 * must additionally account for model file size, KV cache, context length and measured latency.
 */
object ModelRuntimeBudget {
    enum class Tier {
        NOT_RECOMMENDED,
        SMALL_MODEL_ONLY,
        MEDIUM_MODEL_CANDIDATE
    }

    fun evaluate(appHeapLimitMb: Long, freeStorageMb: Long): Tier = when {
        appHeapLimitMb < MIN_HEAP_MB || freeStorageMb < MIN_SMALL_MODEL_STORAGE_MB ->
            Tier.NOT_RECOMMENDED
        appHeapLimitMb >= MEDIUM_HEAP_MB && freeStorageMb >= MEDIUM_MODEL_STORAGE_MB ->
            Tier.MEDIUM_MODEL_CANDIDATE
        else -> Tier.SMALL_MODEL_ONLY
    }

    private const val MIN_HEAP_MB = 768L
    private const val MIN_SMALL_MODEL_STORAGE_MB = 1024L
    private const val MEDIUM_HEAP_MB = 2048L
    private const val MEDIUM_MODEL_STORAGE_MB = 4096L
}
