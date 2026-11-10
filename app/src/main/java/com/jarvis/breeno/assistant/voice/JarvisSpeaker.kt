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
    @Volatile private var engine: TextToSpeech? = null

    init {
        engine = TextToSpeech(appContext) { status ->
            val current = engine
            if (status == TextToSpeech.SUCCESS && current != null) {
                val languageResult = current.setLanguage(Locale("ru", "RU"))
                initialized = languageResult >= TextToSpeech.LANG_AVAILABLE
                val queued = pendingText
                pendingText = null
                if (initialized && !queued.isNullOrBlank()) speak(queued)
            } else {
                pendingText = null
                onSpeakingChanged?.invoke(false)
            }
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        val current = engine ?: return
        if (!initialized) {
            pendingText = text
            return
        }
        current.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
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
        if (current.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis") == TextToSpeech.ERROR) {
            onSpeakingChanged?.invoke(false)
        }
    }

    fun close() {
        pendingText = null
        onSpeakingChanged?.invoke(false)
        engine?.stop()
        engine?.shutdown()
        engine = null
        initialized = false
    }
}
