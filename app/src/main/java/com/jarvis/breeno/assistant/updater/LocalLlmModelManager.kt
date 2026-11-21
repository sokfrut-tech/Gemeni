package com.jarvis.breeno.assistant.updater

import android.content.Context
import com.jarvis.breeno.assistant.core.ModelPackDescriptor
import com.jarvis.breeno.assistant.core.ModelShard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

data class LocalLlmPack(
    val id: String,
    val title: String,
    val description: String,
    val downloadUrl: String,
    val descriptor: ModelPackDescriptor,
) {
    val fileName: String get() = descriptor.shards.first().fileName
    val sizeBytes: Long get() = descriptor.shards.first().sizeBytes
    val sha256: String get() = descriptor.shards.first().sha256
}

/**
 * Manages immutable, SHA-256-verified Qwen2.5 GGUF model packs in app-private storage.
 * Model weights never enter the APK and require explicit user action to download.
 */
class LocalLlmModelManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_llm_packs", Context.MODE_PRIVATE)

    fun catalog(): List<LocalLlmPack> = CATALOG

    fun modelFile(pack: LocalLlmPack): File =
        context.filesDir.resolve("models/llm/${pack.id}/${pack.fileName}")

    private fun markerFile(pack: LocalLlmPack): File =
        context.filesDir.resolve("models/llm/${pack.id}/${pack.fileName}.verified")

    fun isPackReady(packId: String): Boolean {
        val pack = findPack(packId) ?: return false
        val target = modelFile(pack)
        val marker = markerFile(pack)
        if (!target.isFile || target.length() != pack.sizeBytes) return false
        val expectedMarker = "${pack.sizeBytes}:${pack.sha256}"
        return marker.isFile && runCatching { marker.readText(Charsets.UTF_8).trim() == expectedMarker }.getOrDefault(false)
    }

    fun selectedPackId(): String? = prefs.getString(KEY_SELECTED_PACK, null)

    fun selectPack(packId: String) {
        if (findPack(packId) != null) {
            prefs.edit().putString(KEY_SELECTED_PACK, packId).apply()
        }
    }

    fun activeReadyPack(): LocalLlmPack? {
        val selected = selectedPackId()?.let(::findPack)
        if (selected != null && isPackReady(selected.id)) {
            return selected
        }
        // Prefer larger ready pack if multiple are installed and no explicit choice was made.
        return CATALOG.sortedByDescending { it.sizeBytes }.firstOrNull { isPackReady(it.id) }
    }

    fun findPack(packId: String): LocalLlmPack? = CATALOG.firstOrNull { it.id == packId }

    suspend fun ensurePack(
        packId: String,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        val pack = findPack(packId) ?: error("Неизвестная модель: $packId")
        pack.descriptor.validate()

        val target = modelFile(pack)
        val marker = markerFile(pack)
        if (!isPackReady(pack.id)) {
            if (target.isFile && target.length() == pack.sizeBytes &&
                ResumableModelDownloader.sha256(target).equals(pack.sha256, ignoreCase = true)
            ) {
                writeVerifiedMarker(pack, marker)
            } else {
                target.delete()
                marker.delete()
                ResumableModelDownloader.downloadVerified(
                    url = URL(pack.downloadUrl),
                    destination = target,
                    expectedBytes = pack.sizeBytes,
                    expectedSha256 = pack.sha256,
                    onProgress = onProgress,
                )
                writeVerifiedMarker(pack, marker)
            }
        } else {
            onProgress(100, pack.sizeBytes, pack.sizeBytes)
        }

        if (selectedPackId() == null) {
            selectPack(pack.id)
        }
        target
    }

    suspend fun verifyBeforeLoad(pack: LocalLlmPack): File = withContext(Dispatchers.IO) {
        val target = modelFile(pack)
        val marker = markerFile(pack)
        check(target.isFile && target.length() == pack.sizeBytes) {
            "Файл модели ${pack.title} не найден или повреждён"
        }
        if (!isPackReady(pack.id)) {
            val actualHash = ResumableModelDownloader.sha256(target)
            check(actualHash.equals(pack.sha256, ignoreCase = true)) {
                "Контрольная сумма модели ${pack.title} не совпала"
            }
            writeVerifiedMarker(pack, marker)
        }
        target
    }

    fun deletePack(packId: String) {
        val pack = findPack(packId) ?: return
        modelFile(pack).parentFile?.deleteRecursively()
        if (selectedPackId() == packId) {
            prefs.edit().remove(KEY_SELECTED_PACK).apply()
        }
    }

    private fun writeVerifiedMarker(pack: LocalLlmPack, marker: File) {
        marker.parentFile?.mkdirs()
        marker.writeText("${pack.sizeBytes}:${pack.sha256}", Charsets.UTF_8)
    }

    companion object {
        const val PACK_QWEN_05B = "qwen2.5-0.5b-q4_k_m"
        const val PACK_QWEN_15B = "qwen2.5-1.5b-q4_k_m"
        private const val KEY_SELECTED_PACK = "selected_llm_pack_id"

        val CATALOG: List<LocalLlmPack> = listOf(
            LocalLlmPack(
                id = PACK_QWEN_05B,
                title = "Qwen 2.5 0.5B Instruct (Быстрая)",
                description = "Компактный офлайн-мозг для быстрого общения на русском (~469 МБ).",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/9217f5db79a29953eb74d5343926648285ec7e67/qwen2.5-0.5b-instruct-q4_k_m.gguf",
                descriptor = ModelPackDescriptor(
                    packId = PACK_QWEN_05B,
                    version = "2.5-0.5B-Q4_K_M",
                    runtime = "llama.cpp",
                    architecture = "qwen2",
                    quantization = "Q4_K_M",
                    licenseId = "Apache-2.0",
                    sourceRevision = "9217f5db79a29953eb74d5343926648285ec7e67",
                    shards = listOf(
                        ModelShard(
                            index = 0,
                            fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
                            sizeBytes = 491_400_032L,
                            sha256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
                        )
                    ),
                ),
            ),
            LocalLlmPack(
                id = PACK_QWEN_15B,
                title = "Qwen 2.5 1.5B Instruct (Умная для OPPO)",
                description = "Более умная русская модель для 12/16 ГБ RAM OPPO Find X6 Pro (~1.04 ГБ).",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/91cad51170dc346986eccefdc2dd33a9da36ead9/qwen2.5-1.5b-instruct-q4_k_m.gguf",
                descriptor = ModelPackDescriptor(
                    packId = PACK_QWEN_15B,
                    version = "2.5-1.5B-Q4_K_M",
                    runtime = "llama.cpp",
                    architecture = "qwen2",
                    quantization = "Q4_K_M",
                    licenseId = "Apache-2.0",
                    sourceRevision = "91cad51170dc346986eccefdc2dd33a9da36ead9",
                    shards = listOf(
                        ModelShard(
                            index = 0,
                            fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
                            sizeBytes = 1_117_320_736L,
                            sha256 = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e",
                        )
                    ),
                ),
            ),
        )

        init {
            CATALOG.forEach { it.descriptor.validate() }
        }
    }
}
