package com.jarvis.breeno.assistant.voice

import android.speech.tts.TextToSpeech
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisSpeakerTest {
    @Test
    fun keepsOnlyNewestUtteranceUntilRussianInitializationCompletes() {
        val factory = FakeFactory()
        val speaker = JarvisSpeaker(factory)

        speaker.speak("Старый ответ")
        speaker.speak("Новый ответ")

        assertTrue(factory.engine.spoken.isEmpty())
        factory.complete(TextToSpeech.SUCCESS)

        assertEquals(listOf("Новый ответ"), factory.engine.spoken)
        assertEquals(Locale("ru", "RU"), factory.engine.language)
    }

    @Test
    fun handlesInitializationCallbackThatArrivesBeforeFactoryReturns() {
        val factory = FakeFactory(initializeSynchronously = true)
        val speaker = JarvisSpeaker(factory)

        speaker.speak("Проверка")

        assertEquals(listOf("Проверка"), factory.engine.spoken)
        assertEquals(Locale("ru", "RU"), factory.engine.language)
    }

    @Test
    fun unsupportedRussianVoiceFailsClosedInsteadOfQueueingForever() {
        val factory = FakeFactory()
        factory.engine.languageStatus = TextToSpeech.LANG_NOT_SUPPORTED
        val speaker = JarvisSpeaker(factory)

        factory.complete(TextToSpeech.SUCCESS)
        speaker.speak("Ответ без русской озвучки")

        assertTrue(factory.engine.spoken.isEmpty())
    }

    @Test
    fun failedInitializationDiscardsPendingSpeech() {
        val factory = FakeFactory()
        val speaker = JarvisSpeaker(factory)

        speaker.speak("Не произносить")
        factory.complete(TextToSpeech.ERROR)
        speaker.speak("И это тоже не произносить")

        assertTrue(factory.engine.spoken.isEmpty())
    }

    @Test
    fun closeIsIdempotentAndIgnoresLateInitialization() {
        val factory = FakeFactory()
        val speaker = JarvisSpeaker(factory)

        speaker.speak("Отменённый ответ")
        speaker.close()
        speaker.close()
        factory.complete(TextToSpeech.SUCCESS)

        assertTrue(factory.engine.spoken.isEmpty())
        assertEquals(1, factory.engine.stopCalls)
        assertEquals(1, factory.engine.shutdownCalls)
        assertFalse(factory.engine.languageWasSet)
    }

    private class FakeFactory(
        private val initializeSynchronously: Boolean = false
    ) : SpeechEngineFactory {
        val engine = FakeSpeechEngine()
        private var callback: ((Int) -> Unit)? = null

        override fun create(onInit: (Int) -> Unit): SpeechEngine {
            callback = onInit
            if (initializeSynchronously) onInit(TextToSpeech.SUCCESS)
            return engine
        }

        fun complete(status: Int) {
            callback?.invoke(status)
        }
    }

    private class FakeSpeechEngine : SpeechEngine {
        val spoken = mutableListOf<String>()
        var language: Locale? = null
        var languageStatus = TextToSpeech.LANG_AVAILABLE
        var languageWasSet = false
        var stopCalls = 0
        var shutdownCalls = 0

        override fun setLanguage(locale: Locale): Int {
            language = locale
            languageWasSet = true
            return languageStatus
        }

        override fun speak(text: String): Int {
            spoken += text
            return TextToSpeech.SUCCESS
        }

        override fun stop() {
            stopCalls += 1
        }

        override fun shutdown() {
            shutdownCalls += 1
        }
    }
}
