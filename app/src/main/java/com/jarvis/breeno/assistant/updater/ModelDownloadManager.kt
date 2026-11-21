package com.jarvis.breeno.assistant.updater

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/** Downloads Russian ASR model files into app-private storage with SHA-256 verification and mirror fallback. */
class ModelDownloadManager(private val context: Context) {
    companion object {
        const val RUSSIAN_MODEL = "sherpa-onnx-streaming-zipformer-small-ru-vosk-int8-2025-08-16"
        private const val BASE = "https://huggingface.co/csukuangfj/$RUSSIAN_MODEL/resolve/main/"
        private const val MIRROR_BASE = "https://hf-mirror.com/csukuangfj/$RUSSIAN_MODEL/resolve/main/"
        private val FILES = listOf(
            ModelFile("encoder.int8.onnx", 26_214_060L, "e0db705e94ec35d803b1df4f40cda23d064e1142977c80ab288430b109777a9d"),
            ModelFile("decoder.onnx", 2_093_080L, "89b3088a9e20e1ef7f2e85ce1a3478afe6a9c4ac57369cabcc4beb8e95328ea0"),
            ModelFile("joiner.int8.onnx", 259_417L, "b55784b071ab7512eab4c7c44e4f5478284ef33c83562cc6a249b972515a31e5"),
            ModelFile("tokens.txt", 6_388L, null),
        )
        private data class ModelFile(val name: String, val sizeBytes: Long, val sha256: String?)
    }

    fun modelDir(): File = context.filesDir.resolve("models/$RUSSIAN_MODEL").also { it.mkdirs() }

    fun isRussianAsrReady(): Boolean = FILES.all { file ->
        val target = modelDir().resolve(file.name)
        target.isFile && target.length() == file.sizeBytes &&
            (file.sha256 == null || ResumableModelDownloader.sha256(target) == file.sha256)
    }

    suspend fun ensureRussianAsr(onProgress: (Int) -> Unit = {}): File = withContext(Dispatchers.IO) {
        val dir = modelDir()
        FILES.forEachIndexed { index, item ->
            val target = dir.resolve(item.name)
            val valid = target.isFile && target.length() == item.sizeBytes &&
                (item.sha256 == null || ResumableModelDownloader.sha256(target) == item.sha256)
            if (!valid) {
                target.delete()
                ResumableModelDownloader.downloadVerified(
                    url = URL(BASE + item.name),
                    destination = target,
                    expectedBytes = item.sizeBytes,
                    expectedSha256 = item.sha256,
                    fallbackUrls = listOf(URL(MIRROR_BASE + item.name)),
                )
            }
            onProgress(((index + 1) * 100) / FILES.size)
        }
        dir
    }
}
