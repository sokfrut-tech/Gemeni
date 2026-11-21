package com.jarvis.breeno.assistant.core

import android.content.Context

class ExternalAiConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_external_ai", Context.MODE_PRIVATE)

    fun load(): ExternalAiConfig {
        val modeName = prefs.getString(KEY_MODE, ExternalAiMode.OPENAI_COMPATIBLE.name)
        val mode = runCatching { ExternalAiMode.valueOf(modeName ?: "") }
            .getOrDefault(ExternalAiMode.OPENAI_COMPATIBLE)
        return ExternalAiConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            mode = mode,
            baseUrl = prefs.getString(KEY_BASE_URL, "https://api.openai.com/v1/chat/completions")
                ?: "https://api.openai.com/v1/chat/completions",
            modelName = prefs.getString(KEY_MODEL, "gpt-4o-mini") ?: "gpt-4o-mini",
            apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
        )
    }

    fun save(config: ExternalAiConfig) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, config.enabled)
            .putString(KEY_MODE, config.mode.name)
            .putString(KEY_BASE_URL, config.baseUrl.trim())
            .putString(KEY_MODEL, config.modelName.trim())
            .putString(KEY_API_KEY, config.apiKey.trim())
            .apply()
    }

    companion object {
        private const val KEY_ENABLED = "ext_ai_enabled"
        private const val KEY_MODE = "ext_ai_mode"
        private const val KEY_BASE_URL = "ext_ai_base_url"
        private const val KEY_MODEL = "ext_ai_model"
        private const val KEY_API_KEY = "ext_ai_key"
    }
}

/**
 * Opt-in external/cloud AI provider (PC Ollama, OpenAI-compatible API, or Google Gemini).
 * Disabled by default; JarvisCognitiveKernel enforces turn.networkAllowed as well.
 */
class AuthorizedCloudReasoningProvider(
    private val configProvider: () -> ExternalAiConfig,
    private val historyProvider: () -> List<ChatTurn> = { emptyList() },
    private val memorySearchProvider: (String) -> List<String> = { emptyList() },
    private val client: GeminiCloudClient = GeminiCloudClient(configProvider),
) : JarvisReasoningProvider {

    override val id: String = "authorized-cloud-or-pc"
    override val tier: ReasoningTier = ReasoningTier.AUTHORIZED_CLOUD

    override suspend fun availability(): ProviderAvailability {
        val cfg = configProvider()
        if (!cfg.enabled) {
            return ProviderAvailability.Unavailable("Внешний ИИ отключён")
        }
        if (cfg.mode == ExternalAiMode.GEMINI && cfg.apiKey.isBlank()) {
            return ProviderAvailability.Unavailable("Не задан API-ключ Gemini")
        }
        if (cfg.mode == ExternalAiMode.OPENAI_COMPATIBLE && cfg.baseUrl.isBlank()) {
            return ProviderAvailability.Unavailable("Не задан URL сервера ИИ")
        }
        return ProviderAvailability.Ready
    }

    override suspend fun reason(turn: KernelTurn): ProviderTurnResult {
        val cfg = configProvider()
        val answer = client.complete(
            prompt = turn.userText,
            recentTurns = runCatching { historyProvider() }.getOrDefault(emptyList()),
            recalledFacts = runCatching { memorySearchProvider(turn.userText) }.getOrDefault(emptyList()),
            maxOutputTokens = turn.budget.maxOutputTokens,
        )
        return ProviderTurnResult(
            answer = answer,
            modelId = "${cfg.mode.name.lowercase()}:${cfg.modelName.ifBlank { "default" }}",
        )
    }
}
