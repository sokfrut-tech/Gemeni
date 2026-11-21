package com.jarvis.breeno.assistant.core

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

enum class ExternalAiMode {
    OPENAI_COMPATIBLE,
    GEMINI,
    POLLINATIONS_FREE,
}

data class ExternalAiConfig(
    val enabled: Boolean = false,
    val mode: ExternalAiMode = ExternalAiMode.OPENAI_COMPATIBLE,
    val baseUrl: String = "https://api.openai.com/v1/chat/completions",
    val modelName: String = "gpt-4o-mini",
    val apiKey: String = "",
)

/**
 * Real opt-in external LLM client supporting:
 * 1) OpenAI-compatible endpoints (PC Ollama/LM Studio, OpenRouter, OpenAI, DeepSeek)
 * 2) Google Gemini REST API (generativelanguage.googleapis.com)
 * 3) Zero-key smart online AI via Pollinations OpenAI-compatible endpoint
 * Disabled by default; never silently sends user prompts off-device.
 */
class GeminiCloudClient(
    private val configProvider: () -> ExternalAiConfig = { ExternalAiConfig() },
    private val personaProvider: () -> JarvisPersona = { JarvisPersona.STARK_JARVIS },
) {
    suspend fun complete(
        prompt: String,
        recentTurns: List<ChatTurn> = emptyList(),
        recalledFacts: List<String> = emptyList(),
        maxOutputTokens: Int = 512,
    ): String = withContext(Dispatchers.IO) {
        val cfg = configProvider()
        check(cfg.enabled) { "Внешний/облачный ИИ отключён в настройках" }

        when (cfg.mode) {
            ExternalAiMode.OPENAI_COMPATIBLE -> callOpenAiCompatible(cfg, prompt, recentTurns, recalledFacts, maxOutputTokens)
            ExternalAiMode.GEMINI -> callGeminiApi(cfg, prompt, recentTurns, recalledFacts, maxOutputTokens)
            ExternalAiMode.POLLINATIONS_FREE -> callPollinationsFree(prompt, recentTurns, recalledFacts, maxOutputTokens)
        }
    }

    private fun callPollinationsFree(
        prompt: String,
        recentTurns: List<ChatTurn>,
        recalledFacts: List<String>,
        maxOutputTokens: Int,
    ): String {
        val bodyJson = buildOpenAiRequestJson(
            model = "openai",
            userText = prompt,
            recentTurns = recentTurns,
            recalledFacts = recalledFacts,
            maxTokens = maxOutputTokens,
            persona = personaProvider(),
        )
        val responseBody = postJson(POLLINATIONS_TEXT_ENDPOINT, bodyJson, emptyMap())
        return parseOpenAiResponseJson(responseBody)
    }

    private fun callOpenAiCompatible(
        cfg: ExternalAiConfig,
        prompt: String,
        recentTurns: List<ChatTurn>,
        recalledFacts: List<String>,
        maxOutputTokens: Int,
    ): String {
        val urlStr = cfg.baseUrl.trim()
        require(urlStr.startsWith("http://", true) || urlStr.startsWith("https://", true)) {
            "Некорректный URL сервера ИИ"
        }
        val bodyJson = buildOpenAiRequestJson(
            model = cfg.modelName.ifBlank { "qwen2.5:7b" },
            userText = prompt,
            recentTurns = recentTurns,
            recalledFacts = recalledFacts,
            maxTokens = maxOutputTokens,
            persona = personaProvider(),
        )
        val headers = mutableMapOf<String, String>()
        if (cfg.apiKey.isNotBlank()) {
            headers["Authorization"] = "Bearer ${cfg.apiKey.trim()}"
        }
        val responseBody = postJson(urlStr, bodyJson, headers)
        return parseOpenAiResponseJson(responseBody)
    }

    private fun callGeminiApi(
        cfg: ExternalAiConfig,
        prompt: String,
        recentTurns: List<ChatTurn>,
        recalledFacts: List<String>,
        maxOutputTokens: Int,
    ): String {
        val key = cfg.apiKey.trim()
        require(key.isNotBlank()) { "Не указан API-ключ Gemini" }
        val model = cfg.modelName.trim().ifBlank { "gemini-2.0-flash" }
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val bodyJson = buildGeminiRequestJson(
            userText = prompt,
            recentTurns = recentTurns,
            recalledFacts = recalledFacts,
            maxTokens = maxOutputTokens,
            persona = personaProvider(),
        )
        val responseBody = postJson(endpoint, bodyJson, mapOf("x-goog-api-key" to key))
        return parseGeminiResponseJson(responseBody)
    }

    private fun postJson(urlStr: String, body: String, extraHeaders: Map<String, String>): String {
        val connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 8_000
            readTimeout = 20_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code == 402) {
                error("Сервер ИИ требует оплату (HTTP 402). Переключитесь на бесплатный офлайн-мозг Dolphin 3.0 / Hermes 3 во вкладке «Модели» или укажите свой сервер/API-ключ.")
            }
            if (code !in 200..299) {
                error("Сервер ИИ вернул HTTP $code: ${text.take(180)}")
            }
            return text
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val POLLINATIONS_TEXT_ENDPOINT = "https://text.pollinations.ai/openai"
        private val gson = Gson()

        fun buildSystemInstruction(
            recalledFacts: List<String>,
            persona: JarvisPersona = JarvisPersona.STARK_JARVIS,
        ): String {
            val sb = StringBuilder(persona.systemPrompt)
            if (recalledFacts.isNotEmpty()) {
                sb.append("\n\nФакты из памяти пользователя:")
                recalledFacts.forEach { sb.append("\n- ").append(it.trim()) }
            }
            return sb.toString()
        }

        fun buildOpenAiRequestJson(
            model: String,
            userText: String,
            recentTurns: List<ChatTurn> = emptyList(),
            recalledFacts: List<String> = emptyList(),
            maxTokens: Int = 512,
            persona: JarvisPersona = JarvisPersona.STARK_JARVIS,
        ): String {
            val messages = JsonArray()
            messages.add(JsonObject().apply {
                addProperty("role", "system")
                addProperty("content", buildSystemInstruction(recalledFacts, persona))
            })
            recentTurns.takeLast(6).forEach { turn ->
                messages.add(JsonObject().apply {
                    addProperty("role", if (turn.role == "assistant") "assistant" else "user")
                    addProperty("content", turn.content)
                })
            }
            messages.add(JsonObject().apply {
                addProperty("role", "user")
                addProperty("content", userText)
            })
            val root = JsonObject().apply {
                addProperty("model", model)
                add("messages", messages)
                addProperty("temperature", 0.65)
                addProperty("max_tokens", maxTokens)
            }
            return gson.toJson(root)
        }

        fun parseOpenAiResponseJson(json: String): String {
            val root = JsonParser.parseString(json).asJsonObject
            val choices = root.getAsJsonArray("choices")
                ?: error("В ответе OpenAI API отсутствует поле choices")
            if (choices.size() == 0) error("Пустой список ответов от сервера ИИ")
            val message = choices[0].asJsonObject.getAsJsonObject("message")
            return message?.get("content")?.asString?.trim()?.takeIf { it.isNotEmpty() }
                ?: error("Пустой текст ответа от сервера ИИ")
        }

        fun buildGeminiRequestJson(
            userText: String,
            recentTurns: List<ChatTurn> = emptyList(),
            recalledFacts: List<String> = emptyList(),
            maxTokens: Int = 512,
            persona: JarvisPersona = JarvisPersona.STARK_JARVIS,
        ): String {
            val contents = JsonArray()
            recentTurns.takeLast(6).forEach { turn ->
                contents.add(JsonObject().apply {
                    addProperty("role", if (turn.role == "assistant") "model" else "user")
                    add("parts", JsonArray().apply {
                        add(JsonObject().apply { addProperty("text", turn.content) })
                    })
                })
            }
            contents.add(JsonObject().apply {
                addProperty("role", "user")
                add("parts", JsonArray().apply {
                    add(JsonObject().apply { addProperty("text", userText) })
                })
            })
            val root = JsonObject().apply {
                add("systemInstruction", JsonObject().apply {
                    add("parts", JsonArray().apply {
                        add(JsonObject().apply { addProperty("text", buildSystemInstruction(recalledFacts, persona)) })
                    })
                })
                add("contents", contents)
                add("generationConfig", JsonObject().apply {
                    addProperty("temperature", 0.65)
                    addProperty("maxOutputTokens", maxTokens)
                })
            }
            return gson.toJson(root)
        }

        fun parseGeminiResponseJson(json: String): String {
            val root = JsonParser.parseString(json).asJsonObject
            val candidates = root.getAsJsonArray("candidates")
                ?: error("В ответе Gemini API отсутствует candidates")
            if (candidates.size() == 0) error("Gemini вернул пустой список кандидатов")
            val parts = candidates[0].asJsonObject
                .getAsJsonObject("content")
                ?.getAsJsonArray("parts")
                ?: error("В ответе Gemini отсутствует текст")
            val text = (0 until parts.size())
                .mapNotNull { idx -> parts[idx].asJsonObject.get("text")?.asString }
                .joinToString("")
                .trim()
            return text.takeIf { it.isNotEmpty() } ?: error("Gemini вернул пустой ответ")
        }
    }
}
