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
        val dirty = "  Здравствуйте! Чем могу помочь?<|im_end|><|endoftext|><|eot_id|><|end_of_text|></s>  "
        assertEquals("Здравствуйте! Чем могу помочь?", JarvisChatPromptBuilder.sanitizeOutput(dirty))
        assertFalse(JarvisChatPromptBuilder.sanitizeOutput("Ответ<|im_start|>assistant").contains("<|im_start|>"))
        assertFalse(JarvisChatPromptBuilder.sanitizeOutput("Ответ<|start_header_id|>user<|end_header_id|>").contains("<|end_header_id|>"))
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

    @Test
    fun buildsLlama3PromptAndResolvesTemplateAutoDetection() {
        assertEquals(
            PromptTemplateFormat.LLAMA3,
            JarvisChatPromptBuilder.resolveTemplate(
                PromptTemplateFormat.AUTO,
                "l3-8b-stheno-v3.2-q3_k_m",
                "L3-8B-Stheno-v3.2-Q3_K_M.gguf",
                "llama",
            ),
        )
        assertEquals(
            PromptTemplateFormat.CHATML,
            JarvisChatPromptBuilder.resolveTemplate(
                PromptTemplateFormat.AUTO,
                "hermes-3-llama-3.2-3b-q4_k_m",
                "Hermes-3-Llama-3.2-3B.Q4_K_M.gguf",
                "llama",
            ),
        )
        assertEquals(
            PromptTemplateFormat.CHATML,
            JarvisChatPromptBuilder.resolveTemplate(
                PromptTemplateFormat.AUTO,
                "dolphin3.0-qwen2.5-3b-q4_k_m",
                "Dolphin3.0-Qwen2.5-3b-Q4_K_M.gguf",
                "qwen2",
            ),
        )

        val llama3Prompt = JarvisChatPromptBuilder.buildPrompt(
            format = PromptTemplateFormat.LLAMA3,
            userText = "Привет!",
            recentTurns = listOf(
                ChatTurn("user", "Как дела?"),
                ChatTurn("assistant", "Отлично!"),
            ),
            recalledFacts = listOf("OPPO Find X6 Pro 12 ГБ"),
        )
        assertTrue(llama3Prompt.startsWith("<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n"))
        assertTrue(llama3Prompt.contains("OPPO Find X6 Pro 12 ГБ"))
        assertTrue(llama3Prompt.contains("<|eot_id|><|start_header_id|>user<|end_header_id|>\n\nКак дела?<|eot_id|>"))
        assertTrue(llama3Prompt.endsWith("<|start_header_id|>user<|end_header_id|>\n\nПривет!<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n"))
    }
}
