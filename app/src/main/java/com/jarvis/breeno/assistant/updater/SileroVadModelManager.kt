package com.jarvis.breeno.assistant.updater

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Commit-pinned official Sherpa-ONNX Silero VAD model. */
class SileroVadModelManager(private val context: Context) {
    companion object {
        private const val URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"
        private const val SHA256 = "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6"
        const val FILE_NAME = "silero_vad.onnx"
    }

    fun modelFile(): File = context.filesDir.resolve("models/vad/$FILE_NAME")

    fun isReady(): Boolean = modelFile().isFile && sha256(modelFile()) == SHA256

    suspend fun ensure(onProgress: (Int) -> Unit = {}): File = withContext(Dispatchers.IO) {
        val target = modelFile()
        target.parentFile?.mkdirs()
        if (!isReady()) {
            val tmp = File(target.parentFile, "$FILE_NAME.part")
            val connection = (URL(URL) as java.net.URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = true
            try {
                check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode} downloading Silero VAD" }
                val total = connection.contentLengthLong
                var read = 0L
                connection.inputStream.use { input -> tmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (total > 0) onProgress((read * 100 / total).toInt().coerceIn(0, 100))
                    }
                }}
                check(sha256(tmp) == SHA256) { "Silero VAD SHA-256 mismatch" }
                check(tmp.renameTo(target)) { "Cannot commit Silero VAD model" }
            } finally {
                connection.disconnect()
                tmp.delete()
            }
        }
        onProgress(100)
        target
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
