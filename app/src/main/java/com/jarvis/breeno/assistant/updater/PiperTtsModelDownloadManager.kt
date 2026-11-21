package com.jarvis.breeno.assistant.updater

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URL

data class PiperVoicePack(
    val id: String,
    val title: String,
    val description: String,
    val downloadUrl: String,
    val archiveSizeBytes: Long,
    val archiveSha256: String,
)

/**
 * Downloads, verifies, and safely extracts official Russian Piper TTS voices
 * packaged for Sherpa-ONNX (solved silent Chinese-ROM ColorOS missing RU system TTS).
 */
class PiperTtsModelDownloadManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_tts_voices", Context.MODE_PRIVATE)

    fun catalog(): List<PiperVoicePack> = VOICES

    fun voiceDir(voiceId: String): File = context.filesDir.resolve("models/tts/$voiceId")

    fun selectedVoiceId(): String = prefs.getString(KEY_SELECTED_VOICE, VOICE_DMITRI_INT8) ?: VOICE_DMITRI_INT8

    fun selectVoice(voiceId: String) {
        if (findVoice(voiceId) != null) {
            prefs.edit().putString(KEY_SELECTED_VOICE, voiceId).apply()
        }
    }

    fun isVoiceReady(voiceId: String): Boolean {
        val dir = voiceDir(voiceId)
        val pack = findVoice(voiceId) ?: return false
        val marker = dir.resolve(".verified")
        val hasOnnx = dir.resolve("model.onnx").isFile ||
            (dir.listFiles()?.any { it.isFile && it.extension.equals("onnx", ignoreCase = true) } == true)
        val hasTokens = dir.resolve("tokens.txt").isFile
        val hasEspeak = dir.resolve("espeak-ng-data").isDirectory
        val markerOk = marker.isFile && runCatching {
            marker.readText(Charsets.UTF_8).trim() == pack.archiveSha256
        }.getOrDefault(false)
        return hasOnnx && hasTokens && hasEspeak && markerOk
    }

    fun isReady(): Boolean = activeVoiceDir() != null

    fun activeVoiceDir(): File? {
        val preferred = selectedVoiceId()
        if (isVoiceReady(preferred)) return voiceDir(preferred)
        return VOICES.firstOrNull { isVoiceReady(it.id) }?.let { voiceDir(it.id) }
    }

    fun findVoice(voiceId: String): PiperVoicePack? = VOICES.firstOrNull { it.id == voiceId }

    suspend fun ensureVoice(
        voiceId: String = selectedVoiceId(),
        onProgress: (Int) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val pack = findVoice(voiceId) ?: error("Неизвестный голос: $voiceId")
        val finalDir = voiceDir(pack.id)
        if (isVoiceReady(pack.id)) {
            onProgress(100)
            return@withContext finalDir
        }

        val archivesRoot = context.filesDir.resolve("models/tts/.downloads").also { it.mkdirs() }
        val archiveFile = archivesRoot.resolve("${pack.id}.tar.bz2")
        val stageDir = context.filesDir.resolve("models/tts/.stage_${pack.id}")
        stageDir.deleteRecursively()
        stageDir.mkdirs()

        try {
            ResumableModelDownloader.downloadVerified(
                url = URL(pack.downloadUrl),
                destination = archiveFile,
                expectedBytes = pack.archiveSizeBytes,
                expectedSha256 = pack.archiveSha256,
                onProgress = { pct, _, _ -> onProgress((pct * 0.85f).toInt().coerceIn(0, 85)) },
            )

            extractTarBz2Flattened(archiveFile, stageDir)
            onProgress(95)

            val hasOnnx = stageDir.listFiles()?.any { it.isFile && it.extension.equals("onnx", ignoreCase = true) } == true
            check(hasOnnx && stageDir.resolve("tokens.txt").isFile && stageDir.resolve("espeak-ng-data").isDirectory) {
                "Архив голоса ${pack.title} не содержит обязательных файлов (onnx / tokens.txt / espeak-ng-data)"
            }

            stageDir.resolve(".verified").writeText(pack.archiveSha256, Charsets.UTF_8)
            finalDir.deleteRecursively()
            finalDir.parentFile?.mkdirs()
            check(stageDir.renameTo(finalDir)) { "Не удалось переместить файлы голоса ${pack.title}" }
            selectVoice(pack.id)
            onProgress(100)
            finalDir
        } finally {
            archiveFile.delete()
            stageDir.deleteRecursively()
        }
    }

    fun deleteVoice(voiceId: String) {
        voiceDir(voiceId).deleteRecursively()
    }

    private fun extractTarBz2Flattened(archive: File, destDir: File) {
        val canonicalDest = destDir.canonicalPath + File.separator
        BufferedInputStream(FileInputStream(archive)).use { fileIn ->
            BZip2CompressorInputStream(fileIn).use { bzIn ->
                TarArchiveInputStream(bzIn).use { tarIn ->
                    while (true) {
                        val entry = tarIn.nextEntry ?: break
                        val rawName = entry.name.trimStart('.', '/').replace('\\', '/')
                        if (rawName.isBlank()) continue
                        // Strip top-level folder inside tar archive (e.g. "vits-piper-ru_RU-dmitri-medium-int8/")
                        val slashIndex = rawName.indexOf('/')
                        val relative = if (slashIndex >= 0) rawName.substring(slashIndex + 1) else rawName
                        if (relative.isBlank()) continue

                        val outFile = File(destDir, relative)
                        val canonicalOut = outFile.canonicalPath
                        if (!canonicalOut.startsWith(canonicalDest)) {
                            throw SecurityException("Небезопасный путь в архиве: ${entry.name}")
                        }

                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out ->
                                val buf = ByteArray(32 * 1024)
                                while (true) {
                                    val len = tarIn.read(buf)
                                    if (len <= 0) break
                                    out.write(buf, 0, len)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val VOICE_DMITRI_INT8 = "dmitri-int8"
        const val VOICE_RUSLAN_INT8 = "ruslan-int8"
        private const val KEY_SELECTED_VOICE = "selected_piper_voice_id"

        val VOICES: List<PiperVoicePack> = listOf(
            PiperVoicePack(
                id = VOICE_DMITRI_INT8,
                title = "Дмитрий (мужской, офлайн Piper int8)",
                description = "Чёткий мужской русский голос, работает полностью без интернета (~20 МБ).",
                downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ru_RU-dmitri-medium-int8.tar.bz2",
                archiveSizeBytes = 21_129_441L,
                archiveSha256 = "7636793307f634ce54c6e65528a91a61683114f1a6635a08caf64ba6c54e6a63",
            ),
            PiperVoicePack(
                id = VOICE_RUSLAN_INT8,
                title = "Руслан (глубокий мужской, Piper int8)",
                description = "Глубокий баритон для ассистента, офлайн (~20 МБ).",
                downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ru_RU-ruslan-medium-int8.tar.bz2",
                archiveSizeBytes = 21_127_907L,
                archiveSha256 = "93b9f8c7b34c1420bdc253a0ce5ab1cb6b6be2e8fa26c1f3b36c6189233c3e7e",
            ),
        )
    }
}
