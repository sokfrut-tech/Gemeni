package com.jarvis.breeno.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Small lifecycle-safe Russian TTS adapter.
 *
 * TextToSpeech initializes asynchronously. Calls made before onInit are queued
 * instead of being silently sent to an uninitialized engine.
 */
class JarvisSpeaker(context: Context) {
    private val lock = Any()
    private var initialized = false
    private var initializationFailed = false
    private var pendingText: String? = null

    private val tts = TextToSpeech(context.applicationContext) { status ->
        synchronized(lock) {
            if (status != TextToSpeech.SUCCESS) {
                initializationFailed = true
                pendingText = null
                return@synchronized
            }

            val languageStatus = tts.setLanguage(Locale("ru", "RU"))
            initialized = languageStatus != TextToSpeech.LANG_MISSING_DATA &&
                languageStatus != TextToSpeech.LANG_NOT_SUPPORTED

            if (initialized) {
                pendingText?.let(::speakNow)
            }
            pendingText = null
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        synchronized(lock) {
            if (initializationFailed) return
            if (!initialized) {
                // Keep only the newest response; stale queued speech is confusing.
                pendingText = text
                return
            }
            speakNow(text)
        }
    }

    private fun speakNow(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    fun close() {
        synchronized(lock) {
            pendingText = null
            initialized = false
            tts.stop()
            tts.shutdown()
        }
    }
}
