package com.jarvis.breeno.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Small seam that makes asynchronous TTS lifecycle behavior deterministic in unit tests. */
internal interface SpeechEngine {
    fun setLanguage(locale: Locale): Int
    fun speak(text: String): Int
    fun stop()
    fun shutdown()
}

internal fun interface SpeechEngineFactory {
    fun create(onInit: (Int) -> Unit): SpeechEngine
}

/**
 * Lifecycle-safe Russian TTS adapter.
 *
 * TextToSpeech initializes asynchronously. Until initialization and Russian
 * locale selection both succeed, only the newest requested utterance is kept.
 */
class JarvisSpeaker internal constructor(factory: SpeechEngineFactory) {
    constructor(context: Context) : this(
        SpeechEngineFactory { onInit ->
            AndroidSpeechEngine(context.applicationContext, onInit)
        }
    )

    private val lock = Any()
    private var engine: SpeechEngine? = null
    private var pendingInitStatus: Int? = null
    private var initialized = false
    private var initializationFailed = false
    private var closed = false
    private var pendingText: String? = null

    init {
        val created = try {
            factory.create(::onInitialized)
        } catch (_: Throwable) {
            initializationFailed = true
            null
        }

        synchronized(lock) {
            engine = created
            pendingInitStatus?.let { status ->
                pendingInitStatus = null
                created?.let { initializeLocked(it, status) }
            }
        }
    }

    private fun onInitialized(status: Int) {
        synchronized(lock) {
            if (closed) return
            val active = engine
            if (active == null) {
                // A test factory or platform callback may run before create() returns.
                pendingInitStatus = status
                return
            }
            initializeLocked(active, status)
        }
    }

    private fun initializeLocked(active: SpeechEngine, status: Int) {
        if (closed || initializationFailed) return
        if (status != TextToSpeech.SUCCESS) {
            initializationFailed = true
            pendingText = null
            return
        }

        val languageStatus = runCatching {
            active.setLanguage(Locale("ru", "RU"))
        }.getOrElse {
            initializationFailed = true
            pendingText = null
            return
        }

        if (languageStatus < TextToSpeech.LANG_AVAILABLE) {
            // Do not leave future utterances queued forever if Russian voice data is absent.
            initializationFailed = true
            pendingText = null
            return
        }

        initialized = true
        pendingText?.let { speakNow(active, it) }
        pendingText = null
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        synchronized(lock) {
            if (closed || initializationFailed) return
            val active = engine ?: return
            if (!initialized) {
                // Keep only the newest response; stale queued speech is confusing.
                pendingText = text
                return
            }
            speakNow(active, text)
        }
    }

    private fun speakNow(active: SpeechEngine, text: String) {
        if (active.speak(text) == TextToSpeech.ERROR) {
            initializationFailed = true
            pendingText = null
        }
    }

    fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            pendingText = null
            initialized = false
            engine?.let { active ->
                runCatching { active.stop() }
                runCatching { active.shutdown() }
            }
            engine = null
        }
    }
}

private class AndroidSpeechEngine(
    context: Context,
    onInit: (Int) -> Unit
) : SpeechEngine {
    private val tts = TextToSpeech(context) { status -> onInit(status) }

    override fun setLanguage(locale: Locale): Int = tts.setLanguage(locale)

    override fun speak(text: String): Int =
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")

    override fun stop() {
        tts.stop()
    }

    override fun shutdown() {
        tts.shutdown()
    }
}
