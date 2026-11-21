package com.jarvis.breeno.assistant.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.gson.Gson
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
 * Generates images and animated MP4 videos on the phone:
 * - Default mode works immediately without an API key via Pollinations + on-device MediaCodec MP4 synthesis.
 * - Self-hosted mode connects to user's own PC GPU ComfyUI, Automatic1111/Forge WebUI, OpenAI Images, or video API.
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
                val reqJson = gson.toJson(mapOf("prompt" to cleanPrompt, "style" to style))
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
                    "Укажите URL вашего Stable Diffusion (например, http://192.168.1.50:7860)"
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
            "Укажите URL вашего ComfyUI сервера (например, http://192.168.1.50:8188)"
        }
        val baseUrl = normalizeComfyBaseUrl(rawUrl)
        val headers = buildAuthHeaders(cfg.apiKey)

        val ckpt = cfg.checkpointName.trim().ifBlank {
            onProgress("Определяю чекпоинт ComfyUI…")
            val infoJson = getBytes("$baseUrl/object_info/CheckpointLoaderSimple", headers, readTimeoutMs = 15_000)
                .toString(Charsets.UTF_8)
            parseComfyFirstCheckpoint(infoJson)
                ?: error("Не найден чекпоинт в ComfyUI: укажите имя .safetensors в настройках")
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
        )

        onProgress("Отправляю задачу в ComfyUI ($ckpt)…")
        val submitResp = postBytes("$baseUrl/prompt", workflowJson, headers, readTimeoutMs = 30_000)
            .toString(Charsets.UTF_8)
        val promptId = parseComfyPromptId(submitResp)

        onProgress("ComfyUI рендерит изображение…")
        val pollStart = System.currentTimeMillis()
        var imageRef: ComfyImageRef? = null
        while (System.currentTimeMillis() - pollStart < 90_000L) {
            currentCoroutineContext().ensureActive()
            val histJson = getBytes("$baseUrl/history/$promptId", headers, readTimeoutMs = 15_000)
                .toString(Charsets.UTF_8)
            imageRef = parseComfyHistoryImageRef(histJson, promptId)
            if (imageRef != null) break
            delay(800)
        }

        val ref = imageRef ?: error("ComfyUI не завершил генерацию за 90 секунд")
        val qFilename = URLEncoder.encode(ref.filename, "UTF-8")
        val qSubfolder = URLEncoder.encode(ref.subfolder, "UTF-8")
        val qType = URLEncoder.encode(ref.type, "UTF-8")
        return getBytes(
            "$baseUrl/view?filename=$qFilename&subfolder=$qSubfolder&type=$qType",
            headers,
            readTimeoutMs = 30_000,
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
        throw lastErr ?: IllegalStateException("Ошибка загрузки изображения")
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
        private val gson = Gson()

        private fun styleSuffix(style: String): String = when (style.lowercase()) {
            "фото" -> "photorealistic, ultra detailed, 8k, natural lighting"
            "кино" -> "cinematic film still, dramatic lighting, shallow depth of field, anamorphic"
            "киберпанк" -> "cyberpunk neon aesthetic, futuristic, volumetric light, ultra detailed"
            "аниме" -> "high quality anime artwork, vibrant colors, crisp lineart, studio quality"
            "3d арт" -> "octane 3d render, unreal engine 5 style, glossy volumetric lighting"
            else -> style
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
        ): String {
            val safeW = ((width.coerceIn(256, 1536) + 7) / 8) * 8
            val safeH = ((height.coerceIn(256, 1536) + 7) / 8) * 8

            fun nodeRef(id: String, outputIndex: Int = 0) = com.google.gson.JsonArray().apply {
                add(id)
                add(outputIndex)
            }

            fun node(classType: String, inputs: JsonObject) = JsonObject().apply {
                addProperty("class_type", classType)
                add("inputs", inputs)
            }

            val graph = JsonObject().apply {
                add("4", node("CheckpointLoaderSimple", JsonObject().apply {
                    addProperty("ckpt_name", checkpointName)
                }))
                add("5", node("EmptyLatentImage", JsonObject().apply {
                    addProperty("width", safeW)
                    addProperty("height", safeH)
                    addProperty("batch_size", 1)
                }))
                add("6", node("CLIPTextEncode", JsonObject().apply {
                    addProperty("text", "${prompt.trim()}, ${styleSuffix(style)}")
                    add("clip", nodeRef("4", 1))
                }))
                add("7", node("CLIPTextEncode", JsonObject().apply {
                    addProperty("text", negativePrompt.trim())
                    add("clip", nodeRef("4", 1))
                }))
                add("3", node("KSampler", JsonObject().apply {
                    addProperty("seed", seed.coerceAtLeast(1))
                    addProperty("steps", steps.coerceIn(1, 100))
                    addProperty("cfg", cfgScale.coerceIn(1.0f, 30.0f))
                    addProperty("sampler_name", "euler")
                    addProperty("scheduler", "normal")
                    addProperty("denoise", 1.0)
                    add("model", nodeRef("4", 0))
                    add("positive", nodeRef("6", 0))
                    add("negative", nodeRef("7", 0))
                    add("latent_image", nodeRef("5", 0))
                }))
                add("8", node("VAEDecode", JsonObject().apply {
                    add("samples", nodeRef("3", 0))
                    add("vae", nodeRef("4", 2))
                }))
                add("9", node("SaveImage", JsonObject().apply {
                    addProperty("filename_prefix", "JarvisStudio")
                    add("images", nodeRef("8", 0))
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

        fun parseComfyHistoryImageRef(historyJson: String, promptId: String): ComfyImageRef? {
            val root = JsonParser.parseString(historyJson).asJsonObject
            val entry = root.getAsJsonObject(promptId) ?: return null
            val outputs = entry.getAsJsonObject("outputs") ?: return null
            for (key in outputs.keySet()) {
                val nodeObj = outputs.getAsJsonObject(key) ?: continue
                val images = nodeObj.getAsJsonArray("images") ?: continue
                if (images.size() > 0) {
                    val img = images[0].asJsonObject
                    val filename = img.get("filename")?.asString?.takeIf { it.isNotBlank() } ?: continue
                    val subfolder = img.get("subfolder")?.asString ?: ""
                    val type = img.get("type")?.asString ?: "output"
                    return ComfyImageRef(filename = filename, subfolder = subfolder, type = type)
                }
            }
            return null
        }

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
