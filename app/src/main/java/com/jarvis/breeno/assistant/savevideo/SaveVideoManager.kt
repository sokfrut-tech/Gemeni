package com.jarvis.breeno.assistant.savevideo

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages permitted video metadata inspection, download queue, local storage, and history.
 * Pure filesystem + standard HttpURLConnection implementation so it is both Android-safe
 * and unit-testable on the JVM.
 */
class SaveVideoManager(
    private val downloadsDir: File,
    private val historyFile: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onEvent: ((title: String, detail: String, isError: Boolean) -> Unit)? = null,
) {
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val _tasks = MutableStateFlow<List<SaveVideoTask>>(loadHistory())
    val tasks: StateFlow<List<SaveVideoTask>> = _tasks.asStateFlow()

    init {
        downloadsDir.mkdirs()
        historyFile.parentFile?.mkdirs()
    }

    suspend fun inspectMetadata(rawUrl: String): Result<SaveVideoMetadata> = withContext(ioDispatcher) {
        val normalized = SaveVideoUrlParser.normalizeAndValidate(rawUrl)
            ?: return@withContext Result.failure(IllegalArgumentException("Некорректная ссылка (нужен http:// или https://)"))
        val source = SaveVideoUrlParser.detectSource(normalized)

        runCatching {
            val connection = (URL(normalized).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 10_000
                readTimeout = 12_000
                setRequestProperty("User-Agent", "JarvisSaveVideo/1.0 (Android)")
                setRequestProperty("Range", "bytes=0-32767")
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299 && code != 206) {
                    error("Сервер вернул HTTP $code")
                }
                val contentType = connection.contentType?.substringBefore(';')?.trim()
                val contentLength = connection.contentLengthLong.takeIf { it > 0L }
                val isDirectMedia = contentType != null &&
                    (contentType.startsWith("video/", true) || contentType.startsWith("audio/", true))

                val title = if (isDirectMedia) {
                    URL(normalized).path.substringAfterLast('/').ifBlank { "media_${System.currentTimeMillis()}" }
                } else {
                    val snippet = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        val buffer = CharArray(32_768)
                        val count = reader.read(buffer)
                        if (count > 0) String(buffer, 0, count) else ""
                    }
                    SaveVideoUrlParser.extractHtmlTitle(snippet) ?: source.displayName
                }

                SaveVideoMetadata(
                    normalizedUrl = normalized,
                    source = source,
                    title = title,
                    contentType = contentType,
                    contentLengthBytes = contentLength,
                    directDownloadable = isDirectMedia,
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    fun enqueue(
        rawUrl: String,
        quality: SaveVideoQuality = SaveVideoQuality.AUTO,
        startImmediately: Boolean = true,
    ): Result<SaveVideoTask> {
        val normalized = SaveVideoUrlParser.normalizeAndValidate(rawUrl)
            ?: return Result.failure(IllegalArgumentException("Некорректная ссылка (нужен http:// или https://)"))
        val source = SaveVideoUrlParser.detectSource(normalized)
        val task = SaveVideoTask(
            id = UUID.randomUUID().toString().take(8),
            url = normalized,
            source = source,
            quality = quality,
            title = "${source.displayName}: ${normalized.substringAfter("://").take(38)}",
            status = SaveVideoStatus.QUEUED,
        )
        _tasks.update { listOf(task) + it }
        persistHistory()
        onEvent?.invoke("SaveVideo: в очереди", "${source.displayName} (${quality.label})", false)

        if (startImmediately) {
            startTask(task.id)
        }
        return Result.success(task)
    }

    fun startTask(taskId: String) {
        if (activeJobs.containsKey(taskId)) return
        val job = scope.launch {
            try {
                executeDownload(taskId)
            } catch (_: CancellationException) {
                updateTask(taskId) { it.copy(status = SaveVideoStatus.CANCELLED) }
            } finally {
                activeJobs.remove(taskId)
            }
        }
        activeJobs[taskId] = job
    }

    fun cancelTask(taskId: String) {
        activeJobs.remove(taskId)?.cancel()
        updateTask(taskId) {
            if (it.status == SaveVideoStatus.COMPLETED) it
            else it.copy(status = SaveVideoStatus.CANCELLED, errorMessage = "Отменено пользователем")
        }
        onEvent?.invoke("SaveVideo: отменено", "Задача #$taskId", false)
    }

    fun deleteTask(taskId: String, deleteLocalFile: Boolean = true) {
        activeJobs.remove(taskId)?.cancel()
        val existing = _tasks.value.firstOrNull { it.id == taskId }
        if (deleteLocalFile && !existing?.localFilePath.isNullOrBlank()) {
            runCatching { File(existing!!.localFilePath!!).delete() }
        }
        _tasks.update { list -> list.filterNot { it.id == taskId } }
        persistHistory()
    }

    private suspend fun executeDownload(taskId: String) {
        val current = _tasks.value.firstOrNull { it.id == taskId } ?: return
        updateTask(taskId) { it.copy(status = SaveVideoStatus.INSPECTING, errorMessage = null) }

        val metaResult = inspectMetadata(current.url)
        val meta = metaResult.getOrElse { err ->
            val msg = err.message ?: err.javaClass.simpleName
            updateTask(taskId) { it.copy(status = SaveVideoStatus.FAILED, errorMessage = msg) }
            onEvent?.invoke("SaveVideo: ошибка проверки", msg, true)
            return
        }

        if (!meta.directDownloadable) {
            val msg = "Ссылка ведёт на веб-страницу (${meta.source.displayName}), а не на прямой медиапоток. Вставьте прямую ссылку на разрешённый MP4/WebM/аудиофайл."
            updateTask(taskId) {
                it.copy(
                    title = meta.title,
                    contentType = meta.contentType,
                    status = SaveVideoStatus.FAILED,
                    errorMessage = msg,
                )
            }
            onEvent?.invoke("SaveVideo: нужен прямой поток", meta.title, true)
            return
        }

        val extension = guessExtension(meta.contentType, current.url)
        val safeName = "jarvis_${taskId}_${System.currentTimeMillis()}$extension"
        val outFile = File(downloadsDir, safeName)
        downloadsDir.mkdirs()

        updateTask(taskId) {
            it.copy(
                title = meta.title,
                contentType = meta.contentType,
                totalBytes = meta.contentLengthBytes,
                status = SaveVideoStatus.DOWNLOADING,
                progressPercent = 0,
            )
        }

        runCatching {
            val connection = (URL(current.url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "JarvisSaveVideo/1.0 (Android)")
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) error("HTTP $code при загрузке")
                val total = connection.contentLengthLong.takeIf { it > 0L } ?: meta.contentLengthBytes
                var downloaded = 0L
                val buffer = ByteArray(16 * 1024)
                var lastReportedPercent = -1

                connection.inputStream.use { input ->
                    FileOutputStream(outFile).use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val pct = if (total != null && total > 0L) {
                                ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }
                            if (pct != lastReportedPercent) {
                                lastReportedPercent = pct
                                updateTask(taskId, persist = false) {
                                    it.copy(downloadedBytes = downloaded, progressPercent = pct)
                                }
                            }
                        }
                    }
                }

                updateTask(taskId) {
                    it.copy(
                        status = SaveVideoStatus.COMPLETED,
                        progressPercent = 100,
                        downloadedBytes = downloaded,
                        totalBytes = total ?: downloaded,
                        localFilePath = outFile.absolutePath,
                    )
                }
                onEvent?.invoke("SaveVideo: загружено", "${meta.title} (${formatSize(downloaded)})", false)
            } finally {
                connection.disconnect()
            }
        }.onFailure { err ->
            if (err is CancellationException) {
                runCatching { outFile.delete() }
                throw err
            }
            runCatching { outFile.delete() }
            val msg = err.message ?: err.javaClass.simpleName
            updateTask(taskId) { it.copy(status = SaveVideoStatus.FAILED, errorMessage = msg) }
            onEvent?.invoke("SaveVideo: ошибка загрузки", msg, true)
        }
    }

    private fun updateTask(taskId: String, persist: Boolean = true, transform: (SaveVideoTask) -> SaveVideoTask) {
        _tasks.update { list -> list.map { if (it.id == taskId) transform(it) else it } }
        if (persist) persistHistory()
    }

    private fun loadHistory(): List<SaveVideoTask> {
        if (!historyFile.isFile) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<SaveVideoTask>>() {}.type
            val parsed: List<SaveVideoTask>? = gson.fromJson(historyFile.readText(Charsets.UTF_8), type)
            parsed.orEmpty().map { task ->
                // Recover tasks interrupted when the process was killed.
                if (task.status == SaveVideoStatus.DOWNLOADING || task.status == SaveVideoStatus.INSPECTING) {
                    task.copy(status = SaveVideoStatus.QUEUED)
                } else {
                    task
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun persistHistory() {
        runCatching {
            historyFile.parentFile?.mkdirs()
            val tmp = File(historyFile.parentFile, "${historyFile.name}.tmp")
            tmp.writeText(gson.toJson(_tasks.value), Charsets.UTF_8)
            if (!tmp.renameTo(historyFile)) {
                historyFile.writeText(tmp.readText(Charsets.UTF_8), Charsets.UTF_8)
                tmp.delete()
            }
        }
    }

    fun close() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        scope.cancel()
    }

    companion object {
        private fun guessExtension(contentType: String?, url: String): String {
            return when (contentType?.lowercase()) {
                "video/webm" -> ".webm"
                "video/x-matroska" -> ".mkv"
                "video/quicktime" -> ".mov"
                "audio/mpeg" -> ".mp3"
                "audio/mp4", "audio/m4a" -> ".m4a"
                "video/mp4" -> ".mp4"
                else -> {
                    val path = runCatching { URL(url).path }.getOrNull().orEmpty()
                    val dot = path.lastIndexOf('.')
                    if (dot in 1 until path.length - 1 && path.length - dot <= 5) path.substring(dot) else ".mp4"
                }
            }
        }

        fun formatSize(bytes: Long): String {
            if (bytes < 1024L) return "$bytes Б"
            val kb = bytes / 1024.0
            if (kb < 1024.0) return String.format("%.1f КБ", kb)
            val mb = kb / 1024.0
            return String.format("%.1f МБ", mb)
        }
    }
}
