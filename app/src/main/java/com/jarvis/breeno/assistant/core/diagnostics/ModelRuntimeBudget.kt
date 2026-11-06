package com.jarvis.breeno.assistant.core.diagnostics

/**
 * Conservative preflight hint based on system-available RAM and free storage.
 * It is not a performance guarantee. Native model runtimes do not share the
 * Java app heap, so appHeapLimit must not be used as a proxy for model memory.
 * Real selection must additionally account for model size, KV cache, context,
 * native allocations, thermal behavior and measured latency.
 */
object ModelRuntimeBudget {
    enum class Tier {
        NOT_RECOMMENDED,
        SMALL_MODEL_ONLY,
        MEDIUM_MODEL_CANDIDATE
    }

    fun evaluate(availableRamMb: Long, freeStorageMb: Long): Tier = when {
        availableRamMb < MIN_AVAILABLE_RAM_MB || freeStorageMb < MIN_SMALL_MODEL_STORAGE_MB ->
            Tier.NOT_RECOMMENDED
        availableRamMb >= MEDIUM_AVAILABLE_RAM_MB && freeStorageMb >= MEDIUM_MODEL_STORAGE_MB ->
            Tier.MEDIUM_MODEL_CANDIDATE
        else -> Tier.SMALL_MODEL_ONLY
    }

    private const val MIN_AVAILABLE_RAM_MB = 1024L
    private const val MIN_SMALL_MODEL_STORAGE_MB = 1024L
    private const val MEDIUM_AVAILABLE_RAM_MB = 3072L
    private const val MEDIUM_MODEL_STORAGE_MB = 4096L
}
