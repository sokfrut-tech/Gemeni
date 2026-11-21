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
    CUSTOM_OPENAI_IMAGES("Свой сервер (OpenAI Images API)"),
    CUSTOM_A1111_SD("Свой ПК (Stable Diffusion / A1111 WebUI)"),
}

data class MediaGenConfig(
    val backend: MediaGenBackend = MediaGenBackend.POLLINATIONS_ONLINE,
    val customImageUrl: String = "",
    val customVideoUrl: String = "",
    val apiKey: String = "",
    val modelName: String = "flux",
)

class MediaGenConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_media_gen", Context.MODE_PRIVATE)

    fun load(): MediaGenConfig {
        val rawBackend = prefs.getString(KEY_BACKEND, MediaGenBackend.POLLINATIONS_ONLINE.name)
        val backend = runCatching { MediaGenBackend.valueOf(rawBackend ?: "") }
            .getOrDefault(MediaGenBackend.POLLINATIONS_ONLINE)
        return MediaGenConfig(
            backend = backend,
            customImageUrl = prefs.getString(KEY_IMG_URL, "") ?: "",
            customVideoUrl = prefs.getString(KEY_VID_URL, "") ?: "",
            apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
            modelName = prefs.getString(KEY_MODEL, "flux") ?: "flux",
        )
    }

    fun save(config: MediaGenConfig) {
        prefs.edit()
            .putString(KEY_BACKEND, config.backend.name)
            .putString(KEY_IMG_URL, config.customImageUrl.trim())
            .putString(KEY_VID_URL, config.customVideoUrl.trim())
            .putString(KEY_API_KEY, config.apiKey.trim())
            .putString(KEY_MODEL, config.modelName.trim())
            .apply()
    }

    companion object {
        private const val KEY_BACKEND = "media_backend"
        private const val KEY_IMG_URL = "media_custom_image_url"
        private const val KEY_VID_URL = "media_custom_video_url"
        private const val KEY_API_KEY = "media_api_key"
        private const val KEY_MODEL = "media_model"
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
 * - Optional custom server mode connects to user's own Stable Diffusion / ComfyUI / OpenAI-compatible GPU server.
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
            val imageBytes = fetchImageBytes(cfg, cleanPrompt, style, width, height, Random.nextInt(1, 999_999))
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
                        val bytes = fetchImageBytes(cfg, scenePrompt, style, width, height, baseSeed + idx)
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

    private fun fetchImageBytes(
        cfg: MediaGenConfig,
        prompt: String,
        style: String,
        width: Int,
        height: Int,
        seed: Int,
    ): ByteArray {
        return when (cfg.backend) {
            MediaGenBackend.POLLINATIONS_ONLINE -> {
                val url = buildPollinationsImageUrl(prompt, style, width, height, seed, cfg.modelName)
                getBytes(url, emptyMap(), readTimeoutMs = 45_000)
            }
            MediaGenBackend.CUSTOM_A1111_SD -> {
                val url = cfg.customImageUrl.trim()
                require(url.startsWith("http://", true) || url.startsWith("https://", true)) {
                    "Укажите URL вашего Stable Diffusion (например, .../sdapi/v1/txt2img)"
                }
                val reqJson = buildA1111RequestJson(prompt, style, width, height)
                val resp = postBytes(url, reqJson, buildAuthHeaders(cfg.apiKey), readTimeoutMs = 90_000)
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

    private fun buildAuthHeaders(apiKey: String): Map<String, String> =
        if (apiKey.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer ${apiKey.trim()}")

    private fun getBytes(urlStr: String, headers: Map<String, String>, readTimeoutMs: Int): ByteArray {
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
        } finally {
            conn.disconnect()
        }
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

        fun buildPollinationsImageUrl(
            prompt: String,
            style: String = "Фото",
            width: Int = 768,
            height: Int = 768,
            seed: Int = 42,
            model: String = "flux",
        ): String {
            val fullPrompt = "${prompt.trim()}, ${styleSuffix(style)}"
            val encoded = URLEncoder.encode(fullPrompt, "UTF-8").replace("+", "%20")
            val safeModel = URLEncoder.encode(model.ifBlank { "flux" }, "UTF-8")
            return "https://image.pollinations.ai/prompt/$encoded?width=$width&height=$height&seed=$seed&model=$safeModel&nologo=true"
        }

        fun buildStoryboardPrompts(videoPrompt: String, style: String): List<String> {
            val clean = videoPrompt.trim()
            return listOf(
                "$clean, opening wide establishing shot, ${styleSuffix(style)}",
                "$clean, dynamic mid-action camera angle, ${styleSuffix(style)}",
                "$clean, dramatic close-up finale shot, ${styleSuffix(style)}",
            )
        }

        fun buildA1111RequestJson(prompt: String, style: String, width: Int, height: Int): String {
            val root = JsonObject().apply {
                addProperty("prompt", "${prompt.trim()}, ${styleSuffix(style)}")
                addProperty("width", width)
                addProperty("height", height)
                addProperty("steps", 24)
                addProperty("cfg_scale", 7.0)
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
