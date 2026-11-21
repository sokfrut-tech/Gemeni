package com.jarvis.breeno.assistant.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorizedCloudProviderTest {
    @Test
    fun disabledByDefaultReportsUnavailable() = runBlocking {
        val provider = AuthorizedCloudReasoningProvider(configProvider = { ExternalAiConfig(enabled = false) })
        val status = provider.availability()
        assertTrue(status is ProviderAvailability.Unavailable)
    }

    @Test
    fun buildsAndParsesOpenAiCompatibleJson() {
        val req = GeminiCloudClient.buildOpenAiRequestJson(
            model = "qwen2.5:7b",
            userText = "Расскажи о себе",
            recentTurns = listOf(ChatTurn("user", "Привет")),
            recalledFacts = listOf("Имя пользователя — Андрей"),
            maxTokens = 256,
        )
        assertTrue(req.contains("qwen2.5:7b"))
        assertTrue(req.contains("Имя пользователя — Андрей"))
        assertTrue(req.contains("Расскажи о себе"))

        val fakeResp = """
            {"choices":[{"message":{"role":"assistant","content":"  Я Джарвис, готов помочь!  "}}]}
        """.trimIndent()
        assertEquals("Я Джарвис, готов помочь!", GeminiCloudClient.parseOpenAiResponseJson(fakeResp))
    }

    @Test
    fun buildsAndParsesGeminiJson() {
        val req = GeminiCloudClient.buildGeminiRequestJson(
            userText = "Сколько планет в Солнечной системе?",
            maxTokens = 128,
        )
        assertTrue(req.contains("systemInstruction"))
        assertTrue(req.contains("Сколько планет в Солнечной системе?"))

        val fakeResp = """
            {"candidates":[{"content":{"parts":[{"text":"В Солнечной системе восемь планет."}]}}]}
        """.trimIndent()
        assertEquals("В Солнечной системе восемь планет.", GeminiCloudClient.parseGeminiResponseJson(fakeResp))
    }
}
