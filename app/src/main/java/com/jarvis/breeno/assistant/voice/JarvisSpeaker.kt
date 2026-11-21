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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext

/**
 * Dual-backend Russian voice output:
 * 1) Primary: Offline neural Piper TTS via Sherpa-ONNX + AudioTrack (works on Chinese ColorOS).
 * 2) Fallback: Android system TextToSpeech when Russian voice data is installed.
 */
class JarvisSpeaker(
    context: Context,
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
        else -> "Нет русского голоса — скачайте Piper (20 МБ)"
    }

    fun speak(text: String) {
        if (text.isBlank() || isMuted) return
        stop()

        val piperDir = runCatching { piperModelDirProvider() }.getOrNull()
        if (piperDir != null) {
            speakWithPiper(piperDir, text)
        } else if (systemTtsInitialized) {
            speakWithSystemTts(text)
        } else {
            onError?.invoke("Русский голос не установлен: откройте вкладку «Модели» и скачайте «Дмитрий» (20 МБ)")
        }
    }

    private fun speakWithPiper(modelDir: File, text: String) {
        val chunks = PiperTtsEngine.splitForTts(text)
        if (chunks.isEmpty()) return

        activeSpeakJob = scope.launch {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()

            var focusGranted = false
            var track: AudioTrack? = null
            try {
                piperEngine.load(modelDir)
                val sampleRate = piperEngine.sampleRate()
                val minBufferBytes = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                ).coerceAtLeast(sampleRate / 2)

                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
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
                    .setBufferSizeInBytes(minBufferBytes * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                activeTrack = track
                focusGranted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                track.play()
                onSpeakingChanged?.invoke(true)

                val sliceSamples = (sampleRate / 10).coerceAtLeast(1600)
                for (chunk in chunks) {
                    coroutineContext.ensureActive()
                    val floats = piperEngine.synthesize(chunk, speakerId = 0, speed = 1.0f)
                    if (floats.isEmpty()) continue
                    val pcm16 = ShortArray(floats.size) { idx ->
                        (floats[idx].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                    }
                    var offset = 0
                    while (offset < pcm16.size) {
                        coroutineContext.ensureActive()
                        val count = minOf(sliceSamples, pcm16.size - offset)
                        val written = track.write(pcm16, offset, count)
                        if (written < 0) break
                        offset += written
                    }
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
                    track?.pause()
                    track?.flush()
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
