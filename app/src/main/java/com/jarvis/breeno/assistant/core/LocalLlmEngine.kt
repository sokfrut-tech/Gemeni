package com.jarvis.breeno.assistant.core

/**
 * Explicit placeholder until the pinned on-device native inference runtime is integrated.
 *
 * Never echo a prompt and pretend it is a model completion. Callers receive a clear
 * unavailable result and should continue through JarvisModelProvider's deterministic
 * offline fallback. This class performs no network access and loads no model.
 */
class LocalLlmEngine {
    suspend fun complete(prompt: String): String {
        if (prompt.isBlank()) return "Пустой запрос"
        return "Локальная нейросетевая модель ещё не подключена. Использую доступные офлайн-команды."
    }
}
