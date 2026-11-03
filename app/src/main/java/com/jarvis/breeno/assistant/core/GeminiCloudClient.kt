package com.jarvis.breeno.assistant.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Облачный ответ через официальный Gemini API (generateContent).
 * Ключ пользователя берётся из [apiKeyProvider] и никогда не записывается в репозиторий.
 */
class GeminiCloudClient(
    private val apiKeyProvider: () -> String = { "" },
    private val model: String = DEFAULT_MODEL,
) {
    suspend fun complete(prompt: String): String {
        val key = apiKeyProvider().trim()
        if (key.isEmpty()) return "Ключ Gemini не задан. Добавьте его в настройках."
        return withContext(Dispatchers.IO) { request(key, prompt) }
    }

    private fun request(key: String, prompt: String): String {
        val connection = (URL(ENDPOINT.format(model)).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", key)
        }
        try {
            connection.outputStream.use { it.write(buildBody(prompt).toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) return "Ошибка Gemini ($code). Проверьте ключ и лимиты."
            return parseText(text) ?: "Gemini вернул пустой ответ."
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash"
        private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent"

        /** Тело запроса: один текстовый запрос пользователя. */
        fun buildBody(prompt: String): String {
            val part = JSONObject().put("text", prompt)
            val content = JSONObject().put("parts", JSONArray().put(part))
            return JSONObject().put("contents", JSONArray().put(content)).toString()
        }

        /** Достаёт текст первого кандидата из ответа; null, если текста нет. */
        fun parseText(json: String): String? {
            val candidates = JSONObject(json).optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null
            val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts") ?: return null
            val text = StringBuilder()
            for (i in 0 until parts.length()) text.append(parts.getJSONObject(i).optString("text"))
            return text.toString().takeIf { it.isNotBlank() }
        }
    }
}
