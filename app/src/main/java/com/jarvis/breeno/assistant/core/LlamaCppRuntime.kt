package com.jarvis.breeno.assistant.core

import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * App-owned adapter for the pinned llama.cpp JNI runtime.
 *
 * This class never downloads models or accesses the network. The caller must
 * obtain the model through an explicit user-approved installation flow and
 * pass the expected SHA-256 from a pinned model manifest.
 *
 * Call load/generate/close from a worker thread: native inference is CPU-bound.
 */
class LlamaCppRuntime : AutoCloseable, JarvisTextGenerationRuntime {
    fun interface TokenListener {
        /** Return false to stop generation after the current token. */
        fun onToken(text: String): Boolean
    }

    sealed interface LoadResult {
        data object Loaded : LoadResult
        data class Rejected(val reason: String) : LoadResult
    }

    @Volatile
    private var loaded = false

    override val isModelLoaded: Boolean
        get() = loaded

    @Synchronized
    fun loadVerifiedModel(modelFile: File, expectedSha256: String): LoadResult {
        if (!expectedSha256.matches(Regex("[0-9a-fA-F]{64}"))) {
            return LoadResult.Rejected("Некорректная контрольная сумма модели")
        }
        val canonical = runCatching { modelFile.canonicalFile }.getOrElse {
            return LoadResult.Rejected("Не удалось проверить путь к модели")
        }
        if (!canonical.isFile || !canonical.canRead() || canonical.length() <= 0L) {
            return LoadResult.Rejected("Файл модели отсутствует или недоступен")
        }
        val actual = runCatching { sha256(canonical) }.getOrElse {
            return LoadResult.Rejected("Не удалось проверить SHA-256 модели")
        }
        if (!actual.equals(expectedSha256, ignoreCase = true)) {
            return LoadResult.Rejected("SHA-256 модели не совпадает с доверенным манифестом")
        }

        ensureNativeLibrary()
        val handle = nativeLoadModel(canonical.absolutePath, DEFAULT_CONTEXT_TOKENS, DEFAULT_THREADS)
        loaded = handle != 0L
        return if (loaded) LoadResult.Loaded
        else LoadResult.Rejected("Нативный движок не смог загрузить GGUF-модель")
    }

    fun generate(
        prompt: String,
        maxTokens: Int = DEFAULT_MAX_TOKENS,
        listener: TokenListener? = null
    ): String {
        require(prompt.isNotBlank()) { "Пустой запрос" }
        require(prompt.length <= MAX_PROMPT_CHARS) { "Запрос слишком длинный" }
        require(maxTokens in 1..MAX_MAX_TOKENS) { "Недопустимый лимит токенов" }
        check(loaded) { "Локальная модель не загружена" }
        return nativeGenerate(prompt, maxTokens, listener)
    }

    override fun generateForPlanning(prompt: String, maxTokens: Int): String =
        generate(prompt, maxTokens, listener = null)

    /** Safe to call from another thread while generate() is running. */
    fun cancel() {
        if (loaded) nativeCancel()
    }

    @Synchronized
    override fun close() {
        if (loaded) {
            nativeCancel()
            nativeUnload()
            loaded = false
        }
    }

    fun runtimeVersion(): String {
        ensureNativeLibrary()
        return nativeVersion()
    }

    private fun ensureNativeLibrary() {
        if (!libraryLoaded) {
            synchronized(LIBRARY_LOCK) {
                if (!libraryLoaded) {
                    System.loadLibrary("jarvis_native")
                    libraryLoaded = true
                }
            }
        }
    }

    private external fun nativeLoadModel(path: String, contextTokens: Int, threads: Int): Long
    private external fun nativeGenerate(prompt: String, maxTokens: Int, listener: TokenListener?): String
    private external fun nativeCancel()
    private external fun nativeUnload()
    private external fun nativeVersion(): String

    companion object {
        const val QWEN_08B_Q4_0_SHA256 =
            "57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf"
        const val DEFAULT_CONTEXT_TOKENS = 4096
        const val DEFAULT_THREADS = 4
        const val DEFAULT_MAX_TOKENS = 256
        const val MAX_MAX_TOKENS = 512
        const val MAX_PROMPT_CHARS = 12000

        private val LIBRARY_LOCK = Any()
        @Volatile private var libraryLoaded = false

        internal fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
        }
    }
}
