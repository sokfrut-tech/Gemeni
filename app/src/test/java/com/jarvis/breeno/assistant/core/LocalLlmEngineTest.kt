package com.jarvis.breeno.assistant.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LocalLlmEngineTest {
    @Test
    fun unavailableModelNeverEchoesPromptAsCompletion() = runBlocking {
        val prompt = "Придумай план поездки"
        val result = LocalLlmEngine().complete(prompt)

        assertNotEquals(prompt, result)
        assertEquals(
            "Локальная нейросетевая модель ещё не подключена. Использую доступные офлайн-команды.",
            result
        )
    }

    @Test
    fun blankPromptIsRejectedClearly() = runBlocking {
        assertEquals("Пустой запрос", LocalLlmEngine().complete("  "))
    }
}
