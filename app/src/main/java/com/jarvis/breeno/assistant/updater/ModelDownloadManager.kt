package com.jarvis.breeno.assistant.updater

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Downloads model files into app-private storage. Models never enter the APK. */
class ModelDownloadManager(private val context: Context) {
    companion object {
        const val RUSSIAN_MODEL = "sherpa-onnx-streaming-zipformer-small-ru-vosk-int8-2025-08-16"
        private const val BASE = "https://huggingface.co/csukuangfj/$RUSSIAN_MODEL/resolve/main/"
        private const val MAX_SEGMENTS = 256
        private val CONTENT_RANGE = Regex("bytes (\\d+)-(\\d+)/(\\d+)")
        private val FILES = listOf(
            ModelFile("encoder.int8.onnx", "e0db705e94ec35d803b1df4f40cda23d064e1142977c80ab288430b109777a9d"),
            ModelFile("decoder.onnx", "89b3088a9e20e1ef7f2e85ce1a3478afe6a9c4ac57369cabcc4beb8e95328ea0"),
            ModelFile("joiner.int8.onnx", "b55784b071ab7512eab4c7c44e4f5478284ef33c83562cc6a249b972515a31e5"),
            ModelFile("tokens.txt", null),
        )
        private data class ModelFile(val name: String, val sha256: String?)
    }

    fun modelDir(): File = context.filesDir.resolve("models/$RUSSIAN_MODEL").also { it.mkdirs() }

    fun isRussianAsrReady(): Boolean = FILES.all { file ->
        val target = modelDir().resolve(file.name)
        target.isFile && target.length() > 0L && (file.sha256 == null || sha256(target) == file.sha256)
    }

    suspend fun ensureRussianAsr(onProgress: (Int) -> Unit = {}): File = withContext(Dispatchers.IO) {
        val dir = modelDir()
        FILES.forEachIndexed { index, item ->
            val target = dir.resolve(item.name)
            if (!target.isFile || target.length() == 0L || (item.sha256 != null && sha256(target) != item.sha256)) {
                target.delete()
                download(URL(BASE + item.name), target)
                item.sha256?.let { expected ->
                    if (sha256(target) != expected) {
                        target.delete()
                        error("SHA-256 mismatch for ${item.name}; the invalid file was removed. Retry the download.")
                    }
                }
            }
            onProgress(((index + 1) * 100) / FILES.size)
        }
        dir
    }

    private suspend fun download(url: URL, destination: File) {
        val coroutineContext = currentCoroutineContext()
        val tmp = File(destination.parentFile, destination.name + ".part")
        var segmentCount = 0
        while (true) {
            coroutineContext.ensureActive()
            check(++segmentCount <= MAX_SEGMENTS) { "Too many partial responses for ${destination.name}" }
            val offset = tmp.takeIf { it.isFile }?.length() ?: 0L
            var lastError: Throwable? = null
            var completedSegment = false

            for (attempt in 1..3) {
                coroutineContext.ensureActive()
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30_000
                    readTimeout = 60_000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Jarvis-Android/1.0")
                    if (offset > 0L) setRequestProperty("Range", "bytes=$offset-")
                }
                try {
                    val code = connection.responseCode
                    check(code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_PARTIAL) {
                        "HTTP $code for $url"
                    }

                    val append: Boolean
                    val expectedEnd: Long?
                    val totalLength: Long?
                    if (code == HttpURLConnection.HTTP_PARTIAL) {
                        val header = connection.getHeaderField("Content-Range")
                        val match = header?.let(CONTENT_RANGE::matchEntire)
                            ?: error("Missing or invalid Content-Range for ${destination.name}: $header")
                        val start = match.groupValues[1].toLong()
                        val end = match.groupValues[2].toLong()
                        totalLength = match.groupValues[3].toLong()
                        check(start == offset && end >= start && end < totalLength) {
                            "Unexpected Content-Range '$header' at offset $offset"
                        }
                        append = offset > 0L
                        expectedEnd = end
                    } else {
                        // A server that ignores Range sends the complete representation.
                        append = false
                        expectedEnd = null
                        totalLength = connection.contentLengthLong.takeIf { it >= 0L }
                    }

                    var received = 0L
                    connection.inputStream.use { input ->
                        FileOutputStream(tmp, append).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                coroutineContext.ensureActive()
                                val count = input.read(buffer)
                                coroutineContext.ensureActive()
                                if (count < 0) break
                                if (count > 0) {
                                    output.write(buffer, 0, count)
                                    received += count
                                }
                            }
                            output.fd.sync()
                        }
                    }

                    if (expectedEnd != null) {
                        val expectedBytes = expectedEnd - offset + 1L
                        check(received == expectedBytes) {
                            "Short partial response for ${destination.name}: received $received of $expectedBytes bytes"
                        }
                        check(tmp.length() == expectedEnd + 1L) {
                            "Partial file length does not match Content-Range for ${destination.name}"
                        }
                        check(totalLength != null && totalLength > 0L)
                        if (tmp.length() < totalLength) {
                            completedSegment = true
                        } else {
                            check(tmp.length() == totalLength) { "Downloaded more bytes than advertised for ${destination.name}" }
                            check(tmp.renameTo(destination)) { "Cannot commit ${destination.name}" }
                            return
                        }
                    } else {
                        check(received > 0L) { "Empty model file: ${destination.name}" }
                        if (totalLength != null) check(tmp.length() == totalLength) {
                            "Incomplete response for ${destination.name}: ${tmp.length()} of $totalLength bytes"
                        }
                        check(tmp.renameTo(destination)) { "Cannot commit ${destination.name}" }
                        return
                    }
                    if (completedSegment) break
                } catch (error: Throwable) {
                    coroutineContext.ensureActive()
                    lastError = error
                    if (attempt == 3) throw error
                    delay(750L * attempt)
                } finally {
                    connection.disconnect()
                }
            }
            if (!completedSegment && lastError != null) throw lastError
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
