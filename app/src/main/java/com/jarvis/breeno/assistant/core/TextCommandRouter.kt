package com.jarvis.breeno.assistant.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Разбирает текстовую команду и выполняет действие на телефоне.
 * Используется каналом AIDL (submitText). Голосовой путь (JarvisRuntimeController) не меняется.
 * Возвращает короткий ответ для показа или озвучки.
 */
class TextCommandRouter(
    private val context: Context,
    private val languageModel: LanguageModel = LocalLlmEngine(),
) {

    companion object {
        /** Известные приложения: название → пакет. Пакеты перечислены в <queries> манифеста. */
        val APPS = mapOf(
            "яндекс карты" to "ru.yandex.yandexmaps",
            "яндекс навигатор" to "ru.yandex.yandexnavi",
            "телеграм" to "org.telegram.messenger",
            "telegram" to "org.telegram.messenger",
            "хром" to "com.android.chrome",
            "chrome" to "com.android.chrome",
            "гугл карты" to "com.google.android.apps.maps",
            "ютуб" to "com.google.android.youtube",
            "youtube" to "com.google.android.youtube",
            "ватсап" to "com.whatsapp",
            "whatsapp" to "com.whatsapp"
        )
    }


    fun handle(text: String): String {
        val t = text.trim()
        if (t.isEmpty()) return "Скажите или напишите команду"

        val openApp = Regex("^открой\\s+(.+)$", RegexOption.IGNORE_CASE).find(t)
        if (openApp != null) {
            val name = openApp.groupValues[1].trim().lowercase(Locale.getDefault())
            val pkg = APPS[name] ?: return "Не знаю приложение: $name"
            val launch = context.packageManager.getLaunchIntentForPackage(pkg)
                ?: return "Приложение не установлено: $name"
            return open(launch, "Открываю: $name", "Не удалось открыть: $name")
        }
        val search = Regex("^(найди|поиск)\\s*(.*)", RegexOption.IGNORE_CASE).find(t)
        if (search != null) {
            val query = search.groupValues[2].trim()
            if (query.isEmpty()) return "Что найти?"
            return open(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")),
                "Открываю поиск: $query"
            )
        }
        if (t.contains("навига", true) || t.contains("маршрут", true)) {
            return open(
                Intent(Intent.ACTION_VIEW, Uri.parse("yandexnavi://build_route_on_map")),
                "Открываю Яндекс Навигатор",
                "Навигатор не установлен"
            )
        }
        if (t.contains("позвони", true) || t.contains("телефон", true)) {
            return open(Intent(Intent.ACTION_DIAL), "Открываю телефон")
        }
        if (t.contains("время", true)) {
            return "Сейчас " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        }
        // Команда не распознана: спрашиваем языковую модель (сейчас она не установлена).
        return languageModel.complete(t)
    }

    private fun open(intent: Intent, ok: String, fail: String = "Не удалось открыть"): String {
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ok
        } catch (e: Exception) {
            fail
        }
    }
}
