package com.jarvis.breeno.assistant.core

import android.content.Context
import android.os.StatFs
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext


/** Disconnects the currently active HTTP request when its owning coroutine is cancelled. */
internal fun disconnectActiveConnectionOnCancellation(
    job: Job?,
    activeConnection: AtomicReference<HttpURLConnection?>
): DisposableHandle? = job?.invokeOnCompletion { cause ->
    if (cause != null) activeConnection.getAndSet(null)?.disconnect()
}

/**
 * Consent-gated installer for the pinned first-run GGUF candidate.
 *
 * No work starts until install() is called with explicit consent. Downloads stay
 * in app-private storage; every redirect is checked before following it.
 */
class QwenModelInstaller(context: Context) {
    private val appContext = context.applicationContext

    data class Progress(val downloadedBytes: Long, val totalBytes: Long) {
        val fraction: Float
            get() = if (totalBytes <= 0L) 0f
            else (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
    }

    data class InstalledModel(val file: File, val sizeBytes: Long)

    suspend fun install(
        userConsent: Boolean,
        onProgress: (Progress) -> Unit = {}
    ): InstalledModel = withContext(Dispatchers.IO) {
        require(userConsent) { "Для загрузки модели требуется явное согласие пользователя" }

        val directory = File(appContext.filesDir, MODEL_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Не удалось создать защищённую папку модели")
        }
        val canonicalDirectory = directory.canonicalFile
        val destination = File(canonicalDirectory, MODEL_FILENAME)
        val temporary = File(canonicalDirectory, "$MODEL_FILENAME.part")

        if (destination.isFile && destination.length() == MODEL_SIZE_BYTES &&
            sha256(destination).equals(MODEL_SHA256, ignoreCase = true)
        ) {
            onProgress(Progress(MODEL_SIZE_BYTES, MODEL_SIZE_BYTES))
            return@withContext InstalledModel(destination, destination.length())
        }

        val available = StatFs(canonicalDirectory.absolutePath).availableBytes
        if (available < MINIMUM_FREE_SPACE_BYTES) {
            throw IOException("Недостаточно свободного места: требуется не менее 650 МБ")
        }

        temporary.delete()
        try {
            downloadAndVerify(temporary, onProgress)
            currentCoroutineContext().ensureActive()
            // Atomic replacement only: never expose a partially downloaded GGUF.
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
            InstalledModel(destination, destination.length())
        } catch (failure: Throwable) {
            temporary.delete()
            throw failure
        }
    }

    suspend fun removeInstalledModel(): Boolean = withContext(Dispatchers.IO) {
        val directory = File(appContext.filesDir, MODEL_DIRECTORY).canonicalFile
        val model = File(directory, MODEL_FILENAME)
        val temporary = File(directory, "$MODEL_FILENAME.part")
        val removedPart = !temporary.exists() || temporary.delete()
        val removedModel = !model.exists() || model.delete()
        removedPart && removedModel
    }

    fun installedModelFile(): File =
        File(File(appContext.filesDir, MODEL_DIRECTORY), MODEL_FILENAME)

    private suspend fun downloadAndVerify(
        temporary: File,
        onProgress: (Progress) -> Unit
    ) {
        var currentUrl = URL(DOWNLOAD_URL)
        var connection: HttpURLConnection? = null
        val activeConnection = AtomicReference<HttpURLConnection?>(null)
        val job = currentCoroutineContext()[Job]
        // Disconnect the socket from the cancellation callback so cancellation
        // can interrupt responseCode/read(), not merely the next ensureActive().
        val cancellationHandle = disconnectActiveConnectionOnCancellation(job, activeConnection)
        var redirects = 0
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                validateUrl(currentUrl, initial = redirects == 0)
                connection = (currentUrl.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Accept-Encoding", "identity")
                }
                activeConnection.set(connection)
                // Closes the race where cancellation happens as the connection
                // is being registered with the job's completion handler.
                currentCoroutineContext().ensureActive()
                val status = connection.responseCode
                if (status in REDIRECT_CODES) {
                    if (redirects++ >= MAX_REDIRECTS) {
                        throw IOException("Слишком много перенаправлений при загрузке модели")
                    }
                    val location = connection.getHeaderField("Location")
                        ?: throw IOException("Сервер вернул перенаправление без адреса")
                    currentUrl = URL(currentUrl, location)
                    activeConnection.compareAndSet(connection, null)
                    connection.disconnect()
                    connection = null
                    continue
                }
                if (status != HttpURLConnection.HTTP_OK) {
                    throw IOException("Сервер модели вернул HTTP $status")
                }
                val declaredLength = connection.contentLengthLong
                if (declaredLength >= 0L && declaredLength != MODEL_SIZE_BYTES) {
                    throw IOException("Размер модели на сервере не совпадает с манифестом")
                }

                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L
                connection.inputStream.buffered().use { input ->
                    temporary.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            downloaded += count
                            if (downloaded > MODEL_SIZE_BYTES) {
                                throw IOException("Загруженный файл превышает размер из манифеста")
                            }
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                            onProgress(Progress(downloaded, MODEL_SIZE_BYTES))
                        }
                        output.flush()
                    }
                }
                if (downloaded != MODEL_SIZE_BYTES) {
                    throw IOException("Загрузка неполная: получено $downloaded из $MODEL_SIZE_BYTES байт")
                }
                val actualDigest = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actualDigest.equals(MODEL_SHA256, ignoreCase = true)) {
                    throw IOException("SHA-256 загруженной модели не совпадает с манифестом")
                }
                return
            }
        } finally {
            cancellationHandle?.dispose()
            activeConnection.getAndSet(null)?.disconnect()
            connection?.disconnect()
        }
    }

    private fun validateUrl(url: URL, initial: Boolean) {
        require(url.protocol.equals("https", ignoreCase = true)) {
            "Загрузка модели разрешена только по HTTPS"
        }
        require(url.userInfo == null && (url.port == -1 || url.port == 443)) {
            "Недопустимый адрес сервера модели"
        }
        val host = url.host.lowercase()
        val allowed = if (initial) {
            host == "huggingface.co"
        } else {
            host in ALLOWED_REDIRECT_HOSTS
        }
        require(allowed) { "Перенаправление на недоверенный сервер модели запрещено" }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val MODEL_FILENAME = "Qwen3.5-0.8B-Q4_0.gguf"
        const val MODEL_SIZE_BYTES = 563036064L
        const val MODEL_SHA256 =
            "57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf"
        const val MINIMUM_FREE_SPACE_BYTES = 650000000L
        private const val MODEL_DIRECTORY = "models"
        private const val DOWNLOAD_URL =
            "https://huggingface.co/ggml-org/Qwen3.5-0.8B-GGUF/resolve/4c11520cef475dd40ed72ec4a1e68b26721bd769/Qwen3.5-0.8B-Q4_0.gguf"
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_REDIRECTS = 5
        private const val USER_AGENT = "Jarvis-Android/0.53 model-installer"
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private val ALLOWED_REDIRECT_HOSTS = setOf(
            "huggingface.co",
            "hf.co",
            "cdn-lfs.huggingface.co",
            "cas-bridge.xethub.hf.co",
            "us.aws.cdn.hf.co"
        )
    }
}
