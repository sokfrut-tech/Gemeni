package com.jarvis.breeno.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Android TTS bridge with real start/done events for the Jarvis core animation. */
class JarvisSpeaker(context: Context) {
    private val appContext = context.applicationContext
    @Volatile private var initialized = false
    @Volatile private var pendingText: String? = null
    @Volatile var onSpeakingChanged: ((Boolean) -> Unit)? = null

    private val tts = TextToSpeech(appContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            val languageResult = ttsEngine?.setLanguage(Locale("ru", "RU")) ?: TextToSpeech.LANG_NOT_SUPPORTED
            initialized = languageResult >= TextToSpeech.LANG_AVAILABLE
            val queued = pendingText
            pendingText = null
            if (initialized && !queued.isNullOrBlank()) speak(queued)
        } else {
            pendingText = null
            onSpeakingChanged?.invoke(false)
        }
    }

    // Kept separate to avoid touching the lateinit TextToSpeech reference during construction.
    private val ttsEngine: TextToSpeech? get() = tts

    fun speak(text: String) {
        if (text.isBlank()) return
        if (!initialized) {
            pendingText = text
            return
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                onSpeakingChanged?.invoke(true)
            }
            override fun onDone(utteranceId: String?) {
                onSpeakingChanged?.invoke(false)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onSpeakingChanged?.invoke(false)
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                onSpeakingChanged?.invoke(false)
            }
        })
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
        if (result == TextToSpeech.ERROR) onSpeakingChanged?.invoke(false)
    }

    fun close() {
        pendingText = null
        onSpeakingChanged?.invoke(false)
        tts.stop()
        tts.shutdown()
        initialized = false
    }
}
