package com.jarvis.breeno.assistant.core

/**
 * Языковая модель для вопросов, которые не распознала обычная команда.
 * Сейчас реализации нет (см. LocalLlmEngine). Позже сюда подключается локальный движок
 * без изменения экрана и маршрутизатора.
 */
interface LanguageModel {
    /** true, когда модель установлена и готова отвечать. */
    val isInstalled: Boolean

    /** Возвращает ответ на текст пользователя. Вызывать не на главном потоке. */
    fun complete(prompt: String): String
}
