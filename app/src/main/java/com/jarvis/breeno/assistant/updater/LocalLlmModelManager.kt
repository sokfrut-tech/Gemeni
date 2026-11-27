package com.jarvis.breeno.assistant.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jarvis.breeno.assistant.core.JarvisPersona
import com.jarvis.breeno.assistant.core.ModelPackDescriptor
import com.jarvis.breeno.assistant.core.ModelShard
import com.jarvis.breeno.assistant.core.PromptTemplateFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

data class LlmGenerationSettings(
    val temperature: Float = 0.65f,
    val topP: Float = 0.90f,
    val maxTokens: Int = 384,
    val contextLength: Int = 2048,
    val cpuThreads: Int = 4,
    val customSystemPrompt: String = "",
)

data class LocalLlmPack(
    val id: String,
    val title: String,
    val description: String,
    val downloadUrl: String,
    val descriptor: ModelPackDescriptor,
    val family: String = "Qwen 2.5 Instruct",
    val isCustom: Boolean = false,
) {
    val fileName: String get() = descriptor.shards.first().fileName
    val sizeBytes: Long get() = descriptor.shards.first().sizeBytes
    val sha256: String get() = descriptor.shards.first().sha256
}

private data class StoredCustomPack(
    val id: String,
    val title: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
)

/**
 * Manages immutable, SHA-256-verified Dolphin 3.0 / NousResearch Hermes 3 / Qwen 2.5 GGUF packs
 * as well as user-imported local .gguf files in app-private storage.
 * Model weights never enter the APK and require explicit user action to download or import.
 */
class LocalLlmModelManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_llm_packs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun catalog(): List<LocalLlmPack> = CATALOG

    fun customPacks(): List<LocalLlmPack> = loadStoredCustomPacks().map { rec ->
        LocalLlmPack(
            id = rec.id,
            title = rec.title,
            description = "Пользовательский GGUF (${rec.sizeBytes / (1024 * 1024)} МБ)",
            downloadUrl = "local://custom/${rec.id}",
            descriptor = ModelPackDescriptor(
                packId = rec.id,
                version = "custom",
                runtime = "llama.cpp",
                architecture = "gguf",
                quantization = "custom",
                licenseId = "User-Imported",
                sourceRevision = "local",
                shards = listOf(
                    ModelShard(
                        index = 0,
                        fileName = rec.fileName,
                        sizeBytes = rec.sizeBytes,
                        sha256 = rec.sha256,
                    )
                ),
            ),
            family = FAMILY_CUSTOM,
            isCustom = true,
        )
    }

    fun allPacks(): List<LocalLlmPack> = CATALOG + customPacks()

    fun modelFile(pack: LocalLlmPack): File {
        return if (pack.isCustom) {
            val customRoot = context.filesDir.resolve("models/llm/custom")
            val packDir = customRoot.resolve(pack.id)
            val candidate = packDir.resolve(sanitizeCustomFileName(pack.fileName))
            val canonRoot = customRoot.canonicalFile
            val canonTarget = candidate.canonicalFile
            check(canonTarget.path.startsWith(canonRoot.path + File.separator)) {
                "Некорректный путь модели: ${pack.fileName}"
            }
            candidate
        } else {
            context.filesDir.resolve("models/llm/${pack.id}/${pack.fileName}")
        }
    }

    private fun markerFile(pack: LocalLlmPack): File =
        File(modelFile(pack).parentFile, "${sanitizeCustomFileName(pack.fileName)}.verified")

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

    fun selectedPersona(): JarvisPersona =
        JarvisPersona.fromId(prefs.getString(KEY_SELECTED_PERSONA, JarvisPersona.STARK_JARVIS.id))

    fun selectPersona(persona: JarvisPersona) {
        prefs.edit().putString(KEY_SELECTED_PERSONA, persona.id).apply()
    }

    fun selectedPromptTemplate(): PromptTemplateFormat =
        PromptTemplateFormat.fromId(prefs.getString(KEY_PROMPT_TEMPLATE, PromptTemplateFormat.AUTO.id))

    fun selectPromptTemplate(format: PromptTemplateFormat) {
        prefs.edit().putString(KEY_PROMPT_TEMPLATE, format.id).apply()
    }

    fun generationSettings(): LlmGenerationSettings = clampGenerationSettings(
        LlmGenerationSettings(
            temperature = prefs.getFloat(KEY_GEN_TEMP, 0.65f),
            topP = prefs.getFloat(KEY_GEN_TOP_P, 0.90f),
            maxTokens = prefs.getInt(KEY_GEN_MAX_TOKENS, 384),
            contextLength = prefs.getInt(KEY_GEN_CTX, 2048),
            cpuThreads = prefs.getInt(KEY_GEN_THREADS, 4),
            customSystemPrompt = prefs.getString(KEY_GEN_CUSTOM_PROMPT, "") ?: "",
        )
    )

    fun saveGenerationSettings(raw: LlmGenerationSettings) {
        val clamped = clampGenerationSettings(raw)
        prefs.edit()
            .putFloat(KEY_GEN_TEMP, clamped.temperature)
            .putFloat(KEY_GEN_TOP_P, clamped.topP)
            .putInt(KEY_GEN_MAX_TOKENS, clamped.maxTokens)
            .putInt(KEY_GEN_CTX, clamped.contextLength)
            .putInt(KEY_GEN_THREADS, clamped.cpuThreads)
            .putString(KEY_GEN_CUSTOM_PROMPT, clamped.customSystemPrompt)
            .apply()
    }

    fun activeReadyPack(): LocalLlmPack? {
        val selected = selectedPackId()?.let(::findPack)
        if (selected != null && isPackReady(selected.id)) {
            return selected
        }
        // Prefer larger ready pack if multiple are installed and no explicit choice was made.
        return allPacks().sortedByDescending { it.sizeBytes }.firstOrNull { isPackReady(it.id) }
    }

    fun findPack(packId: String): LocalLlmPack? = allPacks().firstOrNull { it.id == packId }

    suspend fun ensurePack(
        packId: String,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        val pack = findPack(packId) ?: error("Неизвестная модель: $packId")
        check(!pack.isCustom) { "Пользовательская модель уже должна быть импортирована с устройства" }
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
                val mirrorUrl = URL(pack.downloadUrl.replace("https://huggingface.co/", "https://hf-mirror.com/"))
                ResumableModelDownloader.downloadVerified(
                    url = URL(pack.downloadUrl),
                    destination = target,
                    expectedBytes = pack.sizeBytes,
                    expectedSha256 = pack.sha256,
                    fallbackUrls = listOf(mirrorUrl),
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

    private fun openUriStream(uri: Uri): InputStream {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val direct = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
        if (direct != null) return direct
        val pfd = runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
        if (pfd != null) {
            return ParcelFileDescriptor.AutoCloseInputStream(pfd)
        }
        error("Не удалось открыть выбранный файл по URI ($uri)")
    }

    suspend fun importCustomGguf(
        uri: Uri,
        displayName: String,
        onProgress: (percent: Int) -> Unit = {},
    ): LocalLlmPack = withContext(Dispatchers.IO) {
        var safeName = sanitizeCustomFileName(displayName)
        val packId = "custom_${System.currentTimeMillis()}"
        val customRoot = context.filesDir.resolve("models/llm/custom")
        val packDir = customRoot.resolve(packId)
        packDir.mkdirs()

        val expectedSize = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) cursor.getLong(idx) else -1L
            } ?: -1L
        }.getOrDefault(-1L)

        val digest = MessageDigest.getInstance("SHA-256")
        var copiedBytes = 0L
        var lastPct = -1

        fun emitProgress(copied: Long) {
            val pct = if (expectedSize > 0) {
                ((copied * 100L) / expectedSize).toInt().coerceIn(0, 99)
            } else {
                ((copied / (10L * 1024L * 1024L)) % 90 + 5).toInt().coerceIn(5, 95)
            }
            if (pct != lastPct) {
                lastPct = pct
                onProgress(pct)
            }
        }

        try {
            val headerProbe = ByteArray(8)
            val probeLen = openUriStream(uri).use { readHeaderAtLeast(it, headerProbe, headerProbe.size) }
            val actualHeader = headerProbe.copyOf(probeLen)
            check(probeLen >= 4) {
                formatHeaderDiagnostic(displayName, expectedSize, actualHeader)
            }

            var target = packDir.resolve(safeName)
            var tmpFile = packDir.resolve("$safeName.tmp")
            val canonRoot = customRoot.canonicalFile
            check(target.canonicalFile.path.startsWith(canonRoot.path + File.separator)) {
                "Недопустимое имя файла: $safeName"
            }

            when {
                isValidGgufMagic(actualHeader) -> {
                    val buf = ByteArray(128 * 1024)
                    openUriStream(uri).use { stream ->
                        tmpFile.outputStream().use { out ->
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = stream.read(buf)
                                if (read <= 0) break
                                out.write(buf, 0, read)
                                digest.update(buf, 0, read)
                                copiedBytes += read
                                emitProgress(copiedBytes)
                            }
                            out.flush()
                        }
                    }
                }

                isZipMagic(actualHeader) -> {
                    val extracted = openUriStream(uri).use { rawStream ->
                        copyZipGgufEntry(
                            rawStream = rawStream,
                            destTmpFile = tmpFile,
                            digest = digest,
                            checkCancelled = { currentCoroutineContext().isActive },
                            onBytesCopied = ::emitProgress,
                        )
                    }
                    copiedBytes = extracted.copiedBytes
                    if (extracted.entryName.isNotBlank()) {
                        val entrySafe = sanitizeCustomFileName(extracted.entryName)
                        val newTarget = packDir.resolve(entrySafe)
                        val newTmp = packDir.resolve("$entrySafe.tmp")
                        check(newTarget.canonicalFile.path.startsWith(canonRoot.path + File.separator)) {
                            "Недопустимое имя файла: $entrySafe"
                        }
                        if (newTmp != tmpFile && tmpFile.renameTo(newTmp)) {
                            safeName = entrySafe
                            target = newTarget
                            tmpFile = newTmp
                        }
                    }
                }

                isGzipMagic(actualHeader) -> {
                    val buf = ByteArray(128 * 1024)
                    GZIPInputStream(openUriStream(uri)).use { gz ->
                        val gzHeader = ByteArray(8)
                        val gzRead = readHeaderAtLeast(gz, gzHeader, gzHeader.size)
                        check(gzRead >= 4 && isValidGgufMagic(gzHeader.copyOf(gzRead))) {
                            formatHeaderDiagnostic(displayName, expectedSize, gzHeader.copyOf(gzRead))
                        }
                        tmpFile.outputStream().use { out ->
                            out.write(gzHeader, 0, gzRead)
                            digest.update(gzHeader, 0, gzRead)
                            copiedBytes += gzRead
                            emitProgress(copiedBytes)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = gz.read(buf)
                                if (read <= 0) break
                                out.write(buf, 0, read)
                                digest.update(buf, 0, read)
                                copiedBytes += read
                                emitProgress(copiedBytes)
                            }
                            out.flush()
                        }
                    }
                }

                else -> error(formatHeaderDiagnostic(displayName, expectedSize, actualHeader))
            }

            check(copiedBytes > 1024L) { "Скопированный GGUF-файл слишком мал ($copiedBytes байт)" }
            if (target.exists()) target.delete()
            check(tmpFile.renameTo(target)) { "Не удалось сохранить файл модели в хранилище" }

            val shaHex = digest.digest().joinToString("") { "%02x".format(it) }
            val record = StoredCustomPack(
                id = packId,
                title = safeName.removeSuffix(".gguf"),
                fileName = safeName,
                sizeBytes = copiedBytes,
                sha256 = shaHex,
            )
            val marker = File(packDir, "$safeName.verified")
            marker.writeText("${record.sizeBytes}:${record.sha256}", Charsets.UTF_8)

            val updated = loadStoredCustomPacks() + record
            saveStoredCustomPacks(updated)
            selectPack(record.id)
            onProgress(100)

            findPack(record.id) ?: error("Импортированная модель не найдена после сохранения")
        } catch (err: Throwable) {
            packDir.deleteRecursively()
            throw err
        }
    }

    suspend fun downloadCustomGgufFromUrl(
        downloadUrl: String,
        displayName: String,
        expectedSizeBytes: Long = -1L,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _, _ -> },
    ): LocalLlmPack = withContext(Dispatchers.IO) {
        val cleanUrl = downloadUrl.trim()
        require(cleanUrl.startsWith("https://", ignoreCase = true)) {
            "Загрузка GGUF разрешена только по защищённому HTTPS URL"
        }

        val safeName = sanitizeCustomFileName(displayName)
        val packId = "custom_${System.currentTimeMillis()}"
        val customRoot = context.filesDir.resolve("models/llm/custom")
        val packDir = customRoot.resolve(packId)
        packDir.mkdirs()

        val target = packDir.resolve(safeName)
        val canonRoot = customRoot.canonicalFile
        check(target.canonicalFile.path.startsWith(canonRoot.path + File.separator)) {
            "Недопустимое имя файла: $safeName"
        }

        try {
            val fallbackUrls = if (cleanUrl.startsWith("https://huggingface.co/", ignoreCase = true)) {
                listOf(URL(cleanUrl.replace("https://huggingface.co/", "https://hf-mirror.com/")))
            } else {
                emptyList()
            }
            ResumableModelDownloader.downloadVerified(
                url = URL(cleanUrl),
                destination = target,
                expectedBytes = expectedSizeBytes.takeIf { it > 0L },
                expectedSha256 = null,
                fallbackUrls = fallbackUrls,
                onProgress = onProgress,
            )

            check(target.isFile && target.length() > 1024L) { "Скачанный файл слишком мал или пуст" }
            val header = ByteArray(4)
            val headerRead = target.inputStream().use { it.read(header) }
            check(headerRead >= 4 && isValidGgufMagic(header)) {
                "Скачанный файл не является моделью формата GGUF"
            }

            val shaHex = ResumableModelDownloader.sha256(target)
            val actualSize = target.length()
            val record = StoredCustomPack(
                id = packId,
                title = safeName.removeSuffix(".gguf"),
                fileName = safeName,
                sizeBytes = actualSize,
                sha256 = shaHex,
            )
            val marker = File(packDir, "$safeName.verified")
            marker.writeText("${record.sizeBytes}:${record.sha256}", Charsets.UTF_8)

            val updated = loadStoredCustomPacks() + record
            saveStoredCustomPacks(updated)
            selectPack(record.id)
            onProgress(100, actualSize, actualSize)

            findPack(record.id) ?: error("Скачанная модель не найдена после сохранения")
        } catch (err: Throwable) {
            packDir.deleteRecursively()
            throw err
        }
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
        if (pack.isCustom) {
            saveStoredCustomPacks(loadStoredCustomPacks().filterNot { it.id == packId })
        }
        if (selectedPackId() == packId) {
            prefs.edit().remove(KEY_SELECTED_PACK).apply()
        }
    }

    private fun writeVerifiedMarker(pack: LocalLlmPack, marker: File) {
        marker.parentFile?.mkdirs()
        marker.writeText("${pack.sizeBytes}:${pack.sha256}", Charsets.UTF_8)
    }

    private fun loadStoredCustomPacks(): List<StoredCustomPack> {
        val raw = prefs.getString(KEY_CUSTOM_PACKS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<StoredCustomPack>>() {}.type
            gson.fromJson<List<StoredCustomPack>>(raw, type).orEmpty()
        }.getOrElse { emptyList() }
    }

    private fun saveStoredCustomPacks(items: List<StoredCustomPack>) {
        prefs.edit().putString(KEY_CUSTOM_PACKS, gson.toJson(items)).apply()
    }

    companion object {
        const val FAMILY_OPEN_COMMUNITY = "Dolphin 3.0 / Hermes 3 / Stheno 8B"
        const val FAMILY_QWEN = "Qwen 2.5 / Qwen3 Instruct"
        const val FAMILY_CUSTOM = "Свои .gguf модели"

        const val PACK_DOLPHIN_QWEN_05B = "dolphin3.0-qwen2.5-0.5b-q4_k_m"
        const val PACK_DOLPHIN_QWEN_15B = "dolphin3.0-qwen2.5-1.5b-q4_k_m"
        const val PACK_DOLPHIN_QWEN_3B = "dolphin3.0-qwen2.5-3b-q4_k_m"
        const val PACK_HERMES3_LLAMA32_3B = "hermes-3-llama-3.2-3b-q4_k_m"
        const val PACK_STHENO_8B_Q3KM = "l3-8b-stheno-v3.2-q3_k_m"
        const val PACK_STHENO_8B_Q4KM = "l3-8b-stheno-v3.2-q4_k_m"

        const val PACK_QWEN_05B = "qwen2.5-0.5b-q4_k_m"
        const val PACK_QWEN_15B = "qwen2.5-1.5b-q4_k_m"
        const val PACK_QWEN_3B = "qwen2.5-3b-q4_k_m"
        const val PACK_QWEN3_06B = "qwen3-0.6b-q4_0"
        const val PACK_QWEN3_4B = "qwen3-4b-q4_k_m"

        private const val KEY_SELECTED_PACK = "selected_llm_pack_id"
        private const val KEY_SELECTED_PERSONA = "selected_jarvis_persona"
        private const val KEY_PROMPT_TEMPLATE = "selected_prompt_template"
        private const val KEY_CUSTOM_PACKS = "custom_gguf_packs_json"
        private const val KEY_GEN_TEMP = "gen_temperature"
        private const val KEY_GEN_TOP_P = "gen_top_p"
        private const val KEY_GEN_MAX_TOKENS = "gen_max_tokens"
        private const val KEY_GEN_CTX = "gen_context_length"
        private const val KEY_GEN_THREADS = "gen_cpu_threads"
        private const val KEY_GEN_CUSTOM_PROMPT = "gen_custom_system_prompt"

        data class ExtractedZipGguf(
            val entryName: String,
            val copiedBytes: Long,
        )

        fun clampGenerationSettings(raw: LlmGenerationSettings): LlmGenerationSettings =
            LlmGenerationSettings(
                temperature = raw.temperature.coerceIn(0.10f, 1.40f),
                topP = raw.topP.coerceIn(0.50f, 1.00f),
                maxTokens = raw.maxTokens.coerceIn(128, 1024),
                contextLength = raw.contextLength.coerceIn(512, 4096),
                cpuThreads = raw.cpuThreads.coerceIn(2, 6),
                customSystemPrompt = raw.customSystemPrompt.trim().take(2000),
            )

        fun isValidGgufMagic(header: ByteArray): Boolean =
            header.size >= 4 &&
                header[0] == 0x47.toByte() &&
                header[1] == 0x47.toByte() &&
                header[2] == 0x55.toByte() &&
                header[3] == 0x46.toByte()

        fun isZipMagic(header: ByteArray): Boolean =
            header.size >= 4 &&
                header[0] == 0x50.toByte() &&
                header[1] == 0x4B.toByte() &&
                header[2] == 0x03.toByte() &&
                header[3] == 0x04.toByte()

        fun isGzipMagic(header: ByteArray): Boolean =
            header.size >= 2 &&
                header[0] == 0x1F.toByte() &&
                header[1] == 0x8B.toByte()

        fun readHeaderAtLeast(stream: InputStream, dest: ByteArray, minBytes: Int = dest.size): Int {
            var total = 0
            val targetLen = minOf(minBytes, dest.size)
            while (total < targetLen) {
                val read = stream.read(dest, total, targetLen - total)
                if (read <= 0) break
                total += read
            }
            return total
        }

        fun formatHeaderDiagnostic(
            displayName: String,
            sizeBytes: Long,
            header: ByteArray,
        ): String {
            val count = header.size.coerceAtMost(8)
            val hex = if (count == 0) "<пусто>" else header.take(count).joinToString(" ") { "%02X".format(it) }
            val ascii = if (count == 0) "" else header.take(count).joinToString("") { b ->
                val v = b.toInt() and 0xFF
                if (v in 32..126) v.toChar().toString() else "."
            }
            val sizeLabel = when {
                sizeBytes >= 1024L * 1024L -> "%.1f МБ".format(Locale.US, sizeBytes / (1024f * 1024f))
                sizeBytes >= 1024L -> "${sizeBytes / 1024L} КБ"
                sizeBytes >= 0L -> "$sizeBytes Б"
                else -> "размер неизвестен"
            }
            val hint = when {
                count == 0 -> "Файл пустой или проводник не отдал данные (дождитесь полного копирования в Download)."
                ascii.startsWith("<") || ascii.startsWith("{") ->
                    "Вместо бинарной модели выбрана HTML/JSON-страница. Проверьте реальный размер (должен быть сотни МБ)."
                else -> "Ожидался заголовок GGUF (47 47 55 46). Возможно, файл повреждён при переносе — перекопируйте его заново."
            }
            return "Выбранный файл ($displayName, $sizeLabel) не является моделью GGUF: байты [$hex] ('$ascii'). $hint"
        }

        fun copyZipGgufEntry(
            rawStream: InputStream,
            destTmpFile: File,
            digest: MessageDigest,
            checkCancelled: () -> Boolean = { true },
            onBytesCopied: (Long) -> Unit = {},
        ): ExtractedZipGguf {
            ZipInputStream(rawStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val headerBuf = ByteArray(8)
                        val readHead = readHeaderAtLeast(zip, headerBuf, headerBuf.size)
                        val validMagic = readHead >= 4 && isValidGgufMagic(headerBuf.copyOf(readHead))
                        val looksGguf = entry.name.endsWith(".gguf", ignoreCase = true)
                        if (validMagic && (looksGguf || entry.size <= 0L || entry.size > 1024L)) {
                            var copiedBytes = 0L
                            val buf = ByteArray(128 * 1024)
                            destTmpFile.outputStream().use { out ->
                                out.write(headerBuf, 0, readHead)
                                digest.update(headerBuf, 0, readHead)
                                copiedBytes += readHead
                                while (true) {
                                    check(checkCancelled()) { "Импорт отменён" }
                                    val n = zip.read(buf)
                                    if (n <= 0) break
                                    out.write(buf, 0, n)
                                    digest.update(buf, 0, n)
                                    copiedBytes += n
                                    onBytesCopied(copiedBytes)
                                }
                                out.flush()
                            }
                            return ExtractedZipGguf(entry.name, copiedBytes)
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            error("ZIP-архив не содержит модель формата GGUF внутри")
        }

        fun sanitizeCustomFileName(rawName: String): String {
            val leaf = rawName.substringAfterLast('/').substringAfterLast('\\').trim()
            val cleaned = leaf.replace(Regex("[^A-Za-z0-9._-]"), "_")
                .trim('.', '_')
                .take(90)
            if (cleaned.isEmpty()) return "custom_model.gguf"
            return if (cleaned.endsWith(".gguf", ignoreCase = true)) {
                cleaned
            } else {
                "$cleaned.gguf"
            }
        }

        val CATALOG: List<LocalLlmPack> = listOf(
            LocalLlmPack(
                id = PACK_DOLPHIN_QWEN_05B,
                title = "Dolphin 3.0 Qwen2.5 0.5B (Быстрая)",
                description = "Открытая модель Dolphin 3.0 на базе Qwen 2.5 для быстрых ответов (~379 МБ).",
                downloadUrl = "https://huggingface.co/bartowski/Dolphin3.0-Qwen2.5-0.5b-GGUF/resolve/2a4428efa5a7377c4404d0b9ff16ce41e52047b8/Dolphin3.0-Qwen2.5-0.5B-Q4_K_M.gguf",
                family = FAMILY_OPEN_COMMUNITY,
                descriptor = ModelPackDescriptor(
                    packId = PACK_DOLPHIN_QWEN_05B,
                    version = "3.0-Qwen2.5-0.5B-Q4_K_M",
                    runtime = "llama.cpp",
                    architecture = "qwen2",
                    quantization = "Q4_K_M",
                    licenseId = "Apache-2.0",
                    sourceRevision = "2a4428efa5a7377c4404d0b9ff16ce41e52047b8",
                    shards = listOf(
                        ModelShard(
                            index = 0,
                            fileName = "Dolphin3.0-Qwen2.5-0.5B-Q4_K_M.gguf",
                            sizeBytes = 397_811_008L,
                            sha256 = "6a53689e2cb91027fdc9e366142eba8e35f56c14ee353e0a4d64de981efbfffa",
                        )
                    ),
                ),
            ),
            LocalLlmPack(
                id = PACK_DOLPHIN_QWEN_15B,
                title = "Dolphin 3.0 Qwen2.5 1.5B (Баланс RU)",
                description = "Сбалансированная модель Dolphin 3.0 с отличным русским (~940 МБ).",
                downloadUrl = "https://huggingface.co/bartowski/Dolphin3.0-Qwen2.5-1.5b-GGUF/resolve/0ff632a6d9820453a9d38dbb3fb9b476d6a3f5a9/Dolphin3.0-Qwen2.5-1.5B-Q4_K_M.gguf",
                family = FAMILY_OPEN_COMMUNITY,
                descriptor = ModelPackDescriptor(
                    packId = PACK_DOLPHIN_QWEN_15B,
                    version = "3.0-Qwen2.5-1.5B-Q4_K_M",
                    runtime = "llama.cpp",
                    architecture = "qwen2",
                    quantization = "Q4_K_M",
                    licenseId = "Apache-2.0",
                    sourceRevision = "0ff632a6d9820453a9d38dbb3fb9b476d6a3f5a9",
                    shards = listOf(
                        ModelShard(
                            index = 0,
                            fileName = "Dolphin3.0-Qwen2.5-1.5B-Q4_K_M.gguf",
                            sizeBytes = 986_051_648L,
                            sha256 = "7caa630a60c8831a509e2663e1761355fa24bcf6ccc03e3cc767e5b5747a3be5",
                        )
                    ),
                ),
            ),
            LocalLlmPack(
                id = PACK_DOLPHIN_QWEN_3B,
                title = "Dolphin 3.0 Qwen2.5 3B (Флагман Dolphin)",
                description = "Умная русскоязычная Dolphin 3.0 для OPPO 12/16 ГБ RAM (~1.80 ГБ).",
                downloadUrl = "https://huggingface.co/bartowski/Dolphin3.0-Qwen2.5-3b-GGUF/resolve/4f84a9b5b7ecc4c49367838cf226677008f37c1e/Dolphin3.0-Qwen2.5-3b-Q4_K_M.gguf",
                family = FAMILY_OPEN_COMMUNITY,
                descriptor = ModelPackDescriptor(
                    packId = PACK_DOLPHIN_QWEN_3B,
                    version = "3.0-Qwen2.5-3B-Q4_K_M",
                    runtime = "llama.cpp",
                    architecture = "qwen2",
                    quantization = "Q4_K_M",
                    licenseId = "Qwen-Research",
                    sourceRevision = "4f84a9b5b7ecc4c49367838cf226677008f37c1e",
                    shards = listOf(
                        ModelShard(
                            index = 0,
                            fileName = "Dolphin3.0-Qwen2.5-3b-Q4_K_M.gguf",
                            sizeBytes = 1_929_906_144L,
                            sha256 = "0cb1908c5f444e1dc2c5b5619d62ac4957a22ad39cd42f2d0b48e2d8b1c358ab",
                        )
                    ),
                ),
            ),
            LocalLlmPack(
                id = PACK_HERMES3_LLAMA32_3B,
                title = "NousResearch Hermes 3 Llama 3.2 3B",
                description = "Официальная GGUF-сборка NousResearch Hermes 3 (ChatML, ~1.88 ГБ).",
                downloadUrl = "https://huggingface.co/NousResearch/Hermes-3-Llama-3.2-3B-GGUF/resolve/3cd927095d8cbab12c743f932aa63b6f7bbfa141/Hermes-3-Llama-3.2-3B.Q4_K_M.gguf",
                family = FAMILY_OPEN_COMMUNITY,
                descriptor = ModelPackDescriptor(
                    packId = PACK_HERMES3_LLAMA32_3B,
                    version = "Hermes-3-Llama-3.2-3B-Q4_K_M",
                    runtime = "llama.cpp",
                    architecture = "llama",
                    quantization = "Q4_K_M",
                    licenseId = "Llama-3.2",
                    sourceRevision = "3cd927095d8cbab12c743f932aa63b6f7bbfa141",
                    shards = listOf(
                        ModelShard(
                            index = 0,
                            fileName = "Hermes-3-Llama-3.2-3B.Q4_K_M.gguf",
                            sizeBytes = 2_019_373_888L,
                            sha256 = "91776fe0f6cd7483d9d5e06162fdd1f8f0262c15ced269791b4d96a655e8a5a2",
                        )
                    ),
                ),
            ),
            LocalLlmPack(
                id = PACK_STHENO_8B_Q3KM,
                title = "L3-8B Stheno v3.2 (Q3_K_M — для 12 ГБ ОЗУ)",
                description = "Сильная 8B Llama 3 модель для диалогов (рекомендуется для 12 ГБ ОЗУ, ~3.74 ГБ).",
                downloadUrl = "https://huggingface.co/bartowski/L3-8B-Stheno-v3.2-GGUF/resolve/dcf7446b0049ee524188ea0b15bd9a5e24cd889b/L3-8B-Stheno-v3.2-Q3_K_M.gguf",
                family = FAMILY_OPEN_COMMUNITY,
                descriptor = ModelPackDescriptor(
                    packId = PACK_STHENO_8B_Q3KM,
                    version = "L3-8B-Stheno-v3.2-Q3_K_M",
                    runtime = "llama.cpp",
                    architecture = "llama",
                    quantization = "Q3_K_M",
                    licenseId = "Llama-3",
                    sourceRevision = "dcf7446b0049ee524188ea0b15bd9a5e24cd889b",
                    shards = listOf(
                        ModelShard(
                            index = 0,
                            fileName = "L3-8B-Stheno-v3.2-Q3_K_M.gguf",
                            sizeBytes = 4_018_917_920L,
                            sha256 = "d989e1d6d3ab0c448149126a287fa49d1cb5c011d91bbdbf7b97462b2575e97c",
                        )
                    ),
                ),
            ),
            LocalLlmPack(
                id = PACK_STHENO_8B_Q4KM,
                title = "L3-8B Stheno v3.2 (Q4_K_M — для 16 ГБ ОЗУ)",
                description = "Полная 8B Llama 3 модель повышенной точности (лучше для 16 ГБ ОЗУ, ~4.58 ГБ).",
                downloadUrl = "https://huggingface.co/bartowski/L3-8B-Stheno-v3.2-GGUF/resolve/dcf7446b0049ee524188ea0b15bd9a5e24cd889b/L3-8B-Stheno-v3.2-Q4_K_M.gguf",
                family = FAMILY_OPEN_COMMUNITY,
                descriptor = ModelPackDescriptor(
                    packId = PACK_STHENO_8B_Q4KM,
                    version = "L3-8B-Stheno-v3.2-Q4_K_M",
                    runtime = "llama.cpp",
                    architecture = "llama",
                    quantization = "Q4_K_M",
                    licenseId = "Llama-3",
                    sourceRevision = "dcf7446b0049ee524188ea0b15bd9a5e24cd889b",
                    shards = listOf(
                        ModelShard(
                            index = 0,
                            fileName = "L3-8B-Stheno-v3.2-Q4_K_M.gguf",
                            sizeBytes = 4_920_734_240L,
                            sha256 = "8e98c1953f9c04e060fd9640bbe866685c844363a2360f09099b79c6c9195fc4",
                        )
                    ),
                ),
            ),
            LocalLlmPack(
                id = PACK_QWEN_05B,
                title = "Qwen 2.5 0.5B Instruct (Быстрая)",
                description = "Компактный офлайн-мозг для быстрого общения на русском (~469 МБ).",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/9217f5db79a29953eb74d5343926648285ec7e67/qwen2.5-0.5b-instruct-q4_k_m.gguf",
                family = FAMILY_QWEN,
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
                title = "Qwen 2.5 1.5B Instruct (Баланс для OPPO)",
                description = "Сбалансированная русская модель для 12/16 ГБ RAM (~1.04 ГБ).",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/91cad51170dc346986eccefdc2dd33a9da36ead9/qwen2.5-1.5b-instruct-q4_k_m.gguf",
                family = FAMILY_QWEN,
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
            LocalLlmPack(
                id = PACK_QWEN_3B,
                title = "Qwen 2.5 3B Instruct (Флагман для OPPO)",
                description = "Максимально умная русская офлайн-модель для Snapdragon 8 Gen 2 (~1.96 ГБ).",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/7dabda4d13d513e3e842b20f0d435c732f172cbe/qwen2.5-3b-instruct-q4_k_m.gguf",
                family = FAMILY_QWEN,
                descriptor = ModelPackDescriptor(
                    packId = PACK_QWEN_3B,
                    version = "2.5-3B-Q4_K_M",
                    runtime = "llama.cpp",
                    architecture = "qwen2",
                    quantization = "Q4_K_M",
                    licenseId = "Apache-2.0",
                    sourceRevision = "7dabda4d13d513e3e842b20f0d435c732f172cbe",
                    shards = listOf(
                        ModelShard(
                            index = 0,
                            fileName = "qwen2.5-3b-instruct-q4_k_m.gguf",
                            sizeBytes = 2_104_932_768L,
                            sha256 = "626b4a6678b86442240e33df819e00132d3ba7dddfe1cdc4fbb18e0a9615c62d",
                        )
                    ),
                ),
            ),
            LocalLlmPack(
                id = PACK_QWEN3_06B,
                title = "Qwen3 0.6B (Ультра-быстрая, Q4_0)",
                description = "Сверхлёгкая модель Qwen3 (~409 МБ, Apache-2.0) для моментального отклика.",
                downloadUrl = "https://huggingface.co/ggml-org/Qwen3-0.6B-GGUF/resolve/afe8ad3577aa22500b3cd1c880f153b3b0e1cae8/Qwen3-0.6B-Q4_0.gguf",
                family = FAMILY_QWEN,
                descriptor = ModelPackDescriptor(
                    packId = PACK_QWEN3_06B,
                    version = "3-0.6B-Q4_0",
                    runtime = "llama.cpp",
                    architecture = "qwen3",
                    quantization = "Q4_0",
                    licenseId = "Apache-2.0",
                    sourceRevision = "afe8ad3577aa22500b3cd1c880f153b3b0e1cae8",
                    shards = listOf(
                        ModelShard(
                            index = 0,
                            fileName = "Qwen3-0.6B-Q4_0.gguf",
                            sizeBytes = 428_970_080L,
                            sha256 = "da2572f16c06133561ce56accaa822216f2391ef4d37fba427801cd6736417d4",
                        )
                    ),
                ),
            ),
            LocalLlmPack(
                id = PACK_QWEN3_4B,
                title = "Qwen3 4B (Новое поколение, Q4_K_M)",
                description = "Сильное рассуждение и русский язык нового поколения Qwen3 для 12/16 ГБ ОЗУ (~2.33 ГБ, Apache-2.0).",
                downloadUrl = "https://huggingface.co/Qwen/Qwen3-4B-GGUF/resolve/bc640142c66e1fdd12af0bd68f40445458f3869b/Qwen3-4B-Q4_K_M.gguf",
                family = FAMILY_QWEN,
                descriptor = ModelPackDescriptor(
                    packId = PACK_QWEN3_4B,
                    version = "3-4B-Q4_K_M",
                    runtime = "llama.cpp",
                    architecture = "qwen3",
                    quantization = "Q4_K_M",
                    licenseId = "Apache-2.0",
                    sourceRevision = "bc640142c66e1fdd12af0bd68f40445458f3869b",
                    shards = listOf(
                        ModelShard(
                            index = 0,
                            fileName = "Qwen3-4B-Q4_K_M.gguf",
                            sizeBytes = 2_497_280_256L,
                            sha256 = "7485fe6f11af29433bc51cab58009521f205840f5b4ae3a32fa7f92e8534fdf5",
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
