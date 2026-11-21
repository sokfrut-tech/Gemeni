package com.jarvis.breeno.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisChatPromptBuilderTest {
    @Test
    fun buildsValidChatMlWithMemoryAndHistory() {
        val prompt = JarvisChatPromptBuilder.buildQwenChatMlPrompt(
            userText = "Какая у меня машина?",
            recentTurns = listOf(
                ChatTurn("user", "Привет"),
                ChatTurn("assistant", "Привет! Чем помочь?"),
            ),
            recalledFacts = listOf("Пользователь ездит на Li Xiang L7"),
            systemInfoNote = "OPPO Find X6 Pro",
        )

        assertTrue(prompt.startsWith("<|im_start|>system\n"))
        assertTrue(prompt.contains("Факты из памяти пользователя:\n- Пользователь ездит на Li Xiang L7"))
        assertTrue(prompt.contains("Контекст устройства: OPPO Find X6 Pro"))
        assertTrue(prompt.contains("<|im_start|>user\nПривет<|im_end|>"))
        assertTrue(prompt.contains("<|im_start|>assistant\nПривет! Чем помочь?<|im_end|>"))
        assertTrue(prompt.endsWith("<|im_start|>user\nКакая у меня машина?<|im_end|>\n<|im_start|>assistant\n"))
    }

    @Test
    fun sanitizesStopTokensFromModelOutput() {
        val dirty = "  Здравствуйте! Чем могу помочь?<|im_end|><|endoftext|><|eot_id|></s>  "
        assertEquals("Здравствуйте! Чем могу помочь?", JarvisChatPromptBuilder.sanitizeOutput(dirty))
        assertFalse(JarvisChatPromptBuilder.sanitizeOutput("Ответ<|im_start|>assistant").contains("<|im_start|>"))
        assertFalse(JarvisChatPromptBuilder.sanitizeOutput("Ответ<|start_header_id|>user").contains("<|start_header_id|>"))
    }

    @Test
    fun appliesSelectedPersonaSystemPrompt() {
        val prompt = JarvisChatPromptBuilder.buildQwenChatMlPrompt(
            userText = "Объясни квантовую запутанность",
            persona = JarvisPersona.DIRECT_ASSISTANT,
        )
        assertTrue(prompt.contains("Dolphin / Hermes") || prompt.contains("без нравоучений"))
        assertFalse(prompt.contains("Тони Старка"))
    }
}
