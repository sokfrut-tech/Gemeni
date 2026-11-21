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

    private fun speakWithPiper(modelDir: File, text: String) {
        val chunks = PiperTtsEngine.splitForTts(text)
        if (chunks.isEmpty()) return

        activeSpeakJob = scope.launch {
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

            try {
                piperEngine.load(modelDir)
                val sampleRate = piperEngine.sampleRate()
                val minBufferBytes = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                ).coerceAtLeast(sampleRate)
                val bufferBytes = (minBufferBytes * 2).coerceAtLeast(sampleRate * 2)

                track = AudioTrack.Builder()
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

                activeTrack = track
                val speed = speechSpeedProvider().coerceIn(0.65f, 1.25f)
                val sliceSamples = (sampleRate / 10).coerceAtLeast(1600)
                val interChunkSilence = ShortArray((sampleRate * 0.20f).toInt())

                for ((index, chunk) in chunks.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    val floats = piperEngine.synthesize(chunk, speakerId = 0, speed = speed)
                    if (floats.isEmpty()) continue

                    if (!startedPlayback) {
                        focusGranted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                        track.play()
                        startedPlayback = true
                        onSpeakingChanged?.invoke(true)
                    }

                    val pcm16 = ShortArray(floats.size) { idx ->
                        (floats[idx].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                    }
                    var offset = 0
                    while (offset < pcm16.size) {
                        currentCoroutineContext().ensureActive()
                        val count = minOf(sliceSamples, pcm16.size - offset)
                        val written = track.write(pcm16, offset, count, AudioTrack.WRITE_BLOCKING)
                        if (written < 0) break
                        offset += written
                    }

                    if (index < chunks.lastIndex) {
                        currentCoroutineContext().ensureActive()
                        track.write(interChunkSilence, 0, interChunkSilence.size, AudioTrack.WRITE_BLOCKING)
                    }
                }

                if (startedPlayback && currentCoroutineContext().isActive) {
                    val tailSilence = ShortArray((sampleRate * 0.25f).toInt())
                    track.write(tailSilence, 0, tailSilence.size, AudioTrack.WRITE_BLOCKING)
                    track.stop()
                    delay(250)
                    currentCoroutineContext().ensureActive()
                    completedNormally = true
                }
            } catch (_: CancellationException) {
                // Barge-in or user stop requested.
            } catch (err: Throwable) {
                onError?.invoke("Ошибка Piper TTS: ${err.message ?: err.javaClass.simpleName}")
                // Fallback to system TTS only if Russian Android TTS is actually available.
                if (systemTtsInitialized) {
                    speakWithSystemTts(text)
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
}
