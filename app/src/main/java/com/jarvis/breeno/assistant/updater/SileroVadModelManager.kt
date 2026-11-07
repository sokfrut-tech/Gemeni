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

/**
 * Commit-pinned Sherpa-ONNX Silero VAD model downloader.
 *
 * This model is optional and is never fetched implicitly: callers must pass
 * userConsent=true only after showing the user a separate, explicit disclosure.
 */
class SileroVadModelManager internal constructor(
    private val target: File,
    private val connectionFactory: (URL) -> HttpURLConnection,
    private val modelUrl: String = MODEL_URL,
    private val expectedSha256: String = SHA256,
    private val maxBytes: Long = MAX_BYTES,
    private val availableBytes: (File) -> Long = { it.usableSpace }
) {
    constructor(context: Context) : this(
        context.applicationContext.filesDir.resolve("models/vad/$FILE_NAME"),
        { url -> url.openConnection() as HttpURLConnection }
    )

    companion object {
        const val FILE_NAME = "silero_vad.onnx"
        private const val MODEL_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"
        private const val SHA256 =
            "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6"
        private const val MAX_BYTES = 32L * 1024L * 1024L
        private const val FREE_MARGIN_BYTES = 16L * 1024L * 1024L
        private const val BUFFER_SIZE = 64 * 1024
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_REDIRECTS = 5
        private const val USER_AGENT = "Jarvis-Android-VAD/1.0"
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private val ALLOWED_REDIRECT_HOSTS = setOf(
            "github.com",
            "release-assets.githubusercontent.com",
            "objects.githubusercontent.com"
        )
    }

    fun modelFile(): File = target.canonicalFile

    fun isReady(): Boolean {
        val file = modelFile()
        return file.isFile && file.length() in 1..maxBytes &&
            sha256(file).equals(expectedSha256, ignoreCase = true)
    }

    @OptIn(InternalCoroutinesApi::class)
    suspend fun ensure(
        userConsent: Boolean,
        onProgress: (Int) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        require(userConsent) {
            "Для загрузки модели Silero VAD требуется отдельное явное согласие пользователя"
        }
        if (isReady()) {
            onProgress(100)
            return@withContext modelFile()
        }

        val destination = modelFile()
        val parent = destination.parentFile
            ?: throw IOException("Silero VAD model directory is unavailable")
        check(parent.exists() || parent.mkdirs()) {
            "Could not create Silero VAD model directory"
        }
        val temporary = File(parent, destination.name + ".part")
        temporary.delete()

        val context = currentCoroutineContext()
        val activeConnection = AtomicReference<HttpURLConnection?>(null)
        val cancellationHandle = context[Job]?.invokeOnCompletion(
            onCancelling = true,
            invokeImmediately = true
        ) { cause ->
            if (cause != null) activeConnection.getAndSet(null)?.disconnect()
        }

        try {
            val connection = openTrustedResponse(URL(modelUrl), activeConnection)
            try {
                val declaredLength = connection.contentLengthLong
                require(declaredLength == -1L || declaredLength in 1..maxBytes) {
                    "Unexpected Silero VAD model size"
                }
                val requiredSpace = (if (declaredLength > 0L) declaredLength else maxBytes) +
                    FREE_MARGIN_BYTES
                require(availableBytes(parent) >= requiredSpace) {
                    "Not enough free storage for Silero VAD model"
                }

                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L
                FileOutputStream(temporary).use { output ->
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            context.ensureActive()
                            val count = input.read(buffer)
                            // Disconnect can unblock a read as EOF; cancellation wins over EOF.
                            context.ensureActive()
                            if (count < 0) break
                            downloaded += count
                            require(downloaded <= maxBytes) {
                                "Silero VAD model exceeded its size limit"
                            }
                            require(declaredLength < 0L || downloaded <= declaredLength) {
                                "Silero VAD model exceeded its declared size"
                            }
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                            if (declaredLength > 0L) {
                                onProgress((downloaded * 100L / declaredLength).toInt().coerceIn(0, 99))
                            }
                        }
                    }
                    output.fd.sync()
                }

                require(downloaded > 0L && (declaredLength < 0L || downloaded == declaredLength)) {
                    "Silero VAD model download is incomplete"
                }
                require(digest.digest().toHex().equals(expectedSha256, ignoreCase = true)) {
                    "Silero VAD model SHA-256 verification failed"
                }
                context.ensureActive()
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
                check(isReady()) { "Installed Silero VAD model verification failed" }
                onProgress(100)
                destination
            } finally {
                activeConnection.compareAndSet(connection, null)
                connection.disconnect()
            }
        } catch (failure: Throwable) {
            temporary.delete()
            throw failure
        } finally {
            cancellationHandle?.dispose()
            activeConnection.getAndSet(null)?.disconnect()
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
            val connection = connectionFactory(currentUrl).apply {
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept-Encoding", "identity")
            }
            activeConnection.set(connection)
            currentCoroutineContext().ensureActive()
            val status = try {
                connection.responseCode
            } catch (failure: Throwable) {
                activeConnection.compareAndSet(connection, null)
                connection.disconnect()
                throw failure
            }
            if (status == HttpURLConnection.HTTP_OK) return connection

            if (status in REDIRECT_CODES) {
                activeConnection.compareAndSet(connection, null)
                connection.disconnect()
                if (redirectCount >= MAX_REDIRECTS) {
                    throw IOException("Too many redirects while downloading Silero VAD")
                }
                val location = connection.getHeaderField("Location")
                if (location.isNullOrBlank()) {
                    throw IOException("Silero VAD redirect has no Location header")
                }
                currentUrl = URL(currentUrl, location)
                validateUrl(currentUrl, initial = false)
                continue
            }

            activeConnection.compareAndSet(connection, null)
            connection.disconnect()
            throw IOException("Silero VAD server returned HTTP $status")
        }
        throw IOException("Too many redirects while downloading Silero VAD")
    }

    private fun validateUrl(url: URL, initial: Boolean) {
        require(url.protocol.equals("https", ignoreCase = true)) {
            "Silero VAD downloads require HTTPS"
        }
        require(url.userInfo == null && (url.port == -1 || url.port == 443)) {
            "Invalid Silero VAD server URL"
        }
        val host = url.host.lowercase()
        val trusted = if (initial) host == "github.com" else host in ALLOWED_REDIRECT_HOSTS
        require(trusted) { "Silero VAD redirect to an untrusted host was blocked" }
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
