package com.jarvis.breeno.assistant.core

fun interface TokenCallback {
    fun onToken(piece: String)
}

data class ChatTurn(
    val role: String,
    val content: String,
)

/**
 * Pure-Kotlin ChatML prompt formatter and output sanitizer for local Qwen2.5 models.
 */
object JarvisChatPromptBuilder {
    private val STOP_MARKERS = listOf("<|im_end|>", "<|endoftext|>", "<|im_start|>assistant", "<|im_start|>")

    fun buildQwenChatMlPrompt(
        userText: String,
        recentTurns: List<ChatTurn> = emptyList(),
        recalledFacts: List<String> = emptyList(),
        systemInfoNote: String = "",
    ): String {
        val systemBuilder = StringBuilder(
            "Ты Джарвис — умный, лаконичный и дружелюбный персональный ИИ-помощник на русском языке. " +
                "Отвечай чётко, естественно и по делу на русском языке, без выдуманных фактов и технических тегов."
        )
        if (recalledFacts.isNotEmpty()) {
            systemBuilder.append("\n\nФакты из памяти пользователя:")
            recalledFacts.forEach { fact ->
                val cleanFact = fact.trim()
                if (cleanFact.isNotEmpty()) {
                    systemBuilder.append("\n- ").append(cleanFact)
                }
            }
        }
        if (systemInfoNote.isNotBlank()) {
            systemBuilder.append("\n\nКонтекст устройства: ").append(systemInfoNote.trim())
        }

        val sb = StringBuilder()
        sb.append("<|im_start|>system\n")
        sb.append(systemBuilder.toString().trim())
        sb.append("<|im_end|>\n")

        recentTurns.takeLast(6).forEach { turn ->
            val role = when (turn.role.lowercase()) {
                "assistant", "model" -> "assistant"
                "system" -> "system"
                else -> "user"
            }
            val clean = turn.content.trim()
            if (clean.isNotEmpty()) {
                sb.append("<|im_start|>").append(role).append('\n')
                    .append(clean).append("<|im_end|>\n")
            }
        }

        sb.append("<|im_start|>user\n")
        sb.append(userText.trim())
        sb.append("<|im_end|>\n")
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    fun sanitizeOutput(raw: String): String {
        var result = raw
        for (marker in STOP_MARKERS) {
            result = result.replace(marker, "")
        }
        return result.trim()
    }
}

/**
 * Narrow JNI bridge to the pinned llama.cpp v1.0.0 C++ engine (libjarvis_llama.so).
 */
object LlamaNativeBridge {
    val isNativeLibraryLoaded: Boolean
    val loadError: String?

    init {
        val loaded = runCatching {
            System.loadLibrary("jarvis_llama")
        }
        isNativeLibraryLoaded = loaded.isSuccess
        loadError = loaded.exceptionOrNull()?.message
    }

    @JvmStatic
    external fun nativeBackendInit()

    @JvmStatic
    external fun nativeLoadModel(modelPath: String, nCtx: Int, nThreads: Int): Long

    @JvmStatic
    external fun nativeGenerate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        callback: TokenCallback?,
    ): String

    @JvmStatic
    external fun nativeCancel(handle: Long)

    @JvmStatic
    external fun nativeUnload(handle: Long)
}
