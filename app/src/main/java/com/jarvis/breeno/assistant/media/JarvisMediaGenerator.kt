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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import kotlin.random.Random

enum class MediaGenBackend(val title: String) {
    POLLINATIONS_ONLINE("Встроенный онлайн-генератор (без ключа)"),
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
    val comfyPonyClipSkip2: Boolean = true,
    val comfyWanI2vEnabled: Boolean = true,
    val comfyWanHighNoiseModel: String = JarvisMediaGenerator.DEFAULT_WAN_HIGH_MODEL,
    val comfyWanLowNoiseModel: String = JarvisMediaGenerator.DEFAULT_WAN_LOW_MODEL,
    val comfyWanTextEncoder: String = JarvisMediaGenerator.DEFAULT_WAN_TEXT_ENCODER,
    val comfyWanVae: String = JarvisMediaGenerator.DEFAULT_WAN_VAE,
    val comfyWanTurboLora: Boolean = true,
    val comfyVideoFrames: Int = 49,
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
            .putBoolean(KEY_COMFY_PONY_CLIP_SKIP2, config.comfyPonyClipSkip2)
            .putBoolean(KEY_COMFY_WAN_I2V_ENABLED, config.comfyWanI2vEnabled)
            .putString(KEY_COMFY_WAN_HIGH, config.comfyWanHighNoiseModel.trim())
            .putString(KEY_COMFY_WAN_LOW, config.comfyWanLowNoiseModel.trim())
            .putString(KEY_COMFY_WAN_CLIP, config.comfyWanTextEncoder.trim())
            .putString(KEY_COMFY_WAN_VAE, config.comfyWanVae.trim())
            .putBoolean(KEY_COMFY_WAN_TURBO, config.comfyWanTurboLora)
            .putInt(KEY_COMFY_VIDEO_FRAMES, config.comfyVideoFrames.coerceIn(17, 121))
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
        private const val KEY_COMFY_PONY_CLIP_SKIP2 = "media_comfy_pony_clip_skip2"
        private const val KEY_COMFY_WAN_I2V_ENABLED = "media_comfy_wan_i2v_enabled"
        private const val KEY_COMFY_WAN_HIGH = "media_comfy_wan_high"
        private const val KEY_COMFY_WAN_LOW = "media_comfy_wan_low"
        private const val KEY_COMFY_WAN_CLIP = "media_comfy_wan_clip"
        private const val KEY_COMFY_WAN_VAE = "media_comfy_wan_vae"
        private const val KEY_COMFY_WAN_TURBO = "media_comfy_wan_turbo"
        private const val KEY_COMFY_VIDEO_FRAMES = "media_comfy_video_frames"
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

/**
 * Generates images and animated MP4 videos on the phone or via self-hosted PC ComfyUI:
 * - Single-window ComfyUI pipeline supports CyberRealistic Pony XL (Clip Skip 2) -> Wan 2.2 I2V-14B.
 * - Also supports Automatic1111/Forge WebUI, OpenAI Images, Pollinations images, and on-device MediaCodec MP4.
 */
class JarvisMediaGenerator(
    private val studioDir: File,
    private val historyFile: File,
    private val configStore: MediaGenConfigStore,
    private val onEvent: ((title: String, detail: String, isError: Boolean) -> Unit)? = null,
) {
    private val gson = Gson()
    private val _items = MutableStateFlow(loadHistory())
    val items: StateFlow<List<GeneratedMediaItem>> = _items.asStateFlow()

    init {
        studioDir.mkdirs()
        historyFile.parentFile?.mkdirs()
    }

    fun currentConfig(): MediaGenConfig = configStore.load()

    fun saveConfig(config: MediaGenConfig) {
        configStore.save(config)
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
        onProgress("Генерирую изображение…")

        runCatching {
            val imageBytes = fetchImageBytes(cfg, cleanPrompt, style, width, height, Random.nextInt(1, 999_999), onProgress)
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
        val id = UUID.randomUUID().toString().take(8)
        studioDir.mkdirs()
        val outFile = File(studioDir, "jarvis_vid_${id}_${System.currentTimeMillis()}.mp4")

        runCatching {
            if (cfg.customVideoUrl.isNotBlank()) {
                onProgress("Отправляю запрос на ваш видео-сервер…")
                val reqJson = gson.toJson(mapOf("prompt" to cleanPrompt, "style" to style, "motion_prompt" to motionPrompt.trim()))
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
                    imagePrompt = cleanPrompt,
                    motionPrompt = motionPrompt,
                    style = style,
                    videoWidth = width,
                    videoHeight = height,
                    seed = Random.nextInt(1, 999_999),
                    onProgress = onProgress,
                )
                outFile.writeBytes(mp4Bytes)
            } else {
                val scenes = buildStoryboardPrompts(cleanPrompt, style)
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
            MediaGenBackend.POLLINATIONS_ONLINE -> {
                val url = buildPollinationsImageUrl(prompt, style, width, height, seed, cfg.modelName)
                getBytes(url, emptyMap(), readTimeoutMs = 45_000)
            }
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
            MediaGenBackend.CUSTOM_OPENAI_IMAGES -> {
                val url = cfg.customImageUrl.trim().ifBlank { "https://api.openai.com/v1/images/generations" }
                val reqJson = buildOpenAiImageRequestJson(cfg.modelName.ifBlank { "gpt-image-1" }, prompt, style, width, height)
                val headers = buildAuthHeaders(cfg.apiKey)
                val resp = postBytes(url, reqJson, headers, readTimeoutMs = 90_000)
                when (val payload = parseOpenAiImageResult(resp.toString(Charsets.UTF_8))) {
                    is OpenAiImagePayload.Base64Data -> Base64.decode(payload.base64, Base64.DEFAULT)
                    is OpenAiImagePayload.RemoteUrl -> getBytes(payload.url, emptyMap(), readTimeoutMs = 45_000)
                }
            }
        }
    }

    private suspend fun resolveComfyCheckpoint(
        baseUrl: String,
        cfg: MediaGenConfig,
        headers: Map<String, String>,
        onProgress: (String) -> Unit,
    ): String = cfg.checkpointName.trim().ifBlank {
        onProgress("Определяю чекпоинт ComfyUI…")
        val infoJson = getBytes("$baseUrl/object_info/CheckpointLoaderSimple", headers, readTimeoutMs = 15_000)
            .toString(Charsets.UTF_8)
        parseComfyFirstCheckpoint(infoJson)
            ?: error("Не найден чекпоинт в ComfyUI: укажите имя .safetensors в настройках")
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
        val isPony = cfg.comfyPonyClipSkip2 ||
            ckpt.contains("pony", ignoreCase = true) ||
            ckpt.contains("cyberrealistic", ignoreCase = true)

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
            usePonyClipSkip2 = isPony,
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

    private fun getBytes(urlStr: String, headers: Map<String, String>, readTimeoutMs: Int): ByteArray {
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
                check(code in 200..299) { "Сервер генерации вернул HTTP $code" }
                return conn.inputStream.use { it.readBytes() }
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
                error("Сервер генерации вернул HTTP $code: ${data.toString(Charsets.UTF_8).take(140)}")
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
        const val DEFAULT_PONY_CHECKPOINT = "CyberRealisticPony_V18.0_F16.safetensors"
        const val DEFAULT_PONY_NEGATIVE = "score_6, score_5, score_4, worst quality, low quality, lowres, bad anatomy, bad hands, signature, watermark"
        const val DEFAULT_WAN_HIGH_MODEL = "wan2.2_i2v_high_noise_14B_fp8_scaled.safetensors"
        const val DEFAULT_WAN_LOW_MODEL = "wan2.2_i2v_low_noise_14B_fp8_scaled.safetensors"
        const val DEFAULT_WAN_TEXT_ENCODER = "umt5_xxl_fp8_e4m3fn_scaled.safetensors"
        const val DEFAULT_WAN_VAE = "wan_2.1_vae.safetensors"
        const val DEFAULT_WAN_HIGH_LORA = "wan2.2_i2v_lightx2v_4steps_lora_v1_high_noise.safetensors"
        const val DEFAULT_WAN_LOW_LORA = "wan2.2_i2v_lightx2v_4steps_lora_v1_low_noise.safetensors"
        const val DEFAULT_WAN_NEGATIVE = "static image, overexposed, blurred details, worst quality, low quality, jpeg artifacts, deformed limbs, extra fingers, cluttered background"

        private val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mov", "mkv")
        private val gson = Gson()

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

        fun parseComfyFirstCheckpoint(objectInfoJson: String): String? {
            return runCatching {
                val root = JsonParser.parseString(objectInfoJson).asJsonObject
                val ckptArray = root.getAsJsonObject("CheckpointLoaderSimple")
                    ?.getAsJsonObject("input")
                    ?.getAsJsonObject("required")
                    ?.getAsJsonArray("ckpt_name")
                    ?: return null
                val choices = ckptArray.get(0)?.asJsonArray ?: return null
                if (choices.size() > 0) choices[0].asString else null
            }.getOrNull()
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
        ): String {
            val safeW = ((width.coerceIn(256, 1536) + 7) / 8) * 8
            val safeH = ((height.coerceIn(256, 1536) + 7) / 8) * 8
            val styledPrompt = "${prompt.trim()}, ${styleSuffix(style)}"
            val finalPos = if (usePonyClipSkip2 && !styledPrompt.contains("score_9", ignoreCase = true)) {
                "score_9, score_8_up, score_7_up, $styledPrompt"
            } else {
                styledPrompt
            }
            val finalNeg = if (usePonyClipSkip2 && negativePrompt.isBlank()) DEFAULT_PONY_NEGATIVE else negativePrompt.trim()
            val clipNodeRef = if (usePonyClipSkip2) nodeRef("11", 0) else nodeRef("4", 1)

            val graph = JsonObject().apply {
                add("4", comfyNode("CheckpointLoaderSimple", JsonObject().apply {
                    addProperty("ckpt_name", checkpointName)
                }))
                if (usePonyClipSkip2) {
                    add("11", comfyNode("CLIPSetLastLayer", JsonObject().apply {
                        add("clip", nodeRef("4", 1))
                        addProperty("stop_at_clip_layer", -2)
                    }))
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
                    addProperty("cfg", cfgScale.coerceIn(1.0f, 30.0f))
                    addProperty("sampler_name", if (usePonyClipSkip2) "dpmpp_2m" else "euler")
                    addProperty("scheduler", if (usePonyClipSkip2) "karras" else "normal")
                    addProperty("denoise", 1.0)
                    add("model", nodeRef("4", 0))
                    add("positive", nodeRef("6", 0))
                    add("negative", nodeRef("7", 0))
                    add("latent_image", nodeRef("5", 0))
                }))
                add("8", comfyNode("VAEDecode", JsonObject().apply {
                    add("samples", nodeRef("3", 0))
                    add("vae", nodeRef("4", 2))
                }))
                add("9", comfyNode("SaveImage", JsonObject().apply {
                    addProperty("filename_prefix", if (usePonyClipSkip2) "JarvisPony" else "JarvisStudio")
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

        fun buildPollinationsImageUrl(
            prompt: String,
            style: String = "Фото",
            width: Int = 768,
            height: Int = 768,
            seed: Int = 42,
            model: String = "",
        ): String {
            val fullPrompt = "${prompt.trim()}, ${styleSuffix(style)}"
            val encoded = URLEncoder.encode(fullPrompt, "UTF-8").replace("+", "%20")
            val modelParam = model.trim().takeIf { it.isNotEmpty() }
                ?.let { "&model=${URLEncoder.encode(it, "UTF-8")}" }
                .orEmpty()
            return "https://image.pollinations.ai/prompt/$encoded?width=$width&height=$height&seed=$seed&nologo=true$modelParam"
        }

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
