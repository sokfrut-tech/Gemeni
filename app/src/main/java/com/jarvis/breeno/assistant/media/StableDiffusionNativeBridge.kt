package com.jarvis.breeno.assistant.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

fun interface SdProgressCallback {
    fun onProgress(step: Int, totalSteps: Int)
}

enum class LocalStudioFileSlot(val folder: String, val labelRu: String) {
    MODEL("model", "Модель (.gguf / .safetensors)"),
    VAE("vae", "VAE (.safetensors / .pt)"),
    CLIP1("clip1", "CLIP 1 (clip_l)"),
    CLIP2("clip2", "CLIP 2 (t5xxl / clip_g)"),
}

data class ResolvedOnDeviceSdSpec(
    val modelPath: String,
    val isStandaloneDiffusion: Boolean,
    val vaePath: String,
    val clipLPath: String,
    val clipGPath: String,
    val t5xxlPath: String,
    val recommendedSteps: Int,
    val recommendedCfg: Float,
    val recommendedClipSkip: Int,
    val recommendedNegative: String,
    val summaryRu: String,
)

/**
 * Narrow JNI bridge to pinned leejet/stable-diffusion.cpp (libjarvis_sd.so).
 * Runs local .gguf / .safetensors + VAE + CLIP/T5 completely offline on phone CPU with zero censorship.
 */
object StableDiffusionNativeBridge {
    val isNativeLibraryLoaded: Boolean
    val loadError: String?

    init {
        val loaded = runCatching {
            System.loadLibrary("jarvis_sd")
        }
        isNativeLibraryLoaded = loaded.isSuccess
        loadError = loaded.exceptionOrNull()?.message
    }

    @JvmStatic
    external fun nativeGenerateImageArgb(
        modelPath: String,
        isStandaloneDiffusion: Boolean,
        vaePath: String,
        clipLPath: String,
        clipGPath: String,
        t5xxlPath: String,
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        cfgScale: Float,
        clipSkip: Int,
        seed: Long,
        nThreads: Int,
        progressCallback: SdProgressCallback?,
    ): IntArray?

    @JvmStatic
    external fun nativeGetLastError(): String

    @JvmStatic
    external fun nativeCancel()

    /**
     * Automatically classifies local diffusion model, VAE, and CLIP/T5 files by filename
     * and wires them into the exact stable-diffusion.cpp context slots.
     */
    fun resolveLocalModelSpec(
        modelPath: String,
        vaePath: String = "",
        clip1Path: String = "",
        clip2Path: String = "",
    ): ResolvedOnDeviceSdSpec {
        val cleanModel = modelPath.trim()
        val modelName = File(cleanModel).name.lowercase()
        val isGguf = modelName.endsWith(".gguf")
        val isFlux = modelName.contains("flux")
        val isSd3 = modelName.contains("sd3")
        val isPony = modelName.contains("pony") || modelName.contains("cyberrealistic")
        val isSdxl = modelName.contains("xl") || modelName.contains("juggernaut") || modelName.contains("illustrious")
        val isStandalone = isGguf || isFlux || isSd3 || modelName.contains("unet") || modelName.contains("wan")

        var clipL = ""
        var clipG = ""
        var t5xxl = ""
        listOf(clip1Path.trim(), clip2Path.trim())
            .filter { it.isNotEmpty() }
            .forEach { path ->
                val lower = File(path).name.lowercase()
                when {
                    lower.contains("t5") -> t5xxl = path
                    lower.contains("clip_g") || lower.contains("clip-g") || lower.contains("clipg") -> clipG = path
                    lower.contains("clip_l") || lower.contains("clip-l") || lower.contains("clipl") -> clipL = path
                    clipL.isEmpty() -> clipL = path
                    isFlux && t5xxl.isEmpty() -> t5xxl = path
                    clipG.isEmpty() -> clipG = path
                }
            }

        val steps = when {
            isFlux && modelName.contains("schnell") -> 4
            isFlux -> 16
            isPony -> 24
            isSdxl -> 22
            else -> 20
        }
        val cfg = when {
            isFlux -> 1.0f
            isSd3 -> 4.5f
            isPony -> 5.5f
            isSdxl -> 6.5f
            else -> 7.0f
        }
        val clipSkip = if (isPony) 2 else -1
        val negative = when {
            isFlux -> ""
            isPony -> JarvisMediaGenerator.DEFAULT_PONY_NEGATIVE
            else -> JarvisMediaGenerator.DEFAULT_LOCAL_DREAM_NEGATIVE
        }
        val archLabel = when {
            isFlux -> if (isGguf) "FLUX GGUF" else "FLUX"
            isSd3 -> if (isGguf) "SD3 GGUF" else "SD3"
            isPony -> "Pony XL"
            isSdxl -> if (isGguf) "SDXL GGUF" else "SDXL"
            else -> if (isGguf) "GGUF Diffusion" else "SD 1.5"
        }
        val extras = buildList {
            if (vaePath.isNotBlank()) add("VAE: ${File(vaePath).name}")
            if (clipL.isNotBlank()) add("CLIP-L: ${File(clipL).name}")
            if (clipG.isNotBlank()) add("CLIP-G: ${File(clipG).name}")
            if (t5xxl.isNotBlank()) add("T5: ${File(t5xxl).name}")
        }.joinToString(" • ")

        return ResolvedOnDeviceSdSpec(
            modelPath = cleanModel,
            isStandaloneDiffusion = isStandalone,
            vaePath = vaePath.trim(),
            clipLPath = clipL,
            clipGPath = clipG,
            t5xxlPath = t5xxl,
            recommendedSteps = steps,
            recommendedCfg = cfg,
            recommendedClipSkip = clipSkip,
            recommendedNegative = negative,
            summaryRu = "Автосвязка $archLabel (Steps=$steps, CFG=$cfg)" +
                if (extras.isNotBlank()) " — $extras" else "",
        )
    }

    fun generateJpegBytes(
        spec: ResolvedOnDeviceSdSpec,
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        cfgScale: Float,
        clipSkip: Int,
        seed: Long,
        nThreads: Int = 4,
        onStepProgress: (Int, Int) -> Unit = { _, _ -> },
    ): ByteArray {
        check(isNativeLibraryLoaded) {
            "Нативный движок stable-diffusion.cpp недоступен (${loadError ?: "ABI не поддерживается"})"
        }
        val packed = nativeGenerateImageArgb(
            modelPath = spec.modelPath,
            isStandaloneDiffusion = spec.isStandaloneDiffusion,
            vaePath = spec.vaePath,
            clipLPath = spec.clipLPath,
            clipGPath = spec.clipGPath,
            t5xxlPath = spec.t5xxlPath,
            prompt = prompt,
            negativePrompt = negativePrompt,
            width = width,
            height = height,
            steps = steps,
            cfgScale = cfgScale,
            clipSkip = clipSkip,
            seed = seed,
            nThreads = nThreads.coerceIn(1, 4),
            progressCallback = SdProgressCallback { step, total -> onStepProgress(step, total) },
        )
        if (packed == null || packed.size < 3) {
            val nativeErr = runCatching { nativeGetLastError() }.getOrDefault("")
            error(nativeErr.ifBlank { "stable-diffusion.cpp не вернул изображение" })
        }
        val outW = packed[0]
        val outH = packed[1]
        check(outW > 0 && outH > 0 && packed.size >= outW * outH + 2) {
            "Некорректный размер изображения от stable-diffusion.cpp (${outW}x$outH)"
        }
        val bmp = Bitmap.createBitmap(packed, 2, outW, outW, outH, Bitmap.Config.ARGB_8888)
        return try {
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 94, out)
            out.toByteArray()
        } finally {
            bmp.recycle()
        }
    }

    /**
     * Copies a user-selected local diffusion file (.gguf / .safetensors / .ckpt / .pt) from Android SAF
     * into app-private storage so native stable-diffusion.cpp can read it directly via POSIX path.
     */
    suspend fun importLocalStudioFile(
        context: Context,
        uri: Uri,
        displayName: String,
        slot: LocalStudioFileSlot,
        onProgress: (Int) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val safeName = displayName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("""[^a-zA-Z0-9._-]"""), "_")
            .ifBlank { "${slot.folder}_file.safetensors" }

        val slotDir = File(context.filesDir, "studio_models/${slot.folder}").apply { mkdirs() }
        // Keep one active imported file per slot to avoid filling phone storage with duplicates.
        slotDir.listFiles()?.forEach { old ->
            if (old.isFile && old.name != safeName) runCatching { old.delete() }
        }
        val tmpFile = File(slotDir, "$safeName.part")
        val finalFile = File(slotDir, safeName)

        val resolver = context.contentResolver
        val pfd: ParcelFileDescriptor? = runCatching { resolver.openFileDescriptor(uri, "r") }.getOrNull()
        val totalBytes = pfd?.statSize?.takeIf { it > 0L } ?: -1L
        val callerCtx = currentCoroutineContext()

        try {
            val inputStream = if (pfd != null) {
                FileInputStream(pfd.fileDescriptor)
            } else {
                resolver.openInputStream(uri) ?: error("Не удалось открыть выбранный файл: $displayName")
            }
            inputStream.use { input ->
                FileOutputStream(tmpFile).use { output ->
                    val buf = ByteArray(256 * 1024)
                    var copied = 0L
                    var lastPct = -1
                    while (true) {
                        callerCtx.ensureActive()
                        val read = input.read(buf)
                        if (read <= 0) break
                        output.write(buf, 0, read)
                        copied += read
                        if (totalBytes > 0L) {
                            val pct = ((copied * 100L) / totalBytes).toInt().coerceIn(0, 99)
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct)
                            }
                        }
                    }
                    output.fd.sync()
                }
            }
            check(tmpFile.length() > 1024L) { "Выбранный файл пустой или повреждён" }
            if (finalFile.exists()) finalFile.delete()
            check(tmpFile.renameTo(finalFile)) { "Не удалось сохранить файл $safeName" }
            onProgress(100)
            finalFile
        } catch (err: Throwable) {
            tmpFile.delete()
            throw err
        } finally {
            runCatching { pfd?.close() }
        }
    }
}
