package com.jarvis.breeno.assistant.core

fun interface TokenCallback {
    fun onToken(piece: String)
}

data class ChatTurn(
    val role: String,
    val content: String,
)

enum class JarvisPersona(
    val id: String,
    val title: String,
    val systemPrompt: String,
) {
    STARK_JARVIS(
        id = "stark",
        title = "Джарвис (Старк)",
        systemPrompt = "Ты — Джарвис (J.A.R.V.I.S.), персональный интеллектуальный ИИ-помощник пользователя в стиле помощника Тони Старка. " +
            "Общайся естественно, живо, уважительно и умно на грамотном русском языке. " +
            "Давай конкретные, полезные и содержательные ответы без канцелярита, воды и технических тегов.",
    ),
    DIRECT_ASSISTANT(
        id = "direct",
        title = "Прямой (Dolphin / Hermes)",
        systemPrompt = "Ты — прямой, эрудированный и открытый локальный ИИ-ассистент пользователя. " +
            "Отвечай на грамотном русском языке чётко, подробно и по существу, без нравоучений, воды и лишних оговорок.",
    );

    companion object {
        fun fromId(id: String?): JarvisPersona = entries.firstOrNull { it.id == id } ?: STARK_JARVIS
    }
}

/**
 * Pure-Kotlin ChatML prompt formatter and output sanitizer for local Dolphin / Hermes / Qwen models.
 */
object JarvisChatPromptBuilder {
    private val STOP_MARKERS = listOf(
        "<|im_end|>",
        "<|endoftext|>",
        "<|eot_id|>",
        "</s>",
        "<|start_header_id|>",
        "<|im_start|>assistant",
        "<|im_start|>",
    )

    fun buildQwenChatMlPrompt(
        userText: String,
        recentTurns: List<ChatTurn> = emptyList(),
        recalledFacts: List<String> = emptyList(),
        systemInfoNote: String = "",
        persona: JarvisPersona = JarvisPersona.STARK_JARVIS,
    ): String {
        val systemBuilder = StringBuilder(persona.systemPrompt)
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
