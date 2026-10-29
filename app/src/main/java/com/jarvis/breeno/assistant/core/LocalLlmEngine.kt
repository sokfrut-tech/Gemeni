package com.jarvis.breeno.assistant.core

/**
 * Заглушка локальной языковой модели. Модель пока не поставляется и не загружается,
 * поэтому честно сообщает об этом и ничего не выдумывает.
 * Реальный движок (llama.cpp) заменит эту реализацию, интерфейс LanguageModel не меняется.
 */
class LocalLlmEngine : LanguageModel {
    override val isInstalled: Boolean = false

    override fun complete(prompt: String): String = NOT_INSTALLED

    companion object {
        const val NOT_INSTALLED = "Языковая модель не установлена"
    }
}
