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

enum class PromptTemplateFormat(val id: String, val title: String) {
    AUTO("auto", "Авто"),
    CHATML("chatml", "ChatML (Dolphin / Qwen / Hermes)"),
    LLAMA3("llama3", "Llama 3 (Stheno / Llama 3.x)");

    companion object {
        fun fromId(id: String?): PromptTemplateFormat = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

/**
 * Pure-Kotlin prompt formatter (ChatML + Llama 3) and output sanitizer for local GGUF models.
 */
object JarvisChatPromptBuilder {
    private val STOP_MARKERS = listOf(
        "<|im_end|>",
        "<|endoftext|>",
        "<|eot_id|>",
        "<|end_of_text|>",
        "</s>",
        "<|end_header_id|>",
        "<|start_header_id|>",
        "<|im_start|>assistant",
        "<|im_start|>",
    )

    fun resolveTemplate(
        selected: PromptTemplateFormat,
        packId: String,
        fileName: String,
        architecture: String,
    ): PromptTemplateFormat {
        if (selected != PromptTemplateFormat.AUTO) return selected
        val hint = "${packId.lowercase()} ${fileName.lowercase()} ${architecture.lowercase()}"
        if (hint.contains("hermes") || hint.contains("dolphin") || hint.contains("qwen")) {
            return PromptTemplateFormat.CHATML
        }
        val looksLlama3 = hint.contains("stheno") ||
            hint.contains("llama-3") ||
            hint.contains("llama3") ||
            hint.contains("l3-") ||
            architecture.equals("llama", ignoreCase = true)
        return if (looksLlama3) PromptTemplateFormat.LLAMA3 else PromptTemplateFormat.CHATML
    }

    fun buildPrompt(
        format: PromptTemplateFormat,
        userText: String,
        recentTurns: List<ChatTurn> = emptyList(),
        recalledFacts: List<String> = emptyList(),
        systemInfoNote: String = "",
        persona: JarvisPersona = JarvisPersona.STARK_JARVIS,
        documentSnippets: List<String> = emptyList(),
    ): String = when (format) {
        PromptTemplateFormat.LLAMA3 -> buildLlama3Prompt(
            userText = userText,
            recentTurns = recentTurns,
            recalledFacts = recalledFacts,
            systemInfoNote = systemInfoNote,
            persona = persona,
            documentSnippets = documentSnippets,
        )
        PromptTemplateFormat.AUTO, PromptTemplateFormat.CHATML ->
            buildQwenChatMlPrompt(
                userText = userText,
                recentTurns = recentTurns,
                recalledFacts = recalledFacts,
                systemInfoNote = systemInfoNote,
                persona = persona,
                documentSnippets = documentSnippets,
            )
    }

    private fun buildSystemText(
        recalledFacts: List<String>,
        systemInfoNote: String,
        persona: JarvisPersona,
        documentSnippets: List<String> = emptyList(),
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
        if (documentSnippets.isNotEmpty()) {
            systemBuilder.append("\n\nФрагменты из документов пользователя:")
            documentSnippets.forEach { snippet ->
                val cleanSnippet = snippet.trim()
                if (cleanSnippet.isNotEmpty()) {
                    systemBuilder.append("\n- ").append(cleanSnippet)
                }
            }
        }
        if (systemInfoNote.isNotBlank()) {
            systemBuilder.append("\n\nКонтекст устройства: ").append(systemInfoNote.trim())
        }
        return systemBuilder.toString().trim()
    }

    private fun normalizeRole(rawRole: String): String = when (rawRole.lowercase()) {
        "assistant", "model" -> "assistant"
        "system" -> "system"
        else -> "user"
    }

    fun buildQwenChatMlPrompt(
        userText: String,
        recentTurns: List<ChatTurn> = emptyList(),
        recalledFacts: List<String> = emptyList(),
        systemInfoNote: String = "",
        persona: JarvisPersona = JarvisPersona.STARK_JARVIS,
        documentSnippets: List<String> = emptyList(),
    ): String {
        val sb = StringBuilder()
        sb.append("<|im_start|>system\n")
        sb.append(buildSystemText(recalledFacts, systemInfoNote, persona, documentSnippets))
        sb.append("<|im_end|>\n")

        recentTurns.takeLast(6).forEach { turn ->
            val role = normalizeRole(turn.role)
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

    fun buildLlama3Prompt(
        userText: String,
        recentTurns: List<ChatTurn> = emptyList(),
        recalledFacts: List<String> = emptyList(),
        systemInfoNote: String = "",
        persona: JarvisPersona = JarvisPersona.STARK_JARVIS,
        documentSnippets: List<String> = emptyList(),
    ): String {
        val sb = StringBuilder()
        sb.append("<|begin_of_text|>")
        sb.append("<|start_header_id|>system<|end_header_id|>\n\n")
        sb.append(buildSystemText(recalledFacts, systemInfoNote, persona, documentSnippets))
        sb.append("<|eot_id|>")

        recentTurns.takeLast(6).forEach { turn ->
            val role = normalizeRole(turn.role)
            val clean = turn.content.trim()
            if (clean.isNotEmpty()) {
                sb.append("<|start_header_id|>").append(role).append("<|end_header_id|>\n\n")
                    .append(clean).append("<|eot_id|>")
            }
        }

        sb.append("<|start_header_id|>user<|end_header_id|>\n\n")
        sb.append(userText.trim())
        sb.append("<|eot_id|>")
        sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
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
