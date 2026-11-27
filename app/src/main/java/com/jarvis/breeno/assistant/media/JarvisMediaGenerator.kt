package com.jarvis.breeno.assistant.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import kotlin.random.Random

enum class MediaGenBackend(val title: String) {
    ON_DEVICE_SD_CPP("Локально на телефоне (GGUF / Safetensors)"),
    POLLINATIONS_ONLINE("Встроенный онлайн (Pollinations + AI Horde)"),
    AI_HORDE_FREE("AI Horde онлайн (бесплатно, без HTTP 402)"),
    LOCAL_DREAM_NPU("Офлайн на телефоне (Local Dream NPU)"),
    CUSTOM_COMFYUI("Свой ПК (ComfyUI API)"),
    CUSTOM_A1111_SD("Свой ПК (Stable Diffusion / A1111 WebUI)"),
    CUSTOM_OPENAI_IMAGES("Свой сервер (OpenAI Images API)"),
}

data class MediaGenConfig(
    val backend: MediaGenBackend = MediaGenBackend.POLLINATIONS_ONLINE,
    val customImageUrl: String = "",
    val customVideoUrl: String = "",
    val apiKey: String = "",
    val modelName: String = "",
    val negativePrompt: String = "lowres, blurry, bad anatomy, watermark, text",
    val steps: Int = 24,
    val cfgScale: Float = 7.0f,
    val checkpointName: String = "",
    val comfyVaeName: String = "",
    val comfyClip1Name: String = "",
    val comfyClip2Name: String = "",
    val localSdModelPath: String = "",
    val localSdVaePath: String = "",
    val localSdClip1Path: String = "",
    val localSdClip2Path: String = "",
    val uncensoredMode: Boolean = true,
    val comfyPonyClipSkip2: Boolean = true,
    val comfyWanI2vEnabled: Boolean = true,
    val comfyWanHighNoiseModel: String = JarvisMediaGenerator.DEFAULT_WAN_HIGH_MODEL,
    val comfyWanLowNoiseModel: String = JarvisMediaGenerator.DEFAULT_WAN_LOW_MODEL,
    val comfyWanTextEncoder: String = JarvisMediaGenerator.DEFAULT_WAN_TEXT_ENCODER,
    val comfyWanVae: String = JarvisMediaGenerator.DEFAULT_WAN_VAE,
    val comfyWanTurboLora: Boolean = true,
    val comfyVideoFrames: Int = 49,
    val localDreamUrl: String = JarvisMediaGenerator.DEFAULT_LOCAL_DREAM_URL,
)

data class ComfyAutoWireResult(
    val architecture: String,
    val modelName: String,
    val vaeName: String,
    val clip1Name: String,
    val clip2Name: String,
    val steps: Int,
    val cfgScale: Float,
    val usePonyClipSkip2: Boolean,
    val negativePrompt: String,
    val summaryRu: String,
)

data class ComfyImageRef(
    val filename: String,
    val subfolder: String,
    val type: String,
)

class MediaGenConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_media_gen", Context.MODE_PRIVATE)

    fun load(): MediaGenConfig {
        val rawBackend = prefs.getString(KEY_BACKEND, MediaGenBackend.POLLINATIONS_ONLINE.name)
        val backend = runCatching { MediaGenBackend.valueOf(rawBackend ?: "") }
            .getOrDefault(MediaGenBackend.POLLINATIONS_ONLINE)
        val storedModel = prefs.getString(KEY_MODEL, "") ?: ""
        // Migrate old default "flux" that triggered HTTP 402 on Pollinations free tier.
        val resolvedModel = if (backend == MediaGenBackend.POLLINATIONS_ONLINE && storedModel.equals("flux", ignoreCase = true)) {
            ""
        } else {
            storedModel
        }
        return MediaGenConfig(
            backend = backend,
            customImageUrl = prefs.getString(KEY_IMG_URL, "") ?: "",
            customVideoUrl = prefs.getString(KEY_VID_URL, "") ?: "",
            apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
            modelName = resolvedModel,
            negativePrompt = prefs.getString(KEY_NEG_PROMPT, "lowres, blurry, bad anatomy, watermark, text")
                ?: "lowres, blurry, bad anatomy, watermark, text",
            steps = prefs.getInt(KEY_STEPS, 24).coerceIn(1, 100),
            cfgScale = prefs.getFloat(KEY_CFG_SCALE, 7.0f).coerceIn(1.0f, 30.0f),
            checkpointName = prefs.getString(KEY_CHECKPOINT, "") ?: "",
            comfyVaeName = prefs.getString(KEY_COMFY_VAE, "") ?: "",
            comfyClip1Name = prefs.getString(KEY_COMFY_CLIP1, "") ?: "",
            comfyClip2Name = prefs.getString(KEY_COMFY_CLIP2, "") ?: "",
            localSdModelPath = prefs.getString(KEY_LOCAL_SD_MODEL, "") ?: "",
            localSdVaePath = prefs.getString(KEY_LOCAL_SD_VAE, "") ?: "",
            localSdClip1Path = prefs.getString(KEY_LOCAL_SD_CLIP1, "") ?: "",
            localSdClip2Path = prefs.getString(KEY_LOCAL_SD_CLIP2, "") ?: "",
            uncensoredMode = prefs.getBoolean(KEY_UNCENSORED_MODE, true),
            comfyPonyClipSkip2 = prefs.getBoolean(KEY_COMFY_PONY_CLIP_SKIP2, true),
            comfyWanI2vEnabled = prefs.getBoolean(KEY_COMFY_WAN_I2V_ENABLED, true),
            comfyWanHighNoiseModel = prefs.getString(KEY_COMFY_WAN_HIGH, JarvisMediaGenerator.DEFAULT_WAN_HIGH_MODEL)
                ?: JarvisMediaGenerator.DEFAULT_WAN_HIGH_MODEL,
            comfyWanLowNoiseModel = prefs.getString(KEY_COMFY_WAN_LOW, JarvisMediaGenerator.DEFAULT_WAN_LOW_MODEL)
                ?: JarvisMediaGenerator.DEFAULT_WAN_LOW_MODEL,
            comfyWanTextEncoder = prefs.getString(KEY_COMFY_WAN_CLIP, JarvisMediaGenerator.DEFAULT_WAN_TEXT_ENCODER)
                ?: JarvisMediaGenerator.DEFAULT_WAN_TEXT_ENCODER,
            comfyWanVae = prefs.getString(KEY_COMFY_WAN_VAE, JarvisMediaGenerator.DEFAULT_WAN_VAE)
                ?: JarvisMediaGenerator.DEFAULT_WAN_VAE,
            comfyWanTurboLora = prefs.getBoolean(KEY_COMFY_WAN_TURBO, true),
            comfyVideoFrames = prefs.getInt(KEY_COMFY_VIDEO_FRAMES, 49).coerceIn(17, 121),
            localDreamUrl = prefs.getString(KEY_LOCAL_DREAM_URL, JarvisMediaGenerator.DEFAULT_LOCAL_DREAM_URL)
                ?: JarvisMediaGenerator.DEFAULT_LOCAL_DREAM_URL,
        )
    }

    fun save(config: MediaGenConfig) {
        prefs.edit()
            .putString(KEY_BACKEND, config.backend.name)
            .putString(KEY_IMG_URL, config.customImageUrl.trim())
            .putString(KEY_VID_URL, config.customVideoUrl.trim())
            .putString(KEY_API_KEY, config.apiKey.trim())
            .putString(KEY_MODEL, config.modelName.trim())
            .putString(KEY_NEG_PROMPT, config.negativePrompt.trim())
            .putInt(KEY_STEPS, config.steps.coerceIn(1, 100))
            .putFloat(KEY_CFG_SCALE, config.cfgScale.coerceIn(1.0f, 30.0f))
            .putString(KEY_CHECKPOINT, config.checkpointName.trim())
            .putString(KEY_COMFY_VAE, config.comfyVaeName.trim())
            .putString(KEY_COMFY_CLIP1, config.comfyClip1Name.trim())
            .putString(KEY_COMFY_CLIP2, config.comfyClip2Name.trim())
            .putString(KEY_LOCAL_SD_MODEL, config.localSdModelPath.trim())
            .putString(KEY_LOCAL_SD_VAE, config.localSdVaePath.trim())
            .putString(KEY_LOCAL_SD_CLIP1, config.localSdClip1Path.trim())
            .putString(KEY_LOCAL_SD_CLIP2, config.localSdClip2Path.trim())
            .putBoolean(KEY_UNCENSORED_MODE, config.uncensoredMode)
            .putBoolean(KEY_COMFY_PONY_CLIP_SKIP2, config.comfyPonyClipSkip2)
            .putBoolean(KEY_COMFY_WAN_I2V_ENABLED, config.comfyWanI2vEnabled)
            .putString(KEY_COMFY_WAN_HIGH, config.comfyWanHighNoiseModel.trim())
            .putString(KEY_COMFY_WAN_LOW, config.comfyWanLowNoiseModel.trim())
            .putString(KEY_COMFY_WAN_CLIP, config.comfyWanTextEncoder.trim())
            .putString(KEY_COMFY_WAN_VAE, config.comfyWanVae.trim())
            .putBoolean(KEY_COMFY_WAN_TURBO, config.comfyWanTurboLora)
            .putInt(KEY_COMFY_VIDEO_FRAMES, config.comfyVideoFrames.coerceIn(17, 121))
            .putString(KEY_LOCAL_DREAM_URL, config.localDreamUrl.trim().ifBlank { JarvisMediaGenerator.DEFAULT_LOCAL_DREAM_URL })
            .apply()
    }

    companion object {
        private const val KEY_BACKEND = "media_backend"
        private const val KEY_IMG_URL = "media_custom_image_url"
        private const val KEY_VID_URL = "media_custom_video_url"
        private const val KEY_API_KEY = "media_api_key"
        private const val KEY_MODEL = "media_model"
        private const val KEY_NEG_PROMPT = "media_neg_prompt"
        private const val KEY_STEPS = "media_steps"
        private const val KEY_CFG_SCALE = "media_cfg_scale"
        private const val KEY_CHECKPOINT = "media_checkpoint"
        private const val KEY_COMFY_VAE = "media_comfy_vae"
        private const val KEY_COMFY_CLIP1 = "media_comfy_clip1"
        private const val KEY_COMFY_CLIP2 = "media_comfy_clip2"
        private const val KEY_LOCAL_SD_MODEL = "media_local_sd_model"
        private const val KEY_LOCAL_SD_VAE = "media_local_sd_vae"
        private const val KEY_LOCAL_SD_CLIP1 = "media_local_sd_clip1"
        private const val KEY_LOCAL_SD_CLIP2 = "media_local_sd_clip2"
        private const val KEY_UNCENSORED_MODE = "media_uncensored_mode"
        private const val KEY_COMFY_PONY_CLIP_SKIP2 = "media_comfy_pony_clip_skip2"
        private const val KEY_COMFY_WAN_I2V_ENABLED = "media_comfy_wan_i2v_enabled"
        private const val KEY_COMFY_WAN_HIGH = "media_comfy_wan_high"
        private const val KEY_COMFY_WAN_LOW = "media_comfy_wan_low"
        private const val KEY_COMFY_WAN_CLIP = "media_comfy_wan_clip"
        private const val KEY_COMFY_WAN_VAE = "media_comfy_wan_vae"
        private const val KEY_COMFY_WAN_TURBO = "media_comfy_wan_turbo"
        private const val KEY_COMFY_VIDEO_FRAMES = "media_comfy_video_frames"
        private const val KEY_LOCAL_DREAM_URL = "media_local_dream_url"
    }
}

enum class GeneratedMediaType(val label: String) {
    IMAGE("Картинка"),
    VIDEO("Видео MP4"),
}

data class GeneratedMediaItem(
    val id: String,
    val type: GeneratedMediaType,
    val prompt: String,
    val style: String,
    val filePath: String,
    val sizeBytes: Long,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

sealed interface OpenAiImagePayload {
    data class Base64Data(val base64: String) : OpenAiImagePayload
    data class RemoteUrl(val url: String) : OpenAiImagePayload
}

sealed interface LocalDreamSseEvent {
    data class Progress(val step: Int, val totalSteps: Int) : LocalDreamSseEvent
    data class Complete(
        val base64Image: String,
        val format: String,
        val width: Int,
        val height: Int,
        val channels: Int,
    ) : LocalDreamSseEvent
    data class Error(val message: String) : LocalDreamSseEvent
    data object Done : LocalDreamSseEvent
}

data class AiHordeCheckStatus(
    val done: Boolean,
    val faulted: Boolean,
    val isPossible: Boolean,
    val waitTime: Int,
    val queuePosition: Int,
    val processing: Int,
)

sealed interface AiHordeImagePayload {
    data class RemoteUrl(val url: String) : AiHordeImagePayload
    data class Base64Data(val base64: String) : AiHordeImagePayload
}

internal class MediaHttpException(
    val statusCode: Int,
    val retryAfterSeconds: Int?,
    val responseBody: String,
    message: String,
) : IllegalStateException(message)

/**
 * Generates images and animated MP4 videos on the phone or via self-hosted PC ComfyUI:
 * - On-device Qualcomm NPU image generation via Local Dream (http://127.0.0.1:8081/generate SSE).
 * - Single-window ComfyUI pipeline supports CyberRealistic Pony XL (Clip Skip 2) -> Wan 2.2 I2V-14B.
 * - Also supports Pollinations + automatic AI Horde free fallback, Automatic1111/Forge WebUI, OpenAI Images, and MP4.
 */
class JarvisMediaGenerator(
    private val studioDir: File,
    private val historyFile: File,
    private val configStore: MediaGenConfigStore,
    private val onEvent: ((title: String, detail: String, isError: Boolean) -> Unit)? = null,
    private val beforeHeavyOnDeviceGen: (suspend () -> Unit)? = null,
) {
    private val gson = Gson()
    private val _items = MutableStateFlow(loadHistory())
    val items: StateFlow<List<GeneratedMediaItem>> = _items.asStateFlow()
    private val _config = MutableStateFlow(configStore.load())
    val config: StateFlow<MediaGenConfig> = _config.asStateFlow()

    @Volatile
    private var pollinationsCooldownUntilMs: Long = 0L

    init {
        studioDir.mkdirs()
        historyFile.parentFile?.mkdirs()
    }

    fun currentConfig(): MediaGenConfig = configStore.load()

    fun saveConfig(config: MediaGenConfig) {
        configStore.save(config)
        _config.value = configStore.load()
    }

    suspend fun generateImage(
        prompt: String,
        style: String = "Фото",
        width: Int = 768,
        height: Int = 768,
        onProgress: (String) -> Unit = {},
    ): Result<GeneratedMediaItem> = withContext(Dispatchers.IO) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Введите описание изображения"))
        }

        val cfg = configStore.load()
        val visualPrompt = RussianMediaPromptBridge.toVisualPrompt(cleanPrompt).ifBlank { cleanPrompt }
        onProgress("Генерирую изображение…")

        runCatching {
            val imageBytes = fetchImageBytes(cfg, visualPrompt, style, width, height, Random.nextInt(1, 999_999), onProgress)
            check(imageBytes.size > 1024) { "Получен слишком маленький или пустой файл изображения" }

            studioDir.mkdirs()
            val id = UUID.randomUUID().toString().take(8)
            val outFile = File(studioDir, "jarvis_img_${id}_${System.currentTimeMillis()}.jpg")
            outFile.writeBytes(imageBytes)

            val item = GeneratedMediaItem(
                id = id,
                type = GeneratedMediaType.IMAGE,
                prompt = cleanPrompt,
                style = style,
                filePath = outFile.absolutePath,
                sizeBytes = outFile.length(),
            )
            _items.update { listOf(item) + it }
            persistHistory()
            onEvent?.invoke("Студия: картинка готова", cleanPrompt.take(50), false)
            item
        }.onFailure { err ->
            val msg = err.message ?: err.javaClass.simpleName
            onEvent?.invoke("Студия: ошибка картинки", msg, true)
        }
    }

    suspend fun generateVideo(
        prompt: String,
        style: String = "Кино",
        width: Int = 512,
        height: Int = 512,
        motionPrompt: String = "",
        onProgress: (String) -> Unit = {},
    ): Result<GeneratedMediaItem> = withContext(Dispatchers.IO) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Введите описание видео"))
        }

        val cfg = configStore.load()
        val visualPrompt = RussianMediaPromptBridge.toVisualPrompt(cleanPrompt).ifBlank { cleanPrompt }
        val visualMotion = if (motionPrompt.isNotBlank()) {
            RussianMediaPromptBridge.toVisualPrompt(motionPrompt.trim()).ifBlank { motionPrompt.trim() }
        } else {
            ""
        }
        val id = UUID.randomUUID().toString().take(8)
        studioDir.mkdirs()
        val outFile = File(studioDir, "jarvis_vid_${id}_${System.currentTimeMillis()}.mp4")

        runCatching {
            if (cfg.customVideoUrl.isNotBlank()) {
                onProgress("Отправляю запрос на ваш видео-сервер…")
                val reqJson = gson.toJson(mapOf("prompt" to visualPrompt, "style" to style, "motion_prompt" to visualMotion))
                val headers = buildAuthHeaders(cfg.apiKey)
                val bytes = postBytes(cfg.customVideoUrl, reqJson, headers, readTimeoutMs = 90_000)
                // Support either raw MP4 bytes or JSON { "video_url": "..." }
                val textHead = bytes.take(128).toByteArray().toString(Charsets.UTF_8).trimStart()
                if (textHead.startsWith("{")) {
                    val obj = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
                    val remoteUrl = obj.get("video_url")?.asString ?: obj.get("url")?.asString
                        ?: error("Видео-сервер не вернул MP4 или video_url")
                    val mp4Bytes = getBytes(remoteUrl, headers, readTimeoutMs = 90_000)
                    outFile.writeBytes(mp4Bytes)
                } else {
                    outFile.writeBytes(bytes)
                }
            } else if (cfg.backend == MediaGenBackend.CUSTOM_COMFYUI && cfg.comfyWanI2vEnabled) {
                val mp4Bytes = fetchVideoFromComfyUiWan22(
                    cfg = cfg,
                    imagePrompt = visualPrompt,
                    motionPrompt = visualMotion,
                    style = style,
                    videoWidth = width,
                    videoHeight = height,
                    seed = Random.nextInt(1, 999_999),
                    onProgress = onProgress,
                )
                outFile.writeBytes(mp4Bytes)
            } else {
                val scenes = buildStoryboardPrompts(visualPrompt, style)
                val bitmaps = mutableListOf<Bitmap>()
                try {
                    val baseSeed = Random.nextInt(1, 999_999)
                    scenes.forEachIndexed { idx, scenePrompt ->
                        onProgress("Рисую сцену ${idx + 1}/${scenes.size}…")
                        val bytes = fetchImageBytes(cfg, scenePrompt, style, width, height, baseSeed + idx, onProgress)
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            ?: error("Не удалось декодировать кадр ${idx + 1}")
                        bitmaps.add(bmp)
                    }
                    onProgress("Собираю MP4 видео (H.264)…")
                    JarvisMp4VideoSynthesizer.renderAnimatedMp4(
                        keyframes = bitmaps,
                        outputFile = outFile,
                        width = width,
                        height = height,
                        fps = 24,
                        secondsPerScene = 2,
                    )
                } finally {
                    bitmaps.forEach { it.recycle() }
                }
            }

            check(outFile.isFile && outFile.length() > 4096L) { "Созданный MP4 файл пуст" }
            val item = GeneratedMediaItem(
                id = id,
                type = GeneratedMediaType.VIDEO,
                prompt = cleanPrompt,
                style = style,
                filePath = outFile.absolutePath,
                sizeBytes = outFile.length(),
            )
            _items.update { listOf(item) + it }
            persistHistory()
            onEvent?.invoke("Студия: видео MP4 готово", cleanPrompt.take(50), false)
            item
        }.onFailure { err ->
            outFile.delete()
            val msg = err.message ?: err.javaClass.simpleName
            onEvent?.invoke("Студия: ошибка видео", msg, true)
        }
    }

    fun deleteItem(id: String) {
        val target = _items.value.firstOrNull { it.id == id }
        if (target != null) {
            runCatching { File(target.filePath).delete() }
        }
        _items.update { list -> list.filterNot { it.id == id } }
        persistHistory()
    }

    private suspend fun fetchImageBytes(
        cfg: MediaGenConfig,
        prompt: String,
        style: String,
        width: Int,
        height: Int,
        seed: Int,
        onProgress: (String) -> Unit,
    ): ByteArray {
        return when (cfg.backend) {
            MediaGenBackend.POLLINATIONS_ONLINE -> fetchOnlineAuto(cfg, prompt, style, width, height, seed, onProgress)
            MediaGenBackend.AI_HORDE_FREE -> fetchFromAiHorde(
                cfg = cfg,
                prompt = prompt,
                style = style,
                width = width,
                height = height,
                seed = seed,
                onProgress = onProgress,
                useCustomKey = true,
            )
            MediaGenBackend.LOCAL_DREAM_NPU -> fetchFromLocalDreamNpu(cfg, prompt, style, width, height, seed, onProgress)
            MediaGenBackend.CUSTOM_COMFYUI -> fetchFromComfyUi(cfg, prompt, style, width, height, seed, onProgress)
            MediaGenBackend.CUSTOM_A1111_SD -> {
                val rawUrl = cfg.customImageUrl.trim()
                require(rawUrl.startsWith("http://", true) || rawUrl.startsWith("https://", true)) {
                    "Укажите URL вашего Stable Diffusion (например, https://.../sd или http://192.168.1.50:7860)"
                }
                val targetUrl = normalizeA1111Txt2ImgUrl(rawUrl)
                val reqJson = buildA1111RequestJson(
                    prompt = prompt,
                    style = style,
                    width = width,
                    height = height,
                    negativePrompt = cfg.negativePrompt,
                    steps = cfg.steps,
                    cfgScale = cfg.cfgScale,
                    seed = seed,
                )
                val resp = postBytes(targetUrl, reqJson, buildAuthHeaders(cfg.apiKey), readTimeoutMs = 90_000)
                val b64 = parseA1111Base64Image(resp.toString(Charsets.UTF_8))
                Base64.decode(b64, Base64.DEFAULT)
            }
            MediaGenBackend.ON_DEVICE_SD_CPP -> fetchFromOnDeviceSdCpp(
                cfg = cfg,
                prompt = prompt,
                style = style,
                width = width,
                height = height,
                seed = seed,
                onProgress = onProgress,
            )

            MediaGenBackend.CUSTOM_OPENAI_IMAGES -> {
                val url = cfg.customImageUrl.trim().ifBlank { "https://api.openai.com/v1/images/generations" }
                val reqJson = buildOpenAiImageRequestJson(cfg.modelName.ifBlank { "gpt-image-1" }, prompt, style, width, height)
                val headers = buildAuthHeaders(cfg.apiKey)
                val resp = postBytes(url, reqJson, headers, readTimeoutMs = 90_000)
                when (val payload = parseOpenAiImageResult(resp.toString(Charsets.UTF_8))) {
                    is OpenAiImagePayload.Base64Data -> Base64.decode(payload.base64, Base64.DEFAULT)
                    is OpenAiImagePayload.RemoteUrl -> getBytes(payload.url, emptyMap(), readTimeoutMs = 45_000, expectImage = true)
                }
            }
        }
    }

    private suspend fun fetchFromOnDeviceSdCpp(
        cfg: MediaGenConfig,
        prompt: String,
        style: String,
        width: Int,
        height: Int,
        seed: Int,
        onProgress: (String) -> Unit,
    ): ByteArray {
        val modelPath = cfg.localSdModelPath.trim()
        require(modelPath.isNotEmpty() && File(modelPath).isFile) {
            "Выберите локальный файл модели (.gguf или .safetensors) в настройках Студии ИИ"
        }
        val vaePath = cfg.localSdVaePath.trim().takeIf { it.isNotEmpty() && File(it).isFile }.orEmpty()
        val clip1Path = cfg.localSdClip1Path.trim().takeIf { it.isNotEmpty() && File(it).isFile }.orEmpty()
        val clip2Path = cfg.localSdClip2Path.trim().takeIf { it.isNotEmpty() && File(it).isFile }.orEmpty()

        val spec = StableDiffusionNativeBridge.resolveLocalModelSpec(
            modelPath = modelPath,
            vaePath = vaePath,
            clip1Path = clip1Path,
            clip2Path = clip2Path,
        )

        onProgress("Освобождаю RAM под локальный рендер…")
        beforeHeavyOnDeviceGen?.invoke()

        val styledPrompt = "${prompt.trim()}, ${styleSuffix(style)}"
        val safeW = ((width.coerceIn(256, 1024) + 32) / 64) * 64
        val safeH = ((height.coerceIn(256, 1024) + 32) / 64) * 64
        val steps = cfg.steps.coerceIn(1, 60)
        val cfgScale = cfg.cfgScale.coerceIn(1.0f, 20.0f)

        onProgress("Загружаю ${File(modelPath).name} (stable-diffusion.cpp)…")
        return try {
            StableDiffusionNativeBridge.generateJpegBytes(
                spec = spec,
                prompt = styledPrompt,
                negativePrompt = cfg.negativePrompt.trim(),
                width = safeW,
                height = safeH,
                steps = steps,
                cfgScale = cfgScale,
                clipSkip = spec.recommendedClipSkip,
                seed = seed.toLong(),
                nThreads = 4,
                onStepProgress = { step, total ->
                    onProgress("Локальный рендер: шаг $step/$total…")
                },
            )
        } catch (cancelled: CancellationException) {
            StableDiffusionNativeBridge.nativeCancel()
            throw cancelled
        }
    }

    private suspend fun fetchOnlineAuto(
        cfg: MediaGenConfig,
        prompt: String,
        style: String,
        width: Int,
        height: Int,
        seed: Int,
        onProgress: (String) -> Unit,
    ): ByteArray {
        var pollinationsErr: Throwable? = null
        val customKey = cfg.apiKey.trim()
        if (customKey.isNotEmpty() && !customKey.startsWith("sk-proj-")) {
            try {
                onProgress("Отправляю запрос в Pollinations (API-ключ)…")
                val authUrl = buildPollinationsAuthenticatedUrl(prompt, style, width, height, seed, cfg.modelName)
                val bytes = getBytes(
                    urlStr = authUrl,
                    headers = mapOf("Authorization" to "Bearer $customKey"),
                    readTimeoutMs = 45_000,
                    expectImage = true,
                )
                return ensureJpegBytes(bytes)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (err: Throwable) {
                pollinationsErr = err
                val codeInfo = if (err is MediaHttpException) " (HTTP ${err.statusCode})" else ""
                onProgress("Pollinations по ключу не ответил$codeInfo — пробую резервный AI Horde…")
            }
        }

        val now = System.currentTimeMillis()
        if (now >= pollinationsCooldownUntilMs) {
            try {
                val url = buildPollinationsImageUrl(prompt, style, width, height, seed, cfg.modelName)
                val bytes = getBytes(url, emptyMap(), readTimeoutMs = 35_000, expectImage = true)
                pollinationsCooldownUntilMs = System.currentTimeMillis() + 46_000L
                return ensureJpegBytes(bytes)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (err: MediaHttpException) {
                pollinationsErr = err
                if (err.statusCode == 402 || err.statusCode == 429) {
                    val waitSec = (err.retryAfterSeconds ?: 45).coerceIn(10, 120)
                    pollinationsCooldownUntilMs = System.currentTimeMillis() + waitSec * 1000L
                    onProgress("Pollinations занят (HTTP ${err.statusCode}) — переключаюсь на резервный AI Horde…")
                } else {
                    onProgress("Pollinations недоступен (HTTP ${err.statusCode}) — переключаюсь на AI Horde…")
                }
            } catch (err: Throwable) {
                pollinationsErr = err
                onProgress("Pollinations не ответил — переключаюсь на AI Horde…")
            }
        } else {
            onProgress("Pollinations на паузе (лимит 45 с) — генерирую через AI Horde…")
        }

        return try {
            fetchFromAiHorde(
                cfg = cfg,
                prompt = prompt,
                style = style,
                width = width,
                height = height,
                seed = seed,
                onProgress = onProgress,
                useCustomKey = false,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (hordeErr: Throwable) {
            val pollHint = if (pollinationsErr is MediaHttpException && pollinationsErr.statusCode == 402) {
                "Pollinations: лимит 1 фото / 45 сек (HTTP 402)"
            } else {
                pollinationsErr?.message ?: "Pollinations на паузе"
            }
            error("$pollHint; резервный AI Horde: ${hordeErr.message ?: hordeErr.javaClass.simpleName}. Повторите чуть позже или используйте Local Dream NPU.")
        }
    }

    private suspend fun fetchFromAiHorde(
        cfg: MediaGenConfig,
        prompt: String,
        style: String,
        width: Int,
        height: Int,
        seed: Int,
        onProgress: (String) -> Unit,
        useCustomKey: Boolean,
    ): ByteArray {
        val cleanPrompt = translatePromptIfCyrillic(prompt, onProgress)
        val (hordeW, hordeH) = computeAiHordeDimensions(width, height)
        val customModel = if (cfg.backend == MediaGenBackend.AI_HORDE_FREE) cfg.modelName.trim() else ""
        val apiKey = if (useCustomKey && cfg.apiKey.isNotBlank()) cfg.apiKey.trim() else DEFAULT_AI_HORDE_ANON_KEY
        val headers = mapOf(
            "apikey" to apiKey,
            "Client-Agent" to AI_HORDE_CLIENT_AGENT,
        )

        onProgress("AI Horde: отправляю задачу ($hordeW×$hordeH)…")
        var jobId = submitAiHordeJob(
            prompt = cleanPrompt,
            cfg = cfg,
            style = style,
            hordeW = hordeW,
            hordeH = hordeH,
            seed = seed,
            customModel = customModel,
            headers = headers,
            includeModels = true,
        )

        val pollStart = System.currentTimeMillis()
        var retriedWithoutModelFilter = false
        try {
            while (System.currentTimeMillis() - pollStart < 120_000L) {
                currentCoroutineContext().ensureActive()
                val checkJson = getBytes("$AI_HORDE_BASE_URL/generate/check/$jobId", mapOf("Client-Agent" to AI_HORDE_CLIENT_AGENT), readTimeoutMs = 15_000)
                    .toString(Charsets.UTF_8)
                val status = parseAiHordeCheckStatus(checkJson)
                if (status.faulted) {
                    error("AI Horde: сбой воркера (faulted)")
                }
                if (status.done) {
                    onProgress("AI Horde: скачиваю готовое изображение…")
                    val statusJson = getBytes("$AI_HORDE_BASE_URL/generate/status/$jobId", mapOf("Client-Agent" to AI_HORDE_CLIENT_AGENT), readTimeoutMs = 20_000)
                        .toString(Charsets.UTF_8)
                    val rawBytes = when (val payload = parseAiHordeCompletedImage(statusJson)) {
                        is AiHordeImagePayload.RemoteUrl -> {
                            require(payload.url.startsWith("https://", ignoreCase = true)) {
                                "AI Horde вернул небезопасный URL"
                            }
                            getBytes(payload.url, emptyMap(), readTimeoutMs = 45_000, expectImage = true)
                        }
                        is AiHordeImagePayload.Base64Data -> Base64.decode(payload.base64, Base64.DEFAULT)
                    }
                    return ensureJpegBytes(rawBytes)
                }
                if (!status.isPossible && !retriedWithoutModelFilter) {
                    retriedWithoutModelFilter = true
                    cancelAiHordeJobBestEffort(jobId)
                    onProgress("AI Horde: подбираю свободного воркера…")
                    jobId = submitAiHordeJob(
                        prompt = cleanPrompt,
                        cfg = cfg,
                        style = style,
                        hordeW = hordeW,
                        hordeH = hordeH,
                        seed = seed,
                        customModel = "",
                        headers = headers,
                        includeModels = false,
                    )
                    delay(1_500)
                    continue
                }
                val progressMsg = if (status.processing > 0) {
                    "AI Horde рисует… (~${status.waitTime} с)"
                } else {
                    "AI Horde: очередь ${status.queuePosition}, ~${status.waitTime} с…"
                }
                onProgress(progressMsg)
                delay(2_500)
            }
            cancelAiHordeJobBestEffort(jobId)
            error("AI Horde не успел завершить генерацию за 2 минуты")
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { cancelAiHordeJobBestEffort(jobId) }
            throw cancelled
        }
    }

    private fun submitAiHordeJob(
        prompt: String,
        cfg: MediaGenConfig,
        style: String,
        hordeW: Int,
        hordeH: Int,
        seed: Int,
        customModel: String,
        headers: Map<String, String>,
        includeModels: Boolean,
    ): String {
        fun postOnce(withModels: Boolean, modelName: String): String {
            val reqJson = buildAiHordeRequestJson(
                prompt = prompt,
                negativePrompt = cfg.negativePrompt,
                style = style,
                width = hordeW,
                height = hordeH,
                steps = cfg.steps.coerceIn(12, 28),
                cfgScale = cfg.cfgScale.coerceIn(3.0f, 15.0f),
                seed = seed,
                customModel = modelName,
                includeModels = withModels,
                uncensoredMode = cfg.uncensoredMode,
            )
            val resp = postBytes("$AI_HORDE_BASE_URL/generate/async", reqJson, headers, readTimeoutMs = 20_000)
                .toString(Charsets.UTF_8)
            return parseAiHordeSubmitId(resp)
        }
        return try {
            postOnce(includeModels, customModel)
        } catch (err: MediaHttpException) {
            if (includeModels && err.statusCode == 400) {
                postOnce(false, "")
            } else {
                throw err
            }
        }
    }

    private fun translatePromptIfCyrillic(prompt: String, onProgress: (String) -> Unit): String {
        val clean = prompt.trim()
        if (!CYRILLIC_REGEX.containsMatchIn(clean)) return clean
        return runCatching {
            onProgress("Перевожу промпт для SD 1.5…")
            val q = URLEncoder.encode(clean.take(450), "UTF-8")
            val conn = (URL("https://api.mymemory.translated.net/get?q=$q&langpair=ru|en").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4_000
                readTimeout = 4_000
                setRequestProperty("User-Agent", "Jarvis-Android/1.0")
            }
            try {
                if (conn.responseCode !in 200..299) return@runCatching clean
                val json = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                parseMyMemoryTranslation(json) ?: clean
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(clean)
    }

    private fun cancelAiHordeJobBestEffort(jobId: String) {
        runCatching {
            val conn = (URL("$AI_HORDE_BASE_URL/generate/status/$jobId").openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                connectTimeout = 5_000
                readTimeout = 5_000
                setRequestProperty("Client-Agent", AI_HORDE_CLIENT_AGENT)
                setRequestProperty("User-Agent", "Jarvis-Android/1.0")
            }
            try {
                conn.responseCode
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun ensureJpegBytes(rawBytes: ByteArray): ByteArray {
        val isJpeg = rawBytes.size >= 3 &&
            rawBytes[0] == 0xFF.toByte() &&
            rawBytes[1] == 0xD8.toByte() &&
            rawBytes[2] == 0xFF.toByte()
        if (isJpeg) return rawBytes
        val bmp = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return rawBytes
        return try {
            ByteArrayOutputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 94, out)
                out.toByteArray()
            }
        } finally {
            bmp.recycle()
        }
    }

    private suspend fun fetchFromLocalDreamNpu(
        cfg: MediaGenConfig,
        prompt: String,
        style: String,
        width: Int,
        height: Int,
        seed: Int,
        onProgress: (String) -> Unit,
    ): ByteArray {
        onProgress("Освобождаю память перед NPU рендером…")
        runCatching { beforeHeavyOnDeviceGen?.invoke() }

        val targetUrl = normalizeLocalDreamGenerateUrl(cfg.localDreamUrl)
        val reqJson = buildLocalDreamRequestJson(
            prompt = prompt,
            negativePrompt = cfg.negativePrompt.ifBlank { DEFAULT_LOCAL_DREAM_NEGATIVE },
            style = style,
            width = width,
            height = height,
            steps = cfg.steps.coerceIn(8, 50),
            cfgScale = cfg.cfgScale,
            seed = seed,
        )

        onProgress("Подключаюсь к Local Dream NPU…")
        val conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            instanceFollowRedirects = false
            connectTimeout = 5_000
            readTimeout = 180_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "text/event-stream, application/json")
            setRequestProperty("User-Agent", "Jarvis-Android/1.0")
        }

        try {
            conn.outputStream.use { it.write(reqJson.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val errBody = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                error("Local Dream NPU вернул HTTP $code: ${errBody.take(140)}")
            }

            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readLine() ?: break
                    when (val ev = parseLocalDreamSseEvent(line)) {
                        is LocalDreamSseEvent.Progress ->
                            onProgress("Local Dream NPU: шаг ${ev.step}/${ev.totalSteps}…")
                        is LocalDreamSseEvent.Error ->
                            error("Local Dream NPU: ${ev.message}")
                        is LocalDreamSseEvent.Complete ->
                            return decodeLocalDreamCompleteImage(ev)
                        LocalDreamSseEvent.Done -> break
                        null -> Unit
                    }
                }
            }
            error("Local Dream NPU завершил поток без изображения")
        } catch (_: ConnectException) {
            error("Local Dream NPU не запущен (127.0.0.1:8081). Откройте приложение Local Dream, загрузите SD 1.5 модель (Absolute Reality / DreamShaper / Anything V5) и оставьте его в фоне.")
        } finally {
            conn.disconnect()
        }
    }

    private fun decodeLocalDreamCompleteImage(event: LocalDreamSseEvent.Complete): ByteArray {
        val cleanB64 = event.base64Image.substringAfter("base64,")
        val rawBytes = Base64.decode(cleanB64, Base64.DEFAULT)
        if (!event.format.equals("raw", ignoreCase = true)) {
            return rawBytes
        }
        val channels = event.channels.coerceIn(3, 4)
        val w = event.width.coerceAtLeast(1)
        val h = event.height.coerceAtLeast(1)
        val expectedBytes = w.toLong() * h * channels
        check(expectedBytes > 0L && rawBytes.size >= expectedBytes) {
            "Local Dream вернул неполный RAW-буфер (${rawBytes.size} из $expectedBytes Б)"
        }
        val pixels = IntArray(w * h)
        var p = 0
        for (i in pixels.indices) {
            val r = rawBytes[p++].toInt() and 0xFF
            val g = rawBytes[p++].toInt() and 0xFF
            val b = rawBytes[p++].toInt() and 0xFF
            val a = if (channels == 4) rawBytes[p++].toInt() and 0xFF else 0xFF
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        return try {
            bmp.setPixels(pixels, 0, w, 0, 0, w, h)
            ByteArrayOutputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                out.toByteArray()
            }
        } finally {
            bmp.recycle()
        }
    }

    suspend fun autoDiscoverComfyModels(
        rawUrl: String,
        apiKey: String = "",
    ): Result<ComfyAutoWireResult> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = rawUrl.trim()
            require(clean.startsWith("http://", true) || clean.startsWith("https://", true)) {
                "Укажите URL ComfyUI (например, http://192.168.1.10:8188)"
            }
            val baseUrl = normalizeComfyBaseUrl(clean)
            val headers = buildAuthHeaders(apiKey)

            val ggufModels = runCatching {
                val json = getBytes("$baseUrl/object_info/UnetLoaderGGUF", headers, readTimeoutMs = 12_000)
                    .toString(Charsets.UTF_8)
                parseComfyNodeChoices(json, "UnetLoaderGGUF", "unet_name")
            }.getOrDefault(emptyList())

            val checkpoints = runCatching {
                val json = getBytes("$baseUrl/object_info/CheckpointLoaderSimple", headers, readTimeoutMs = 12_000)
                    .toString(Charsets.UTF_8)
                parseComfyNodeChoices(json, "CheckpointLoaderSimple", "ckpt_name")
            }.getOrDefault(emptyList())

            val vaes = runCatching {
                val json = getBytes("$baseUrl/object_info/VAELoader", headers, readTimeoutMs = 12_000)
                    .toString(Charsets.UTF_8)
                parseComfyNodeChoices(json, "VAELoader", "vae_name")
            }.getOrDefault(emptyList())

            val clips = runCatching {
                val json = getBytes("$baseUrl/object_info/DualCLIPLoader", headers, readTimeoutMs = 12_000)
                    .toString(Charsets.UTF_8)
                parseComfyNodeChoices(json, "DualCLIPLoader", "clip_name1")
            }.getOrDefault(emptyList())

            val pickedModel = ggufModels.firstOrNull()
                ?: checkpoints.firstOrNull()
                ?: error("На сервере ComfyUI не найдено моделей (.gguf или .safetensors)")

            autoConfigureComfyModel(
                modelName = pickedModel,
                availableVaes = vaes,
                availableClips = clips,
            )
        }
    }

    private suspend fun resolveComfyCheckpoint(
        baseUrl: String,
        cfg: MediaGenConfig,
        headers: Map<String, String>,
        onProgress: (String) -> Unit,
    ): String = cfg.checkpointName.trim().ifBlank {
        onProgress("Определяю модель ComfyUI…")
        val ggufFirst = runCatching {
            val infoJson = getBytes("$baseUrl/object_info/UnetLoaderGGUF", headers, readTimeoutMs = 12_000)
                .toString(Charsets.UTF_8)
            parseComfyNodeChoices(infoJson, "UnetLoaderGGUF", "unet_name").firstOrNull()
        }.getOrNull()
        if (!ggufFirst.isNullOrBlank()) return ggufFirst

        val infoJson = getBytes("$baseUrl/object_info/CheckpointLoaderSimple", headers, readTimeoutMs = 15_000)
            .toString(Charsets.UTF_8)
        parseComfyFirstCheckpoint(infoJson)
            ?: error("Не найден чекпоинт в ComfyUI: укажите имя .gguf или .safetensors в настройках")
    }

    private suspend fun fetchFromComfyUi(
        cfg: MediaGenConfig,
        prompt: String,
        style: String,
        width: Int,
        height: Int,
        seed: Int,
        onProgress: (String) -> Unit,
    ): ByteArray {
        val rawUrl = cfg.customImageUrl.trim()
        require(rawUrl.startsWith("http://", true) || rawUrl.startsWith("https://", true)) {
            "Укажите URL вашего ComfyUI сервера (например, https://.../comfy)"
        }
        val baseUrl = normalizeComfyBaseUrl(rawUrl)
        val headers = buildAuthHeaders(cfg.apiKey)
        val ckpt = resolveComfyCheckpoint(baseUrl, cfg, headers, onProgress)
        val autoPreset = autoConfigureComfyModel(ckpt)
        val isPony = cfg.comfyPonyClipSkip2 ||
            ckpt.contains("pony", ignoreCase = true) ||
            ckpt.contains("cyberrealistic", ignoreCase = true)
        val resolvedVae = cfg.comfyVaeName.trim().ifBlank {
            if (ckpt.endsWith(".gguf", ignoreCase = true)) autoPreset.vaeName else ""
        }
        val resolvedClip1 = cfg.comfyClip1Name.trim().ifBlank {
            if (ckpt.endsWith(".gguf", ignoreCase = true)) autoPreset.clip1Name else ""
        }
        val resolvedClip2 = cfg.comfyClip2Name.trim().ifBlank {
            if (ckpt.endsWith(".gguf", ignoreCase = true)) autoPreset.clip2Name else ""
        }

        val workflowJson = buildComfyUiWorkflowJson(
            prompt = prompt,
            negativePrompt = cfg.negativePrompt,
            style = style,
            width = width,
            height = height,
            steps = cfg.steps,
            cfgScale = cfg.cfgScale,
            seed = seed,
            checkpointName = ckpt,
            usePonyClipSkip2 = isPony && !ckpt.contains("flux", ignoreCase = true),
            vaeName = resolvedVae,
            clip1Name = resolvedClip1,
            clip2Name = resolvedClip2,
        )

        onProgress("Отправляю задачу в ComfyUI ($ckpt)…")
        val submitResp = postBytes("$baseUrl/prompt", workflowJson, headers, readTimeoutMs = 30_000)
            .toString(Charsets.UTF_8)
        val promptId = parseComfyPromptId(submitResp)

        onProgress("ComfyUI рендерит изображение…")
        val pollStart = System.currentTimeMillis()
        var imageRef: ComfyImageRef? = null
        while (System.currentTimeMillis() - pollStart < 120_000L) {
            currentCoroutineContext().ensureActive()
            val histJson = getBytes("$baseUrl/history/$promptId", headers, readTimeoutMs = 15_000)
                .toString(Charsets.UTF_8)
            imageRef = parseComfyHistoryImageRef(histJson, promptId)
            if (imageRef != null) break
            delay(800)
        }

        val ref = imageRef ?: error("ComfyUI не завершил генерацию за 120 секунд")
        val qFilename = URLEncoder.encode(ref.filename, "UTF-8")
        val qSubfolder = URLEncoder.encode(ref.subfolder, "UTF-8")
        val qType = URLEncoder.encode(ref.type, "UTF-8")
        return getBytes(
            "$baseUrl/view?filename=$qFilename&subfolder=$qSubfolder&type=$qType",
            headers,
            readTimeoutMs = 30_000,
        )
    }

    private suspend fun fetchVideoFromComfyUiWan22(
        cfg: MediaGenConfig,
        imagePrompt: String,
        motionPrompt: String,
        style: String,
        videoWidth: Int,
        videoHeight: Int,
        seed: Int,
        onProgress: (String) -> Unit,
    ): ByteArray {
        val rawUrl = cfg.customImageUrl.trim()
        require(rawUrl.startsWith("http://", true) || rawUrl.startsWith("https://", true)) {
            "Укажите URL вашего ComfyUI сервера в настройках Студии"
        }
        val baseUrl = normalizeComfyBaseUrl(rawUrl)
        val headers = buildAuthHeaders(cfg.apiKey)
        val ckpt = resolveComfyCheckpoint(baseUrl, cfg, headers, onProgress)
        val isPony = cfg.comfyPonyClipSkip2 ||
            ckpt.contains("pony", ignoreCase = true) ||
            ckpt.contains("cyberrealistic", ignoreCase = true)

        val (imgW, imgH) = if (isPony) {
            when {
                videoWidth > videoHeight -> 1216 to 832
                videoHeight > videoWidth -> 832 to 1216
                else -> 1024 to 1024
            }
        } else {
            when {
                videoWidth > videoHeight -> 768 to 432
                videoHeight > videoWidth -> 432 to 768
                else -> 768 to 768
            }
        }

        val workflowJson = buildComfyUiPonyToWan22I2vWorkflowJson(
            imagePrompt = imagePrompt,
            motionPrompt = motionPrompt,
            negativePrompt = cfg.negativePrompt,
            style = style,
            imageWidth = imgW,
            imageHeight = imgH,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            frames = cfg.comfyVideoFrames,
            imageSteps = cfg.steps,
            imageCfg = cfg.cfgScale,
            seed = seed,
            checkpointName = ckpt,
            ponyClipSkip2 = isPony,
            wanHighNoiseModel = cfg.comfyWanHighNoiseModel,
            wanLowNoiseModel = cfg.comfyWanLowNoiseModel,
            wanTextEncoder = cfg.comfyWanTextEncoder,
            wanVae = cfg.comfyWanVae,
            turboLora = cfg.comfyWanTurboLora,
        )

        onProgress("Запускаю Pony XL -> Wan 2.2 I2V-14B в ComfyUI…")
        val submitResp = postBytes("$baseUrl/prompt", workflowJson, headers, readTimeoutMs = 30_000)
            .toString(Charsets.UTF_8)
        val promptId = parseComfyPromptId(submitResp)

        val pollStart = System.currentTimeMillis()
        var videoRef: ComfyImageRef? = null
        while (System.currentTimeMillis() - pollStart < 600_000L) {
            currentCoroutineContext().ensureActive()
            val elapsedSec = ((System.currentTimeMillis() - pollStart) / 1000L).toInt()
            onProgress("Wan 2.2 I2V рендерит видео… ($elapsedSec с)")
            val histJson = getBytes("$baseUrl/history/$promptId", headers, readTimeoutMs = 15_000)
                .toString(Charsets.UTF_8)
            videoRef = parseComfyHistoryMediaRef(histJson, promptId, preferVideo = true)
            if (videoRef != null) break
            delay(2_000)
        }

        val ref = videoRef ?: error("Wan 2.2 I2V не завершил генерацию за 10 минут")
        val qFilename = URLEncoder.encode(ref.filename, "UTF-8")
        val qSubfolder = URLEncoder.encode(ref.subfolder, "UTF-8")
        val qType = URLEncoder.encode(ref.type, "UTF-8")
        onProgress("Скачиваю готовое MP4 видео…")
        return getBytes(
            "$baseUrl/view?filename=$qFilename&subfolder=$qSubfolder&type=$qType",
            headers,
            readTimeoutMs = 90_000,
        )
    }

    private fun buildAuthHeaders(apiKey: String): Map<String, String> =
        if (apiKey.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer ${apiKey.trim()}")

    private fun getBytes(
        urlStr: String,
        headers: Map<String, String>,
        readTimeoutMs: Int,
        expectImage: Boolean = false,
    ): ByteArray {
        var lastErr: Throwable? = null
        for (attempt in 1..2) {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = readTimeoutMs
                setRequestProperty("User-Agent", "Jarvis-Android/1.0")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) {
                    val errText = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    val retryAfter = parseRetryAfterSeconds(conn.getHeaderField("Retry-After"))
                    throw MediaHttpException(
                        statusCode = code,
                        retryAfterSeconds = retryAfter,
                        responseBody = errText,
                        message = "Сервер генерации вернул HTTP $code",
                    )
                }
                if (expectImage) {
                    val ctype = conn.contentType.orEmpty().lowercase()
                    if (ctype.startsWith("text/") || ctype.startsWith("application/json")) {
                        throw IllegalStateException("Сервер вернул не изображение (${conn.contentType})")
                    }
                }
                return conn.inputStream.use { it.readBytes() }
            } catch (err: MediaHttpException) {
                lastErr = err
                if (err.statusCode in 400..499 || attempt == 2) throw err
            } catch (err: Throwable) {
                lastErr = err
                if (attempt == 2) throw err
            } finally {
                conn.disconnect()
            }
        }
        throw lastErr ?: IllegalStateException("Ошибка загрузки медиа")
    }

    private fun postBytes(
        urlStr: String,
        bodyJson: String,
        headers: Map<String, String>,
        readTimeoutMs: Int,
    ): ByteArray {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = readTimeoutMs
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json, image/*, video/mp4")
            setRequestProperty("User-Agent", "Jarvis-Android/1.0")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            conn.outputStream.use { it.write(bodyJson.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val data = stream?.use { it.readBytes() } ?: ByteArray(0)
            if (code !in 200..299) {
                val errText = data.toString(Charsets.UTF_8)
                val retryAfter = parseRetryAfterSeconds(conn.getHeaderField("Retry-After"))
                throw MediaHttpException(
                    statusCode = code,
                    retryAfterSeconds = retryAfter,
                    responseBody = errText,
                    message = "Сервер генерации вернул HTTP $code: ${errText.take(140)}",
                )
            }
            return data
        } finally {
            conn.disconnect()
        }
    }

    private fun loadHistory(): List<GeneratedMediaItem> {
        if (!historyFile.isFile) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<GeneratedMediaItem>>() {}.type
            val parsed: List<GeneratedMediaItem>? = gson.fromJson(historyFile.readText(Charsets.UTF_8), type)
            parsed.orEmpty().filter { File(it.filePath).isFile }
        }.getOrElse { emptyList() }
    }

    private fun persistHistory() {
        runCatching {
            historyFile.parentFile?.mkdirs()
            historyFile.writeText(gson.toJson(_items.value), Charsets.UTF_8)
        }
    }

    companion object {
        const val DEFAULT_AI_HORDE_ANON_KEY = "0000000000"
        const val AI_HORDE_BASE_URL = "https://aihorde.net/api/v2"
        const val AI_HORDE_CLIENT_AGENT = "JarvisAndroid:1.0:github.com/sokfrut-tech/Gemeni"
        const val DEFAULT_LOCAL_DREAM_URL = "http://127.0.0.1:8081"
        const val DEFAULT_LOCAL_DREAM_NEGATIVE = "bad anatomy, extra fingers, deformed, blurry, low quality, worst quality"
        const val DEFAULT_PONY_CHECKPOINT = "CyberRealisticPony_V18.0_F16.safetensors"
        const val DEFAULT_PONY_NEGATIVE = "score_6, score_5, score_4, worst quality, low quality, lowres, bad anatomy, bad hands, signature, watermark"
        const val DEFAULT_WAN_HIGH_MODEL = "wan2.2_i2v_high_noise_14B_fp8_scaled.safetensors"
        const val DEFAULT_WAN_LOW_MODEL = "wan2.2_i2v_low_noise_14B_fp8_scaled.safetensors"
        const val DEFAULT_WAN_TEXT_ENCODER = "umt5_xxl_fp8_e4m3fn_scaled.safetensors"
        const val DEFAULT_WAN_VAE = "wan_2.1_vae.safetensors"
        const val DEFAULT_WAN_HIGH_LORA = "wan2.2_i2v_lightx2v_4steps_lora_v1_high_noise.safetensors"
        const val DEFAULT_WAN_LOW_LORA = "wan2.2_i2v_lightx2v_4steps_lora_v1_low_noise.safetensors"
        const val DEFAULT_WAN_NEGATIVE = "static image, overexposed, blurred details, worst quality, low quality, jpeg artifacts, deformed limbs, extra fingers, cluttered background"

        private val CYRILLIC_REGEX = Regex("[а-яА-ЯёЁ]")
        private val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mov", "mkv")
        private val gson = Gson()

        fun normalizeLocalDreamGenerateUrl(rawUrl: String): String {
            val clean = rawUrl.trim().ifBlank { DEFAULT_LOCAL_DREAM_URL }.trimEnd('/')
            return if (clean.endsWith("/generate", ignoreCase = true)) {
                clean
            } else {
                "$clean/generate"
            }
        }

        fun buildLocalDreamRequestJson(
            prompt: String,
            negativePrompt: String = DEFAULT_LOCAL_DREAM_NEGATIVE,
            style: String = "Фото",
            width: Int = 512,
            height: Int = 512,
            steps: Int = 24,
            cfgScale: Float = 7.0f,
            seed: Int = 42,
        ): String {
            val safeW = ((width.coerceIn(384, 768) + 32) / 64) * 64
            val safeH = ((height.coerceIn(384, 768) + 32) / 64) * 64
            val root = JsonObject().apply {
                addProperty("prompt", "${prompt.trim()}, ${styleSuffix(style)}")
                addProperty("negative_prompt", negativePrompt.trim().ifBlank { DEFAULT_LOCAL_DREAM_NEGATIVE })
                addProperty("steps", steps.coerceIn(4, 60))
                addProperty("cfg", cfgScale.coerceIn(1.0f, 20.0f))
                addProperty("width", safeW)
                addProperty("height", safeH)
                addProperty("seed", seed.coerceAtLeast(1))
                addProperty("scheduler", "dpm")
                addProperty("show_diffusion_process", false)
                addProperty("output_format", "jpeg")
            }
            return gson.toJson(root)
        }

        fun parseLocalDreamSseEvent(line: String): LocalDreamSseEvent? {
            val trimmed = line.trim()
            if (!trimmed.startsWith("data:")) return null
            val payload = trimmed.removePrefix("data:").trim()
            if (payload.isEmpty()) return null
            if (payload == "[DONE]") return LocalDreamSseEvent.Done

            return runCatching {
                val obj = JsonParser.parseString(payload).asJsonObject
                when (obj.get("type")?.asString?.lowercase()) {
                    "progress" -> LocalDreamSseEvent.Progress(
                        step = obj.get("step")?.asInt ?: 0,
                        totalSteps = (obj.get("total_steps")?.asInt ?: 1).coerceAtLeast(1),
                    )
                    "complete" -> {
                        val b64 = obj.get("image")?.asString?.takeIf { it.isNotBlank() }
                            ?: return@runCatching LocalDreamSseEvent.Error("Пустое изображение в ответе Local Dream")
                        LocalDreamSseEvent.Complete(
                            base64Image = b64,
                            format = obj.get("format")?.asString ?: "raw",
                            width = obj.get("width")?.asInt ?: 512,
                            height = obj.get("height")?.asInt ?: 512,
                            channels = obj.get("channels")?.asInt ?: 3,
                        )
                    }
                    "error" -> LocalDreamSseEvent.Error(
                        obj.get("message")?.asString ?: obj.get("error")?.asString ?: "Ошибка Local Dream"
                    )
                    else -> null
                }
            }.getOrNull()
        }

        private fun styleSuffix(style: String): String = when (style.lowercase()) {
            "фото" -> "photorealistic, ultra detailed, 8k, natural lighting"
            "кино" -> "cinematic film still, dramatic lighting, shallow depth of field, anamorphic"
            "киберпанк" -> "cyberpunk neon aesthetic, futuristic, volumetric light, ultra detailed"
            "аниме" -> "high quality anime artwork, vibrant colors, crisp lineart, studio quality"
            "3d арт" -> "octane 3d render, unreal engine 5 style, glossy volumetric lighting"
            else -> style
        }

        private fun nodeRef(id: String, outputIndex: Int = 0) = JsonArray().apply {
            add(id)
            add(outputIndex)
        }

        private fun comfyNode(classType: String, inputs: JsonObject) = JsonObject().apply {
            addProperty("class_type", classType)
            add("inputs", inputs)
        }

        fun normalizeComfyBaseUrl(rawUrl: String): String {
            var base = rawUrl.trim().trimEnd('/')
            for (suffix in listOf("/prompt", "/view", "/history")) {
                if (base.endsWith(suffix, ignoreCase = true)) {
                    base = base.dropLast(suffix.length).trimEnd('/')
                }
            }
            return base
        }

        fun normalizeA1111Txt2ImgUrl(rawUrl: String): String {
            val clean = rawUrl.trim().trimEnd('/')
            return if (clean.contains("/sdapi/", ignoreCase = true)) {
                clean
            } else {
                "$clean/sdapi/v1/txt2img"
            }
        }

        fun parseComfyNodeChoices(objectInfoJson: String, nodeClass: String, inputField: String): List<String> {
            return runCatching {
                val root = JsonParser.parseString(objectInfoJson).asJsonObject
                val arr = root.getAsJsonObject(nodeClass)
                    ?.getAsJsonObject("input")
                    ?.getAsJsonObject("required")
                    ?.getAsJsonArray(inputField)
                    ?: return emptyList()
                val choices = arr.get(0)?.asJsonArray ?: return emptyList()
                buildList {
                    for (idx in 0 until choices.size()) {
                        choices[idx]?.asString?.takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                }
            }.getOrDefault(emptyList())
        }

        fun parseComfyFirstCheckpoint(objectInfoJson: String): String? =
            parseComfyNodeChoices(objectInfoJson, "CheckpointLoaderSimple", "ckpt_name").firstOrNull()

        /**
         * Automatically detects diffusion architecture from a GGUF/safetensors filename
         * and wires compatible VAE, CLIP/T5 encoders, steps, CFG scale, and negative prompt.
         */
        fun autoConfigureComfyModel(
            modelName: String,
            availableVaes: List<String> = emptyList(),
            availableClips: List<String> = emptyList(),
        ): ComfyAutoWireResult {
            val cleanModel = modelName.trim()
            val lower = cleanModel.lowercase()
            val isGguf = lower.endsWith(".gguf")

            fun pickMatching(list: List<String>, keywords: List<String>, fallback: String): String {
                for (kw in keywords) {
                    list.firstOrNull {
                        it.substringAfterLast('/').substringAfterLast('\\').equals(kw, ignoreCase = true)
                    }?.let { return it }
                }
                for (kw in keywords) {
                    list.firstOrNull { it.lowercase().contains(kw) }?.let { return it }
                }
                return list.firstOrNull() ?: fallback
            }

            return when {
                lower.contains("flux") -> {
                    val vae = pickMatching(availableVaes, listOf("ae.safetensors", "flux_vae", "flux"), "ae.safetensors")
                    val clip1 = pickMatching(availableClips, listOf("clip_l", "clipl"), "clip_l.safetensors")
                    val clip2 = pickMatching(availableClips, listOf("t5xxl", "t5_xxl", "t5-"), "t5xxl_fp8_e4m3fn.safetensors")
                    val isSchnell = lower.contains("schnell")
                    ComfyAutoWireResult(
                        architecture = if (isGguf) "FLUX_GGUF" else "FLUX",
                        modelName = cleanModel,
                        vaeName = vae,
                        clip1Name = clip1,
                        clip2Name = clip2,
                        steps = if (isSchnell) 4 else 20,
                        cfgScale = 1.0f,
                        usePonyClipSkip2 = false,
                        negativePrompt = "",
                        summaryRu = "Автонастройка FLUX${if (isGguf) " GGUF" else ""}: VAE=$vae, CLIP=$clip1 + $clip2, CFG=1.0",
                    )
                }

                lower.contains("sd3") -> {
                    val vae = pickMatching(availableVaes, listOf("sd3", "ae.safetensors"), "sd3_vae.safetensors")
                    val clip1 = pickMatching(availableClips, listOf("clip_l"), "clip_l.safetensors")
                    val clip2 = pickMatching(availableClips, listOf("clip_g", "t5xxl"), "clip_g.safetensors")
                    ComfyAutoWireResult(
                        architecture = if (isGguf) "SD3_GGUF" else "SD3",
                        modelName = cleanModel,
                        vaeName = vae,
                        clip1Name = clip1,
                        clip2Name = clip2,
                        steps = 28,
                        cfgScale = 4.5f,
                        usePonyClipSkip2 = false,
                        negativePrompt = "lowres, blurry, deformed",
                        summaryRu = "Автонастройка SD3${if (isGguf) " GGUF" else ""}: VAE=$vae, CFG=4.5",
                    )
                }

                lower.contains("pony") || lower.contains("cyberrealistic") -> {
                    val vae = if (isGguf || availableVaes.any { it.lowercase().contains("sdxl") }) {
                        pickMatching(availableVaes, listOf("sdxl_vae", "sdxl", "pony"), if (isGguf) "sdxl_vae.safetensors" else "")
                    } else ""
                    val clip1 = if (isGguf) pickMatching(availableClips, listOf("clip_l"), "clip_l.safetensors") else ""
                    val clip2 = if (isGguf) pickMatching(availableClips, listOf("clip_g"), "clip_g.safetensors") else ""
                    ComfyAutoWireResult(
                        architecture = if (isGguf) "PONY_GGUF" else "PONY_XL",
                        modelName = cleanModel,
                        vaeName = vae,
                        clip1Name = clip1,
                        clip2Name = clip2,
                        steps = 30,
                        cfgScale = 5.5f,
                        usePonyClipSkip2 = true,
                        negativePrompt = DEFAULT_PONY_NEGATIVE,
                        summaryRu = "Автонастройка Pony XL: Clip Skip 2, 30 шагов, CFG=5.5${if (vae.isNotBlank()) ", VAE=$vae" else ""}",
                    )
                }

                lower.contains("xl") || lower.contains("juggernaut") || lower.contains("illustrious") -> {
                    val vae = if (isGguf || availableVaes.any { it.lowercase().contains("sdxl") }) {
                        pickMatching(availableVaes, listOf("sdxl_vae", "sdxl"), if (isGguf) "sdxl_vae.safetensors" else "")
                    } else ""
                    val clip1 = if (isGguf) pickMatching(availableClips, listOf("clip_l"), "clip_l.safetensors") else ""
                    val clip2 = if (isGguf) pickMatching(availableClips, listOf("clip_g"), "clip_g.safetensors") else ""
                    ComfyAutoWireResult(
                        architecture = if (isGguf) "SDXL_GGUF" else "SDXL",
                        modelName = cleanModel,
                        vaeName = vae,
                        clip1Name = clip1,
                        clip2Name = clip2,
                        steps = 28,
                        cfgScale = 6.5f,
                        usePonyClipSkip2 = false,
                        negativePrompt = "lowres, blurry, bad anatomy, watermark, text",
                        summaryRu = "Автонастройка SDXL: 28 шагов, CFG=6.5${if (vae.isNotBlank()) ", VAE=$vae" else ""}",
                    )
                }

                else -> {
                    val vae = if (isGguf) {
                        pickMatching(availableVaes, listOf("vae-ft-mse", "ae.safetensors", "sdxl_vae"), "ae.safetensors")
                    } else {
                        availableVaes.firstOrNull { it.lowercase().contains("vae-ft-mse") } ?: ""
                    }
                    val clip1 = if (isGguf) pickMatching(availableClips, listOf("clip_l"), "clip_l.safetensors") else ""
                    val clip2 = if (isGguf) pickMatching(availableClips, listOf("t5xxl", "clip_g"), "") else ""
                    ComfyAutoWireResult(
                        architecture = if (isGguf) "GENERIC_GGUF" else "SD15",
                        modelName = cleanModel,
                        vaeName = vae,
                        clip1Name = clip1,
                        clip2Name = clip2,
                        steps = 24,
                        cfgScale = 7.0f,
                        usePonyClipSkip2 = false,
                        negativePrompt = "lowres, blurry, bad anatomy, watermark, text",
                        summaryRu = "Автонастройка ${if (isGguf) "GGUF" else "SD 1.5"}: 24 шага, CFG=7.0${if (vae.isNotBlank()) ", VAE=$vae" else ""}",
                    )
                }
            }
        }

        fun buildComfyUiWorkflowJson(
            prompt: String,
            negativePrompt: String,
            style: String,
            width: Int,
            height: Int,
            steps: Int,
            cfgScale: Float,
            seed: Int,
            checkpointName: String,
            usePonyClipSkip2: Boolean = false,
            vaeName: String = "",
            clip1Name: String = "",
            clip2Name: String = "",
        ): String {
            val safeW = ((width.coerceIn(256, 1536) + 7) / 8) * 8
            val safeH = ((height.coerceIn(256, 1536) + 7) / 8) * 8
            val styledPrompt = "${prompt.trim()}, ${styleSuffix(style)}"
            val isGguf = checkpointName.trim().endsWith(".gguf", ignoreCase = true)
            val isFlux = checkpointName.contains("flux", ignoreCase = true)
            val effectivePony = usePonyClipSkip2 && !isFlux
            val finalPos = if (effectivePony && !styledPrompt.contains("score_9", ignoreCase = true)) {
                "score_9, score_8_up, score_7_up, $styledPrompt"
            } else {
                styledPrompt
            }
            val finalNeg = when {
                isFlux -> ""
                effectivePony && negativePrompt.isBlank() -> DEFAULT_PONY_NEGATIVE
                else -> negativePrompt.trim()
            }

            val graph = JsonObject().apply {
                val modelRef: JsonArray
                val baseClipRef: JsonArray
                val vaeRef: JsonArray

                if (isGguf) {
                    add("4", comfyNode("UnetLoaderGGUF", JsonObject().apply {
                        addProperty("unet_name", checkpointName.trim())
                    }))
                    modelRef = nodeRef("4", 0)

                    val resolvedVae = vaeName.trim().ifBlank { if (isFlux) "ae.safetensors" else "sdxl_vae.safetensors" }
                    add("12", comfyNode("VAELoader", JsonObject().apply {
                        addProperty("vae_name", resolvedVae)
                    }))
                    vaeRef = nodeRef("12", 0)

                    val resolvedClip1 = clip1Name.trim().ifBlank { "clip_l.safetensors" }
                    val resolvedClip2 = clip2Name.trim()
                    if (resolvedClip2.isNotBlank()) {
                        add("13", comfyNode("DualCLIPLoader", JsonObject().apply {
                            addProperty("clip_name1", resolvedClip1)
                            addProperty("clip_name2", resolvedClip2)
                            addProperty("type", when {
                                isFlux -> "flux"
                                checkpointName.contains("sd3", ignoreCase = true) -> "sd3"
                                else -> "sdxl"
                            })
                        }))
                    } else {
                        add("13", comfyNode("CLIPLoader", JsonObject().apply {
                            addProperty("clip_name", resolvedClip1)
                            addProperty("type", if (isFlux) "flux" else "stable_diffusion")
                        }))
                    }
                    baseClipRef = nodeRef("13", 0)
                } else {
                    add("4", comfyNode("CheckpointLoaderSimple", JsonObject().apply {
                        addProperty("ckpt_name", checkpointName)
                    }))
                    modelRef = nodeRef("4", 0)
                    baseClipRef = nodeRef("4", 1)
                    if (vaeName.trim().isNotBlank()) {
                        add("12", comfyNode("VAELoader", JsonObject().apply {
                            addProperty("vae_name", vaeName.trim())
                        }))
                        vaeRef = nodeRef("12", 0)
                    } else {
                        vaeRef = nodeRef("4", 2)
                    }
                }

                val clipNodeRef = if (effectivePony) {
                    add("11", comfyNode("CLIPSetLastLayer", JsonObject().apply {
                        add("clip", baseClipRef)
                        addProperty("stop_at_clip_layer", -2)
                    }))
                    nodeRef("11", 0)
                } else {
                    baseClipRef
                }

                add("5", comfyNode("EmptyLatentImage", JsonObject().apply {
                    addProperty("width", safeW)
                    addProperty("height", safeH)
                    addProperty("batch_size", 1)
                }))
                add("6", comfyNode("CLIPTextEncode", JsonObject().apply {
                    addProperty("text", finalPos)
                    add("clip", clipNodeRef)
                }))
                add("7", comfyNode("CLIPTextEncode", JsonObject().apply {
                    addProperty("text", finalNeg)
                    add("clip", clipNodeRef)
                }))
                add("3", comfyNode("KSampler", JsonObject().apply {
                    addProperty("seed", seed.coerceAtLeast(1))
                    addProperty("steps", steps.coerceIn(1, 100))
                    addProperty("cfg", if (isFlux) 1.0f else cfgScale.coerceIn(1.0f, 30.0f))
                    addProperty("sampler_name", when {
                        isFlux -> "euler"
                        effectivePony -> "dpmpp_2m"
                        else -> "euler"
                    })
                    addProperty("scheduler", when {
                        isFlux -> "simple"
                        effectivePony -> "karras"
                        else -> "normal"
                    })
                    addProperty("denoise", 1.0)
                    add("model", modelRef)
                    add("positive", nodeRef("6", 0))
                    add("negative", nodeRef("7", 0))
                    add("latent_image", nodeRef("5", 0))
                }))
                add("8", comfyNode("VAEDecode", JsonObject().apply {
                    add("samples", nodeRef("3", 0))
                    add("vae", vaeRef)
                }))
                add("9", comfyNode("SaveImage", JsonObject().apply {
                    addProperty("filename_prefix", when {
                        isGguf -> "JarvisGguf"
                        effectivePony -> "JarvisPony"
                        else -> "JarvisStudio"
                    })
                    add("images", nodeRef("8", 0))
                }))
            }

            val payload = JsonObject().apply {
                add("prompt", graph)
                addProperty("client_id", "jarvis-android")
            }
            return gson.toJson(payload)
        }

        fun buildComfyUiPonyToWan22I2vWorkflowJson(
            imagePrompt: String,
            motionPrompt: String = "",
            negativePrompt: String = DEFAULT_PONY_NEGATIVE,
            style: String = "Фото",
            imageWidth: Int = 896,
            imageHeight: Int = 1152,
            videoWidth: Int = 640,
            videoHeight: Int = 640,
            frames: Int = 49,
            imageSteps: Int = 30,
            imageCfg: Float = 5.5f,
            seed: Int = 42,
            checkpointName: String = DEFAULT_PONY_CHECKPOINT,
            ponyClipSkip2: Boolean = true,
            wanHighNoiseModel: String = DEFAULT_WAN_HIGH_MODEL,
            wanLowNoiseModel: String = DEFAULT_WAN_LOW_MODEL,
            wanTextEncoder: String = DEFAULT_WAN_TEXT_ENCODER,
            wanVae: String = DEFAULT_WAN_VAE,
            turboLora: Boolean = true,
            wanHighLora: String = DEFAULT_WAN_HIGH_LORA,
            wanLowLora: String = DEFAULT_WAN_LOW_LORA,
        ): String {
            val safeImgW = ((imageWidth.coerceIn(512, 1536) + 7) / 8) * 8
            val safeImgH = ((imageHeight.coerceIn(512, 1536) + 7) / 8) * 8
            val safeVidW = ((videoWidth.coerceIn(320, 960) + 15) / 16) * 16
            val safeVidH = ((videoHeight.coerceIn(320, 960) + 15) / 16) * 16
            val rawFrames = frames.coerceIn(17, 121)
            val alignedFrames = ((rawFrames - 1) / 4) * 4 + 1

            val styledImg = "${imagePrompt.trim()}, ${styleSuffix(style)}"
            val finalImgPos = if (ponyClipSkip2 && !styledImg.contains("score_9", ignoreCase = true)) {
                "score_9, score_8_up, score_7_up, $styledImg"
            } else {
                styledImg
            }
            val finalImgNeg = negativePrompt.trim().ifBlank { DEFAULT_PONY_NEGATIVE }
            val resolvedMotion = motionPrompt.trim().ifBlank {
                "${imagePrompt.trim()}, smooth cinematic camera motion, subtle natural movement"
            }
            val ponyClipRef = if (ponyClipSkip2) nodeRef("11", 0) else nodeRef("4", 1)

            val wanSteps = if (turboLora) 4 else 20
            val splitStep = if (turboLora) 2 else 10
            val wanCfg = if (turboLora) 1.0f else 3.5f

            val graph = JsonObject().apply {
                // Stage 1: CyberRealistic Pony XL keyframe generation
                add("4", comfyNode("CheckpointLoaderSimple", JsonObject().apply {
                    addProperty("ckpt_name", checkpointName.trim().ifBlank { DEFAULT_PONY_CHECKPOINT })
                }))
                if (ponyClipSkip2) {
                    add("11", comfyNode("CLIPSetLastLayer", JsonObject().apply {
                        add("clip", nodeRef("4", 1))
                        addProperty("stop_at_clip_layer", -2)
                    }))
                }
                add("5", comfyNode("EmptyLatentImage", JsonObject().apply {
                    addProperty("width", safeImgW)
                    addProperty("height", safeImgH)
                    addProperty("batch_size", 1)
                }))
                add("6", comfyNode("CLIPTextEncode", JsonObject().apply {
                    addProperty("text", finalImgPos)
                    add("clip", ponyClipRef)
                }))
                add("7", comfyNode("CLIPTextEncode", JsonObject().apply {
                    addProperty("text", finalImgNeg)
                    add("clip", ponyClipRef)
                }))
                add("3", comfyNode("KSampler", JsonObject().apply {
                    addProperty("seed", seed.coerceAtLeast(1))
                    addProperty("steps", imageSteps.coerceIn(1, 100))
                    addProperty("cfg", imageCfg.coerceIn(1.0f, 30.0f))
                    addProperty("sampler_name", if (ponyClipSkip2) "dpmpp_2m" else "euler")
                    addProperty("scheduler", if (ponyClipSkip2) "karras" else "normal")
                    addProperty("denoise", 1.0)
                    add("model", nodeRef("4", 0))
                    add("positive", nodeRef("6", 0))
                    add("negative", nodeRef("7", 0))
                    add("latent_image", nodeRef("5", 0))
                }))
                add("16", comfyNode("VAEDecode", JsonObject().apply {
                    add("samples", nodeRef("3", 0))
                    add("vae", nodeRef("4", 2))
                }))
                add("17", comfyNode("SaveImage", JsonObject().apply {
                    addProperty("filename_prefix", "JarvisPonyKeyframe")
                    add("images", nodeRef("16", 0))
                }))

                // Stage 2: Wan 2.2 I2V-14B High/Low Noise MoE image-to-video
                add("20", comfyNode("UNETLoader", JsonObject().apply {
                    addProperty("unet_name", wanHighNoiseModel.trim().ifBlank { DEFAULT_WAN_HIGH_MODEL })
                    addProperty("weight_dtype", "default")
                }))
                add("21", comfyNode("UNETLoader", JsonObject().apply {
                    addProperty("unet_name", wanLowNoiseModel.trim().ifBlank { DEFAULT_WAN_LOW_MODEL })
                    addProperty("weight_dtype", "default")
                }))
                if (turboLora) {
                    add("22", comfyNode("LoraLoaderModelOnly", JsonObject().apply {
                        add("model", nodeRef("20", 0))
                        addProperty("lora_name", wanHighLora.trim().ifBlank { DEFAULT_WAN_HIGH_LORA })
                        addProperty("strength_model", 1.0)
                    }))
                    add("23", comfyNode("LoraLoaderModelOnly", JsonObject().apply {
                        add("model", nodeRef("21", 0))
                        addProperty("lora_name", wanLowLora.trim().ifBlank { DEFAULT_WAN_LOW_LORA })
                        addProperty("strength_model", 1.0)
                    }))
                }
                add("24", comfyNode("ModelSamplingSD3", JsonObject().apply {
                    add("model", nodeRef(if (turboLora) "22" else "20", 0))
                    addProperty("shift", 5.0)
                }))
                add("25", comfyNode("ModelSamplingSD3", JsonObject().apply {
                    add("model", nodeRef(if (turboLora) "23" else "21", 0))
                    addProperty("shift", 5.0)
                }))
                add("26", comfyNode("CLIPLoader", JsonObject().apply {
                    addProperty("clip_name", wanTextEncoder.trim().ifBlank { DEFAULT_WAN_TEXT_ENCODER })
                    addProperty("type", "wan")
                    addProperty("device", "default")
                }))
                add("27", comfyNode("VAELoader", JsonObject().apply {
                    addProperty("vae_name", wanVae.trim().ifBlank { DEFAULT_WAN_VAE })
                }))
                add("28", comfyNode("CLIPTextEncode", JsonObject().apply {
                    addProperty("text", resolvedMotion)
                    add("clip", nodeRef("26", 0))
                }))
                add("29", comfyNode("CLIPTextEncode", JsonObject().apply {
                    addProperty("text", DEFAULT_WAN_NEGATIVE)
                    add("clip", nodeRef("26", 0))
                }))
                add("30", comfyNode("WanImageToVideo", JsonObject().apply {
                    add("positive", nodeRef("28", 0))
                    add("negative", nodeRef("29", 0))
                    add("vae", nodeRef("27", 0))
                    add("start_image", nodeRef("16", 0))
                    addProperty("width", safeVidW)
                    addProperty("height", safeVidH)
                    addProperty("length", alignedFrames)
                    addProperty("batch_size", 1)
                }))
                add("31", comfyNode("KSamplerAdvanced", JsonObject().apply {
                    addProperty("add_noise", "enable")
                    addProperty("noise_seed", seed.coerceAtLeast(1))
                    addProperty("steps", wanSteps)
                    addProperty("cfg", wanCfg)
                    addProperty("sampler_name", "euler")
                    addProperty("scheduler", "simple")
                    addProperty("start_at_step", 0)
                    addProperty("end_at_step", splitStep)
                    addProperty("return_with_leftover_noise", "enable")
                    add("model", nodeRef("24", 0))
                    add("positive", nodeRef("30", 0))
                    add("negative", nodeRef("30", 1))
                    add("latent_image", nodeRef("30", 2))
                }))
                add("32", comfyNode("KSamplerAdvanced", JsonObject().apply {
                    addProperty("add_noise", "disable")
                    addProperty("noise_seed", 0)
                    addProperty("steps", wanSteps)
                    addProperty("cfg", wanCfg)
                    addProperty("sampler_name", "euler")
                    addProperty("scheduler", "simple")
                    addProperty("start_at_step", splitStep)
                    addProperty("end_at_step", 10000)
                    addProperty("return_with_leftover_noise", "disable")
                    add("model", nodeRef("25", 0))
                    add("positive", nodeRef("30", 0))
                    add("negative", nodeRef("30", 1))
                    add("latent_image", nodeRef("31", 0))
                }))
                add("33", comfyNode("VAEDecode", JsonObject().apply {
                    add("samples", nodeRef("32", 0))
                    add("vae", nodeRef("27", 0))
                }))
                add("34", comfyNode("CreateVideo", JsonObject().apply {
                    add("images", nodeRef("33", 0))
                    addProperty("fps", 16.0)
                }))
                add("35", comfyNode("SaveVideo", JsonObject().apply {
                    add("video", nodeRef("34", 0))
                    addProperty("filename_prefix", "video/JarvisWan22")
                    addProperty("format", "mp4")
                    addProperty("codec", "auto")
                }))
            }

            val payload = JsonObject().apply {
                add("prompt", graph)
                addProperty("client_id", "jarvis-android")
            }
            return gson.toJson(payload)
        }

        fun parseComfyPromptId(submitJson: String): String {
            val root = JsonParser.parseString(submitJson).asJsonObject
            return root.get("prompt_id")?.asString?.takeIf { it.isNotBlank() }
                ?: error("ComfyUI не вернул prompt_id")
        }

        fun parseComfyHistoryMediaRef(
            historyJson: String,
            promptId: String,
            preferVideo: Boolean,
        ): ComfyImageRef? {
            val root = JsonParser.parseString(historyJson).asJsonObject
            val entry = root.getAsJsonObject(promptId) ?: return null
            val outputs = entry.getAsJsonObject("outputs") ?: return null
            val keys = if (preferVideo) listOf("videos", "gifs", "images") else listOf("images", "gifs", "videos")
            for (nodeKey in outputs.keySet()) {
                val nodeObj = outputs.getAsJsonObject(nodeKey) ?: continue
                for (arrayKey in keys) {
                    val arr = nodeObj.getAsJsonArray(arrayKey) ?: continue
                    for (idx in 0 until arr.size()) {
                        val item = arr[idx].asJsonObject
                        val filename = item.get("filename")?.asString?.takeIf { it.isNotBlank() } ?: continue
                        val ext = filename.substringAfterLast('.', "").lowercase()
                        if (preferVideo && ext !in VIDEO_EXTENSIONS) continue
                        val subfolder = item.get("subfolder")?.asString ?: ""
                        val type = item.get("type")?.asString ?: "output"
                        return ComfyImageRef(filename = filename, subfolder = subfolder, type = type)
                    }
                }
            }
            return null
        }

        fun parseComfyHistoryImageRef(historyJson: String, promptId: String): ComfyImageRef? =
            parseComfyHistoryMediaRef(historyJson, promptId, preferVideo = false)

        private fun buildPollinationsPathAndQuery(
            prompt: String,
            style: String,
            width: Int,
            height: Int,
            seed: Int,
            model: String,
        ): String {
            val fullPrompt = "${prompt.trim()}, ${styleSuffix(style)}"
            val encoded = URLEncoder.encode(fullPrompt, "UTF-8").replace("+", "%20")
            val modelParam = model.trim().takeIf { it.isNotEmpty() }
                ?.let { "&model=${URLEncoder.encode(it, "UTF-8")}" }
                .orEmpty()
            return "$encoded?width=$width&height=$height&seed=$seed&nologo=true$modelParam"
        }

        fun buildPollinationsImageUrl(
            prompt: String,
            style: String = "Фото",
            width: Int = 768,
            height: Int = 768,
            seed: Int = 42,
            model: String = "",
        ): String = "https://image.pollinations.ai/prompt/${buildPollinationsPathAndQuery(prompt, style, width, height, seed, model)}"

        fun buildPollinationsAuthenticatedUrl(
            prompt: String,
            style: String = "Фото",
            width: Int = 768,
            height: Int = 768,
            seed: Int = 42,
            model: String = "",
        ): String = "https://gen.pollinations.ai/image/${buildPollinationsPathAndQuery(prompt, style, width, height, seed, model)}"

        fun parseRetryAfterSeconds(headerValue: String?): Int? =
            headerValue?.trim()?.toIntOrNull()?.takeIf { it > 0 }

        fun computeAiHordeDimensions(width: Int, height: Int): Pair<Int, Int> = when {
            width > height -> 640 to 384
            height > width -> 384 to 640
            else -> 512 to 512
        }

        fun aiHordePreferredModels(style: String, customModel: String = ""): List<String> {
            val custom = customModel.trim()
            if (custom.isNotEmpty()) return listOf(custom)
            return if (style.equals("Аниме", ignoreCase = true)) {
                listOf("Flat-2D Animerge", "Rev Animated", "Deliberate", "stable_diffusion")
            } else {
                listOf("Deliberate", "ICBINP - I Can't Believe It's Not Photography", "AbsoluteReality", "Rev Animated", "stable_diffusion")
            }
        }

        fun buildAiHordeRequestJson(
            prompt: String,
            negativePrompt: String = DEFAULT_LOCAL_DREAM_NEGATIVE,
            style: String = "Фото",
            width: Int = 512,
            height: Int = 512,
            steps: Int = 20,
            cfgScale: Float = 7.0f,
            seed: Int = 42,
            customModel: String = "",
            includeModels: Boolean = true,
            uncensoredMode: Boolean = false,
        ): String {
            val safeW = ((width.coerceIn(384, 768) + 32) / 64) * 64
            val safeH = ((height.coerceIn(384, 768) + 32) / 64) * 64
            val neg = negativePrompt.trim().ifBlank { DEFAULT_LOCAL_DREAM_NEGATIVE }
            val combinedPrompt = "${prompt.trim()}, ${styleSuffix(style)} ### $neg"
            val params = JsonObject().apply {
                addProperty("width", safeW)
                addProperty("height", safeH)
                addProperty("steps", steps.coerceIn(12, 28))
                addProperty("cfg_scale", cfgScale.coerceIn(3.0f, 15.0f))
                addProperty("sampler_name", "k_euler_a")
                addProperty("karras", true)
                addProperty("n", 1)
                if (seed > 0) {
                    addProperty("seed", seed.toString())
                }
            }
            val root = JsonObject().apply {
                addProperty("prompt", combinedPrompt)
                add("params", params)
                addProperty("nsfw", uncensoredMode)
                addProperty("censor_nsfw", false)
                addProperty("r2", true)
                addProperty("shared", false)
                if (includeModels) {
                    val modelsArr = JsonArray()
                    aiHordePreferredModels(style, customModel).forEach { modelsArr.add(it) }
                    add("models", modelsArr)
                }
            }
            return gson.toJson(root)
        }

        fun parseAiHordeSubmitId(json: String): String {
            val root = JsonParser.parseString(json).asJsonObject
            return root.get("id")?.asString?.takeIf { it.isNotBlank() }
                ?: error("AI Horde не вернул ID задачи")
        }

        fun parseAiHordeCheckStatus(json: String): AiHordeCheckStatus {
            val root = JsonParser.parseString(json).asJsonObject
            return AiHordeCheckStatus(
                done = root.get("done")?.asBoolean ?: false,
                faulted = root.get("faulted")?.asBoolean ?: false,
                isPossible = root.get("is_possible")?.asBoolean ?: true,
                waitTime = root.get("wait_time")?.asInt ?: 0,
                queuePosition = root.get("queue_position")?.asInt ?: 0,
                processing = root.get("processing")?.asInt ?: 0,
            )
        }

        fun parseAiHordeCompletedImage(json: String): AiHordeImagePayload {
            val root = JsonParser.parseString(json).asJsonObject
            val gens = root.getAsJsonArray("generations")
                ?: error("AI Horde не вернул массив generations")
            if (gens.size() == 0) error("AI Horde вернул пустой список изображений")
            val first = gens[0].asJsonObject
            val rawImg = first.get("img")?.asString?.trim()?.takeIf { it.isNotBlank() }
                ?: error("AI Horde вернул пустое поле img")
            return if (rawImg.startsWith("https://", ignoreCase = true) || rawImg.startsWith("http://", ignoreCase = true)) {
                AiHordeImagePayload.RemoteUrl(rawImg)
            } else {
                AiHordeImagePayload.Base64Data(rawImg.substringAfter("base64,"))
            }
        }

        fun parseMyMemoryTranslation(json: String): String? = runCatching {
            val root = JsonParser.parseString(json).asJsonObject
            val statusCode = root.get("responseStatus")?.asString?.toDoubleOrNull()?.toInt()
            if (statusCode != null && statusCode != 200) return null
            val translated = root.getAsJsonObject("responseData")
                ?.get("translatedText")
                ?.asString
                ?.trim()
                ?: return null
            if (translated.isBlank() ||
                translated.startsWith("MYMEMORY WARNING", ignoreCase = true) ||
                translated.startsWith("PLEASE SELECT", ignoreCase = true) ||
                translated.startsWith("QUERY LENGTH LIMIT", ignoreCase = true)
            ) {
                null
            } else {
                translated
            }
        }.getOrNull()

        fun buildStoryboardPrompts(videoPrompt: String, style: String): List<String> {
            val clean = videoPrompt.trim()
            return listOf(
                "$clean, opening wide establishing shot, ${styleSuffix(style)}",
                "$clean, dynamic mid-action camera angle, ${styleSuffix(style)}",
                "$clean, dramatic close-up finale shot, ${styleSuffix(style)}",
            )
        }

        fun buildA1111RequestJson(
            prompt: String,
            style: String,
            width: Int,
            height: Int,
            negativePrompt: String = "",
            steps: Int = 24,
            cfgScale: Float = 7.0f,
            seed: Int = -1,
        ): String {
            val root = JsonObject().apply {
                addProperty("prompt", "${prompt.trim()}, ${styleSuffix(style)}")
                if (negativePrompt.isNotBlank()) {
                    addProperty("negative_prompt", negativePrompt.trim())
                }
                addProperty("width", width)
                addProperty("height", height)
                addProperty("steps", steps.coerceIn(1, 100))
                addProperty("cfg_scale", cfgScale.coerceIn(1.0f, 30.0f))
                if (seed > 0) {
                    addProperty("seed", seed)
                }
            }
            return gson.toJson(root)
        }

        fun parseA1111Base64Image(json: String): String {
            val root = JsonParser.parseString(json).asJsonObject
            val images = root.getAsJsonArray("images")
                ?: error("В ответе Stable Diffusion отсутствует массив images")
            if (images.size() == 0) error("Stable Diffusion вернул пустой массив изображений")
            return images[0].asString.substringAfter("base64,")
        }

        fun buildOpenAiImageRequestJson(
            model: String,
            prompt: String,
            style: String,
            width: Int,
            height: Int,
        ): String {
            val size = when {
                width > height -> "1536x1024"
                height > width -> "1024x1536"
                else -> "1024x1024"
            }
            val root = JsonObject().apply {
                addProperty("model", model)
                addProperty("prompt", "${prompt.trim()}, ${styleSuffix(style)}")
                addProperty("n", 1)
                addProperty("size", size)
            }
            return gson.toJson(root)
        }

        fun parseOpenAiImageResult(json: String): OpenAiImagePayload {
            val root = JsonParser.parseString(json).asJsonObject
            val data = root.getAsJsonArray("data")
                ?: error("В ответе сервера генерации отсутствует data")
            if (data.size() == 0) error("Сервер вернул пустой список изображений")
            val first = data[0].asJsonObject
            val b64 = first.get("b64_json")?.asString
            if (!b64.isNullOrBlank()) return OpenAiImagePayload.Base64Data(b64)
            val url = first.get("url")?.asString
            if (!url.isNullOrBlank()) return OpenAiImagePayload.RemoteUrl(url)
            error("В ответе нет ни b64_json, ни url")
        }
    }
}
