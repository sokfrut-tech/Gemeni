package com.jarvis.breeno.assistant.updater

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class AsrModelFile(
    val name: String,
    val sha256: String?,
    val maxBytes: Long = 256L * 1024L * 1024L
)

/**
 * Downloads pinned ASR files into app-private storage.
 *
 * Redirects are followed manually and validated hop-by-hop. Bytes are bounded
 * while streaming, checksums are computed during the write, and .part files are
 * never considered installed. The caller must obtain explicit user consent
 * before calling ensureRussianAsr().
 */
class ModelDownloadManager internal constructor(
    private val directory: File,
    private val connectionFactory: (URL) -> HttpURLConnection,
    private val modelFiles: List<AsrModelFile> = DEFAULT_FILES,
    private val baseUrl: String = BASE_URL,
    private val availableBytes: (File) -> Long = { it.usableSpace }
) {
    constructor(context: Context) : this(
        File(context.applicationContext.filesDir, "models/$RUSSIAN_MODEL"),
        { url -> url.openConnection() as HttpURLConnection }
    )

    companion object {
        const val RUSSIAN_MODEL = "sherpa-onnx-streaming-zipformer-small-ru-vosk-int8-2025-08-16"
        // Immutable Hugging Face revision; do not follow a moving main branch for model artifacts.
        private const val MODEL_REVISION = "31fa603e4f31279c6e1f7600fed13dc4312663ab"
        private const val BASE_URL = "https://huggingface.co/csukuangfj/$RUSSIAN_MODEL/resolve/$MODEL_REVISION/"
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_REDIRECTS = 5
        private const val MIN_FREE_MARGIN_BYTES = 16L * 1024L * 1024L
        private const val MIN_FREE_SPACE_FOR_UNKNOWN_LENGTH = 256L * 1024L * 1024L
        private const val BUFFER_SIZE = 64 * 1024
        private const val USER_AGENT = "Jarvis-Android-ASR/1.0"
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private val ALLOWED_REDIRECT_HOSTS = setOf(
            "huggingface.co",
            "hf.co",
            "cdn-lfs.huggingface.co",
            "cas-bridge.xethub.hf.co",
            "us.aws.cdn.hf.co"
        )
        private val DEFAULT_FILES = listOf(
            AsrModelFile("encoder.int8.onnx", "e0db705e94ec35d803b1df4f40cda23d064e1142977c80ab288430b109777a9d"),
            AsrModelFile("decoder.onnx", "89b3088a9e20e1ef7f2e85ce1a3478afe6a9c4ac57369cabcc4beb8e95328ea0"),
            AsrModelFile("joiner.int8.onnx", "b55784b071ab7512eab4c7c44e4f5478284ef33c83562cc6a249b972515a31e5"),
            AsrModelFile("tokens.txt", null, maxBytes = 4L * 1024L * 1024L)
        )
    }

    fun modelDir(): File = directory.canonicalFile.also {
        if (!it.exists() && !it.mkdirs()) {
            throw IOException("Could not create Russian ASR model directory")
        }
    }

    fun isRussianAsrReady(): Boolean {
        val dir = modelDir()
        return modelFiles.all { item ->
            val file = File(dir, item.name)
            file.isFile && file.length() in 1..item.maxBytes &&
                (item.sha256 == null || sha256(file).equals(item.sha256, ignoreCase = true))
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    suspend fun ensureRussianAsr(onProgress: (Int) -> Unit = {}): File = withContext(Dispatchers.IO) {
        val coroutineContext = currentCoroutineContext()
        val activeConnection = AtomicReference<HttpURLConnection?>(null)
        // Disconnect the active socket as soon as the coroutine is cancelling,
        // including when the worker is blocked inside responseCode/read().
        val cancellationHandle = coroutineContext[Job]?.invokeOnCompletion(
            onCancelling = true,
            invokeImmediately = true
        ) { cause ->
            if (cause != null) activeConnection.getAndSet(null)?.disconnect()
        }

        try {
            val dir = modelDir()
            modelFiles.forEachIndexed { index, item ->
                coroutineContext.ensureActive()
                val target = File(dir, item.name)
                if (!isValidFile(target, item)) {
                    target.delete()
                    download(item, target, activeConnection) { downloaded, total ->
                        if (total > 0L) {
                            val fraction = (downloaded.toDouble() / total.toDouble()).coerceIn(0.0, 1.0)
                            onProgress(((index + fraction) * 100.0 / modelFiles.size).toInt())
                        }
                    }
                }
                onProgress(((index + 1) * 100) / modelFiles.size)
            }
            check(isRussianAsrReady()) { "Russian ASR model verification failed" }
            dir
        } finally {
            cancellationHandle?.dispose()
            activeConnection.getAndSet(null)?.disconnect()
        }
    }

    private fun isValidFile(file: File, item: AsrModelFile): Boolean =
        file.isFile && file.length() in 1..item.maxBytes &&
            (item.sha256 == null || sha256(file).equals(item.sha256, ignoreCase = true))

    private suspend fun download(
        item: AsrModelFile,
        destination: File,
        activeConnection: AtomicReference<HttpURLConnection?>,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ) {
        val temporary = File(destination.parentFile, destination.name + ".part")
        temporary.delete()
        var connection: HttpURLConnection? = null
        try {
            connection = openTrustedResponse(URL(baseUrl + item.name), activeConnection)
            val declaredLength = connection.contentLengthLong
            require(declaredLength == -1L || declaredLength in 1..item.maxBytes) {
                "Unexpected size for ASR file ${item.name}"
            }
            val requiredSpace = if (declaredLength > 0L) {
                declaredLength + MIN_FREE_MARGIN_BYTES
            } else {
                MIN_FREE_SPACE_FOR_UNKNOWN_LENGTH
            }
            require(availableBytes(destination.parentFile!!) >= requiredSpace) {
                "Not enough free storage for ASR file ${item.name}"
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            FileOutputStream(temporary).use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        downloaded += count
                        require(downloaded <= item.maxBytes) {
                            "ASR file ${item.name} exceeded its size limit"
                        }
                        require(declaredLength < 0L || downloaded <= declaredLength) {
                            "ASR file ${item.name} exceeded its declared size"
                        }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        onProgress(downloaded, declaredLength)
                    }
                }
                output.fd.sync()
            }

            require(downloaded > 0L && (declaredLength < 0L || downloaded == declaredLength)) {
                "ASR file ${item.name} download is incomplete"
            }
            item.sha256?.let { expected ->
                val actual = digest.digest().toHex()
                require(actual.equals(expected, ignoreCase = true)) {
                    "SHA-256 verification failed for ${item.name}"
                }
            }
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (failure: Throwable) {
            temporary.delete()
            throw failure
        } finally {
            activeConnection.compareAndSet(connection, null)
            connection?.disconnect()
        }
    }

    private suspend fun openTrustedResponse(
        initialUrl: URL,
        activeConnection: AtomicReference<HttpURLConnection?>
    ): HttpURLConnection {
        var currentUrl = initialUrl
        for (redirectCount in 0..MAX_REDIRECTS) {
            currentCoroutineContext().ensureActive()
            validateUrl(currentUrl, initial = redirectCount == 0)
            val current = connectionFactory(currentUrl).apply {
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept-Encoding", "identity")
            }
            activeConnection.set(current)
            currentCoroutineContext().ensureActive()
            val status = try {
                current.responseCode
            } catch (failure: Throwable) {
                activeConnection.compareAndSet(current, null)
                current.disconnect()
                throw failure
            }

            if (status == HttpURLConnection.HTTP_OK) return current
            if (status in REDIRECT_CODES) {
                if (redirectCount >= MAX_REDIRECTS) {
                    activeConnection.compareAndSet(current, null)
                    current.disconnect()
                    throw IOException("Too many redirects while downloading ASR model")
                }
                val location = current.getHeaderField("Location")
                activeConnection.compareAndSet(current, null)
                current.disconnect()
                if (location.isNullOrBlank()) {
                    throw IOException("ASR model redirect has no Location header")
                }
                currentUrl = URL(currentUrl, location)
                validateUrl(currentUrl, initial = false)
                continue
            }

            activeConnection.compareAndSet(current, null)
            current.disconnect()
            throw IOException("ASR model server returned HTTP $status")
        }
        throw IOException("Too many redirects while downloading ASR model")
    }

    private fun validateUrl(url: URL, initial: Boolean) {
        require(url.protocol.equals("https", ignoreCase = true)) {
            "ASR model downloads require HTTPS"
        }
        require(url.userInfo == null && (url.port == -1 || url.port == 443)) {
            "Invalid ASR model server URL"
        }
        val host = url.host.lowercase()
        val allowed = if (initial) host == "huggingface.co" else host in ALLOWED_REDIRECT_HOSTS
        require(allowed) { "ASR model redirect to an untrusted host was blocked" }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
