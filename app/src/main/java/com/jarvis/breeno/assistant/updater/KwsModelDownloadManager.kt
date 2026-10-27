package com.jarvis.breeno.assistant.updater

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Downloads and verifies the small always-on KWS model separately from the APK. */
class KwsModelDownloadManager(private val context: Context) {
    companion object {
        const val MODEL_ID = "sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20"
        private const val BASE = "https://huggingface.co/moeru-ai/$MODEL_ID/resolve/main/"
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
                download(URL(BASE + item.name), target)
                check(sha256(target) == item.sha256) { "KWS SHA-256 mismatch: ${item.name}" }
            }
            onProgress((index + 1) * 100 / FILES.size)
        }
        val keywords = dir.resolve("keywords.txt")
        keywords.writeText("JH AA1 R V IH0 S:2.0 #0.25 @JARVIS\n", Charsets.UTF_8)
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
            connection.inputStream.use { input -> tmp.outputStream().use { output -> input.copyTo(output, 64 * 1024) } }
            check(tmp.length() > 0) { "Empty KWS model: ${destination.name}" }
            check(tmp.renameTo(destination)) { "Cannot commit ${destination.name}" }
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
