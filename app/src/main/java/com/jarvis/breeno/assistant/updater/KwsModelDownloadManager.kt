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

/** Downloads and verifies the small always-on KWS model separately from the APK. */
class KwsModelDownloadManager(private val context: Context) {
    companion object {
        const val MODEL_ID = "sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20"
        private const val BASE = "https://huggingface.co/moeru-ai/$MODEL_ID/resolve/main/"
        private const val MAX_SEGMENTS = 256
        private val CONTENT_RANGE = Regex("bytes (\\d+)-(\\d+)/(\\d+)")
        private val FILES = listOf(
            ModelFile("encoder.onnx", "540ff509ed89bd22afe04bf7049a54bb1c95c6d8a18742ea9691910cdb5f859e"),
            ModelFile("decoder.onnx", "63a22dd60f40fff082ac3e09afa507f6787da36df76ded2fbe145fa233e22c21"),
            ModelFile("joiner.onnx", "76f7a24ed0c08633af14b2ee377f747af880d3b65eeba2cd3f31f3380fb73e8d"),
            ModelFile("tokens.txt", "2d3f32311f9b692b964da3c90e830258d3e78e013cb0c992dbfb15cd5a1a71b0"),
        )
        private data class ModelFile(val name: String, val sha256: String)
    }

    fun modelDir(): File = context.filesDir.resolve("models/$MODEL_ID").also { it.mkdirs() }

    fun isReady(): Boolean = FILES.all { item ->
        modelDir().resolve(item.name).let { it.isFile && it.length() > 0 && sha256(it) == item.sha256 }
    }

    suspend fun ensure(onProgress: (Int) -> Unit = {}): File = withContext(Dispatchers.IO) {
        val dir = modelDir()
        FILES.forEachIndexed { index, item ->
            val target = dir.resolve(item.name)
            if (!target.isFile || sha256(target) != item.sha256) {
                target.delete()
                download(URL(BASE + item.name), target)
                check(sha256(target) == item.sha256) { "KWS SHA-256 mismatch: ${item.name}" }
            }
            onProgress((index + 1) * 100 / FILES.size)
        }
        val keywords = dir.resolve("keywords.txt")
        keywords.writeText("JH AA1 R V IH0 S:2.0 #0.25 @JARVIS\n", Charsets.UTF_8)
        dir
    }

    private suspend fun download(url: URL, destination: File) {
        val coroutineContext = currentCoroutineContext()
        val tmp = File(destination.parentFile, destination.name + ".part")
        var segmentCount = 0
        while (true) {
            coroutineContext.ensureActive()
            check(++segmentCount <= MAX_SEGMENTS) { "Too many partial responses for ${destination.name}" }
            var lastError: Throwable? = null
            var completedSegment = false

            for (attempt in 1..3) {
                coroutineContext.ensureActive()
                // Recompute after every failed response: bytes already received are retained.
                val offset = tmp.takeIf { it.isFile }?.length() ?: 0L
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
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
                        check(received > 0L) { "Empty KWS model: ${destination.name}" }
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
