package com.jarvis.breeno.assistant.updater

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Shared resumable HTTP downloader with explicit 301/302/303/307/308 redirect handling,
 * mirror fallback support (e.g. hf-mirror.com), SHA-256 verification, and atomic commit.
 */
object ResumableModelDownloader {
    private const val MAX_SEGMENTS = 512
    private const val MAX_REDIRECTS = 6
    private val CONTENT_RANGE = Regex("""bytes (\d+)-(\d+)/(\d+)""")

    suspend fun downloadVerified(
        url: URL,
        destination: File,
        expectedBytes: Long?,
        expectedSha256: String?,
        fallbackUrls: List<URL> = emptyList(),
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _, _ -> },
    ) {
        val candidates = listOf(url) + fallbackUrls
        var lastError: Throwable? = null
        for (candidate in candidates) {
            try {
                downloadSingleUrl(
                    url = candidate,
                    destination = destination,
                    expectedBytes = expectedBytes,
                    expectedSha256 = expectedSha256,
                    onProgress = onProgress,
                )
                return
            } catch (error: Throwable) {
                currentCoroutineContext().ensureActive()
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("Не удалось скачать ${destination.name}")
    }

    private suspend fun downloadSingleUrl(
        url: URL,
        destination: File,
        expectedBytes: Long?,
        expectedSha256: String?,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit,
    ) {
        val ctx = currentCoroutineContext()
        destination.parentFile?.mkdirs()
        val tmp = File(destination.parentFile, "${destination.name}.part")
        var segmentCount = 0

        while (true) {
            ctx.ensureActive()
            check(++segmentCount <= MAX_SEGMENTS) { "Too many partial responses for ${destination.name}" }
            var lastError: Throwable? = null
            var completedSegment = false

            for (attempt in 1..3) {
                ctx.ensureActive()
                val offset = tmp.takeIf { it.isFile }?.length() ?: 0L
                if (expectedBytes != null && offset == expectedBytes && expectedBytes > 0L) {
                    verifyAndCommit(tmp, destination, expectedBytes, expectedSha256)
                    onProgress(100, expectedBytes, expectedBytes)
                    return
                }
                if (expectedBytes != null && offset > expectedBytes) {
                    tmp.delete()
                }

                val resumeOffset = tmp.takeIf { it.isFile }?.length() ?: 0L
                val connection = openFollowingRedirects(url, resumeOffset)

                try {
                    val code = connection.responseCode
                    if (code == 416 && resumeOffset > 0L) {
                        tmp.delete()
                        continue
                    }
                    check(code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_PARTIAL) {
                        "HTTP $code при загрузке ${destination.name}"
                    }

                    val append: Boolean
                    val expectedEnd: Long?
                    val totalLength: Long?
                    if (code == HttpURLConnection.HTTP_PARTIAL) {
                        val header = connection.getHeaderField("Content-Range")
                        val match = header?.let(CONTENT_RANGE::matchEntire)
                            ?: error("Некорректный Content-Range для ${destination.name}: $header")
                        val start = match.groupValues[1].toLong()
                        val end = match.groupValues[2].toLong()
                        totalLength = match.groupValues[3].toLong()
                        check(start == resumeOffset && end >= start && end < totalLength) {
                            "Неожиданный Content-Range '$header' на позиции $resumeOffset"
                        }
                        append = resumeOffset > 0L
                        expectedEnd = end
                    } else {
                        append = false
                        expectedEnd = null
                        totalLength = connection.contentLengthLong.takeIf { it > 0L } ?: expectedBytes
                    }

                    val targetTotal = totalLength ?: expectedBytes ?: -1L
                    var currentFileSize = if (append) resumeOffset else 0L
                    var received = 0L
                    var lastReportPct = -1

                    connection.inputStream.use { input ->
                        FileOutputStream(tmp, append).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                ctx.ensureActive()
                                val count = input.read(buffer)
                                ctx.ensureActive()
                                if (count < 0) break
                                if (count > 0) {
                                    output.write(buffer, 0, count)
                                    received += count
                                    currentFileSize += count
                                    if (targetTotal > 0L) {
                                        val pct = ((currentFileSize * 100L) / targetTotal).toInt().coerceIn(0, 99)
                                        if (pct != lastReportPct) {
                                            lastReportPct = pct
                                            onProgress(pct, currentFileSize, targetTotal)
                                        }
                                    }
                                }
                            }
                            output.fd.sync()
                        }
                    }

                    if (expectedEnd != null) {
                        val expectedChunk = expectedEnd - resumeOffset + 1L
                        check(received == expectedChunk) {
                            "Обрыв частичной загрузки ${destination.name}: получено $received из $expectedChunk Б"
                        }
                        check(totalLength != null && totalLength > 0L)
                        if (tmp.length() < totalLength) {
                            completedSegment = true
                        } else {
                            verifyAndCommit(tmp, destination, expectedBytes ?: totalLength, expectedSha256)
                            onProgress(100, destination.length(), destination.length())
                            return
                        }
                    } else {
                        check(received > 0L) { "Пустой ответ при загрузке ${destination.name}" }
                        if (totalLength != null) {
                            check(tmp.length() == totalLength) {
                                "Неполная загрузка ${destination.name}: ${tmp.length()} из $totalLength Б"
                            }
                        }
                        verifyAndCommit(tmp, destination, expectedBytes, expectedSha256)
                        onProgress(100, destination.length(), destination.length())
                        return
                    }
                    if (completedSegment) break
                } catch (error: Throwable) {
                    ctx.ensureActive()
                    lastError = error
                    if (attempt == 3) throw error
                    delay(800L * attempt)
                } finally {
                    connection.disconnect()
                }
            }

            if (!completedSegment && lastError != null) {
                throw lastError
            }
        }
    }

    private fun openFollowingRedirects(initialUrl: URL, resumeOffset: Long): HttpURLConnection {
        var current = initialUrl
        for (hop in 0..MAX_REDIRECTS) {
            val conn = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Jarvis-Android/1.0")
                if (resumeOffset > 0L) {
                    setRequestProperty("Range", "bytes=$resumeOffset-")
                }
            }
            val code = conn.responseCode
            if (code in setOf(301, 302, 303, 307, 308)) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                check(!location.isNullOrBlank()) { "Пустой Redirect Location (HTTP $code)" }
                current = URL(current, location)
                continue
            }
            return conn
        }
        error("Слишком много редиректов при загрузке $initialUrl")
    }

    private fun verifyAndCommit(
        tmp: File,
        destination: File,
        expectedBytes: Long?,
        expectedSha256: String?,
    ) {
        if (expectedBytes != null && expectedBytes > 0L) {
            if (tmp.length() != expectedBytes) {
                val actualLen = tmp.length()
                tmp.delete()
                error("Размер ${destination.name} не совпал ($actualLen вместо $expectedBytes Б)")
            }
        }
        if (!expectedSha256.isNullOrBlank()) {
            val actualHash = sha256(tmp)
            if (!actualHash.equals(expectedSha256, ignoreCase = true)) {
                tmp.delete()
                error("SHA-256 не совпал для ${destination.name}; повреждённый файл удалён")
            }
        }
        if (destination.exists()) {
            destination.delete()
        }
        check(tmp.renameTo(destination)) { "Не удалось сохранить ${destination.name}" }
    }

    fun sha256(file: File): String {
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
