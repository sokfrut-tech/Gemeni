package com.jarvis.breeno.assistant.updater

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Downloads model files into app-private storage. Models never enter the APK. */
class ModelDownloadManager(private val context: Context) {
    companion object {
        const val RUSSIAN_MODEL = "sherpa-onnx-streaming-zipformer-small-ru-vosk-int8-2025-08-16"
        private const val BASE = "https://huggingface.co/csukuangfj/$RUSSIAN_MODEL/resolve/main/"

        private val FILES = listOf(
            ModelFile("encoder.int8.onnx", "e0db705e94ec35d803b1df4f40cda23d064e1142977c80ab288430b109777a9d"),
            ModelFile("decoder.onnx", "89b3088a0f9e20e1ef7f85ce1a3478afe6a9c4ac57369cabcc4beb8e95328ea0"),
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
                download(URL(BASE + item.name), target)
                item.sha256?.let { expected ->
                    check(sha256(target) == expected) { "SHA-256 mismatch for ${item.name}" }
                }
            }
            onProgress(((index + 1) * 100) / FILES.size)
        }
        dir
    }

    private fun download(url: URL, destination: File) {
        val tmp = File(destination.parentFile, destination.name + ".part")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        try {
            check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode} for $url" }
            connection.inputStream.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            }
            check(tmp.length() > 0) { "Empty model file: ${destination.name}" }
            if (!tmp.renameTo(destination)) error("Cannot commit ${destination.name}")
        } finally {
            connection.disconnect()
            tmp.delete()
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
