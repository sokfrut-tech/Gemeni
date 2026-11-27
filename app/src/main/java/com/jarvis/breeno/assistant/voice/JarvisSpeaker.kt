package com.jarvis.breeno.assistant.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Dual-backend Russian voice output:
 * 1) Primary: Offline neural Piper TTS via Sherpa-ONNX + AudioTrack (works on Chinese ColorOS).
 * 2) Fallback: Android system TextToSpeech when Russian voice data is installed.
 */
class JarvisSpeaker(
    context: Context,
    private val speechSpeedProvider: () -> Float = { 0.85f },
    private val piperModelDirProvider: () -> File? = { null },
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val ttsDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "JarvisPiperTts").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + ttsDispatcher)
    private val piperEngine = PiperTtsEngine()

    @Volatile private var systemTtsInitialized = false
    @Volatile private var pendingSystemText: String? = null
    @Volatile var onSpeakingChanged: ((Boolean) -> Unit)? = null
    @Volatile var onError: ((String) -> Unit)? = null
    @Volatile var isMuted: Boolean = false
    @Volatile private var systemEngine: TextToSpeech? = null
    @Volatile private var activeSpeakJob: Job? = null
    @Volatile private var activeTrack: AudioTrack? = null
    @Volatile private var streamSessionSeq: Long = 0L
    @Volatile private var activeStreamSessionId: Long = 0L
    @Volatile private var activeStreamChannel: Channel<String>? = null

    init {
        systemEngine = runCatching {
            TextToSpeech(appContext) { status ->
                val current = systemEngine
                if (status == TextToSpeech.SUCCESS && current != null) {
                    val languageResult = current.setLanguage(Locale("ru", "RU"))
                    systemTtsInitialized = languageResult >= TextToSpeech.LANG_AVAILABLE
                    val queued = pendingSystemText
                    pendingSystemText = null
                    if (systemTtsInitialized && !queued.isNullOrBlank() && piperModelDirProvider() == null) {
                        speakWithSystemTts(queued)
                    }
                } else {
                    pendingSystemText = null
                    systemTtsInitialized = false
                }
            }
        }.getOrNull()
    }

    fun activeEngineLabel(): String = when {
        piperModelDirProvider() != null -> "Piper RU (офлайн)"
        systemTtsInitialized -> "Android TTS (RU)"
        else -> "Нет русского голоса — скачайте Дмитрий HD (~64 МБ)"
    }

    /** Starts voice output; returns true if playback was queued/started, false otherwise. */
    fun speak(text: String): Boolean {
        if (text.isBlank() || isMuted) return false
        stop()

        val piperDir = runCatching { piperModelDirProvider() }.getOrNull()
        return if (piperDir != null) {
            speakWithPiper(piperDir, text)
            true
        } else if (systemTtsInitialized) {
            speakWithSystemTts(text)
            true
        } else {
            onError?.invoke("Русский голос не установлен: откройте вкладку «Модели» и скачайте «Дмитрий HD» (~64 МБ)")
            false
        }
    }

    /**
     * Opens an incremental Piper TTS playback session so the assistant begins speaking
     * as soon as the first sentence is streamed. Returns null if muted or no Piper voice is ready.
     */
    fun beginStreamingSession(): Long? {
        if (isMuted) return null
        val piperDir = runCatching { piperModelDirProvider() }.getOrNull() ?: return null
        stop()

        val sessionId = ++streamSessionSeq
        val channel = Channel<String>(Channel.UNLIMITED)
        activeStreamSessionId = sessionId
        activeStreamChannel = channel
        activeSpeakJob = scope.launch {
            try {
                playPiperQueue(piperDir, fallbackFullText = null) {
                    channel.receiveCatching().getOrNull()
                }
            } finally {
                if (activeStreamChannel === channel) {
                    activeStreamChannel = null
                    activeStreamSessionId = 0L
                }
                channel.close()
            }
        }
        return sessionId
    }

    /** Queues one completed sentence into the active streaming session. */
    fun enqueueStreamingSentence(sessionId: Long, sentence: String): Boolean {
        if (isMuted || sessionId <= 0L || sessionId != activeStreamSessionId) return false
        val trimmed = sentence.trim()
        if (trimmed.isEmpty()) return false
        return activeStreamChannel?.trySend(trimmed)?.isSuccess == true
    }

    /** Closes the sentence channel so queued sentences finish playing cleanly and release audio focus. */
    fun finishStreamingSession(sessionId: Long) {
        if (sessionId > 0L && sessionId == activeStreamSessionId) {
            activeStreamChannel?.close()
        }
    }

    private fun speakWithPiper(modelDir: File, text: String) {
        val chunks = PiperTtsEngine.splitForTts(text)
        if (chunks.isEmpty()) return
        val iter = chunks.iterator()

        activeSpeakJob = scope.launch {
            playPiperQueue(modelDir, fallbackFullText = text) {
                if (iter.hasNext()) iter.next() else null
            }
        }
    }

    private suspend fun playPiperQueue(
        modelDir: File,
        fallbackFullText: String?,
        nextChunk: suspend () -> String?,
    ) {
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .build()

        var focusGranted = false
        var track: AudioTrack? = null
        var startedPlayback = false
        var completedNormally = false
        var totalWrittenFrames = 0L

        try {
            piperEngine.load(modelDir)
            val sampleRate = piperEngine.sampleRate()
            val speed = speechSpeedProvider().coerceIn(0.65f, 1.25f)
            val sliceSamples = (sampleRate / 10).coerceAtLeast(1600)
            val interChunkSilence = ShortArray((sampleRate * 0.22f).toInt())

            while (true) {
                val chunk = nextChunk() ?: break
                currentCoroutineContext().ensureActive()
                val trimmed = chunk.trim()
                if (trimmed.isEmpty()) continue
                val floats = piperEngine.synthesize(trimmed, speakerId = 0, speed = speed)
                if (floats.isEmpty()) continue

                if (!startedPlayback) {
                    val minBufferBytes = AudioTrack.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                    ).coerceAtLeast(sampleRate)
                    val bufferBytes = (minBufferBytes * 2).coerceAtLeast(sampleRate * 2)

                    val createdTrack = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(bufferBytes)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()

                    track = createdTrack
                    activeTrack = createdTrack
                    focusGranted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                    createdTrack.play()
                    startedPlayback = true
                    onSpeakingChanged?.invoke(true)
                } else {
                    currentCoroutineContext().ensureActive()
                    val writtenSilence = track?.write(interChunkSilence, 0, interChunkSilence.size, AudioTrack.WRITE_BLOCKING) ?: 0
                    if (writtenSilence > 0) totalWrittenFrames += writtenSilence
                }

                val activeOut = track ?: break
                val pcm16 = ShortArray(floats.size) { idx ->
                    (floats[idx].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                }
                var offset = 0
                while (offset < pcm16.size) {
                    currentCoroutineContext().ensureActive()
                    val count = minOf(sliceSamples, pcm16.size - offset)
                    val written = activeOut.write(pcm16, offset, count, AudioTrack.WRITE_BLOCKING)
                    if (written < 0) break
                    totalWrittenFrames += written
                    offset += written
                }
            }

            if (startedPlayback && currentCoroutineContext().isActive) {
                val activeOut = track
                val tailSilence = ShortArray((sampleRate * 0.35f).toInt())
                val writtenTail = activeOut?.write(tailSilence, 0, tailSilence.size, AudioTrack.WRITE_BLOCKING) ?: 0
                if (writtenTail > 0) totalWrittenFrames += writtenTail
                activeOut?.stop()

                // Wait until AudioTrack actually plays all buffered frames (buffer holds ~1000 ms on ColorOS/Android)
                // so trailing Russian syllables are never clipped by premature release().
                val maxDrainWaitMs = calculateMaxDrainWaitMs(totalWrittenFrames, sampleRate)
                val drainStartMs = System.currentTimeMillis()
                while (activeOut != null && currentCoroutineContext().isActive) {
                    val playedFrames = activeOut.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                    val elapsedMs = System.currentTimeMillis() - drainStartMs
                    if (playedFrames >= totalWrittenFrames || elapsedMs >= maxDrainWaitMs) {
                        break
                    }
                    delay(25L)
                }
                // Extra hardware HAL output pipeline margin (Bluetooth / ColorOS speaker DSP).
                delay(180L)
                currentCoroutineContext().ensureActive()
                completedNormally = true
            }
        } catch (_: CancellationException) {
            // Barge-in or user stop requested.
        } catch (err: Throwable) {
            onError?.invoke("Ошибка Piper TTS: ${err.message ?: err.javaClass.simpleName}")
            if (fallbackFullText != null && systemTtsInitialized) {
                speakWithSystemTts(fallbackFullText)
            }
        } finally {
            activeTrack = null
            runCatching {
                if (!completedNormally) {
                    track?.pause()
                    track?.flush()
                }
                track?.release()
            }
            if (focusGranted) {
                runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
            }
            if (startedPlayback) {
                onSpeakingChanged?.invoke(false)
            }
        }
    }

    private fun speakWithSystemTts(text: String) {
        val current = systemEngine ?: return
        if (!systemTtsInitialized) {
            pendingSystemText = text
            return
        }
        runCatching { current.setSpeechRate(speechSpeedProvider().coerceIn(0.65f, 1.25f)) }
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

    fun stop() {
        streamSessionSeq++
        activeStreamSessionId = 0L
        activeStreamChannel?.close()
        activeStreamChannel = null
        pendingSystemText = null
        activeSpeakJob?.cancel()
        activeSpeakJob = null
        runCatching {
            activeTrack?.pause()
            activeTrack?.flush()
        }
        runCatching { systemEngine?.stop() }
        onSpeakingChanged?.invoke(false)
    }

    fun close() {
        stop()
        scope.cancel()
        runCatching { piperEngine.close() }
        runCatching { ttsDispatcher.close() }
        runCatching {
            systemEngine?.stop()
            systemEngine?.shutdown()
        }
        systemEngine = null
        systemTtsInitialized = false
    }

    companion object {
        internal fun calculateMaxDrainWaitMs(totalWrittenFrames: Long, sampleRate: Int): Long {
            val safeRate = sampleRate.coerceAtLeast(8_000)
            val audioDurationMs = (totalWrittenFrames * 1000L) / safeRate
            // Buffer holds ~1.0–1.5s + HAL pipeline; bound max wait so a broken HAL never hangs.
            return (audioDurationMs + 1_200L).coerceIn(1_500L, 4_500L)
        }
    }
}
