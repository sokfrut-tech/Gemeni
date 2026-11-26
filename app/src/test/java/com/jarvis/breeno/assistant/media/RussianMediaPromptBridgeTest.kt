package com.jarvis.breeno.assistant.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RussianMediaPromptBridgeTest {
    @Test
    fun stripsRussianCommandPrefixesCleanly() {
        assertEquals(
            "красного дракона в горах",
            RussianMediaPromptBridge.stripRussianMediaPrefix("Джарвис, пожалуйста нарисуй мне картинку красного дракона в горах"),
        )
    }

    @Test
    fun translatesRussianVisualPromptsForOfflineGenerators() {
        val translated = RussianMediaPromptBridge.toVisualPrompt("Нарисуй футуристичный ночной город под дождём")
        assertTrue(translated.contains("city"))
        assertTrue(translated.contains("night"))
        assertTrue(translated.contains("rain"))
        assertFalse(translated.contains("нарисуй", ignoreCase = true))
    }

    @Test
    fun preservesAlreadyEnglishPrompts() {
        val english = "cyberpunk neon street with glowing orb reflections, 8k"
        assertEquals(english, RussianMediaPromptBridge.toVisualPrompt(english))
    }
}
