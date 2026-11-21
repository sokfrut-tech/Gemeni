package com.jarvis.breeno.assistant.core

/**
 * Facade over the real llama.cpp local reasoning provider.
 * Reports an explicit unavailable state rather than echoing the prompt when no model is loaded.
 */
class LocalLlmEngine(
    private val provider: ManagedLocalReasoningProvider? = null,
) {
    suspend fun isReady(): Boolean = provider?.availability() == ProviderAvailability.Ready

    suspend fun complete(prompt: String): String {
        val target = provider
            ?: error("Локальный LLM не настроен. Откройте вкладку «Модели» и скачайте Qwen 2.5.")
        return when (val status = target.availability()) {
            is ProviderAvailability.Unavailable -> error(status.reason)
            ProviderAvailability.Ready -> target.reason(
                KernelTurn(taskId = "local-llm-direct", userText = prompt)
            ).answer
        }
    }
}
