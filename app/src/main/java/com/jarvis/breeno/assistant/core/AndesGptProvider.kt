package com.jarvis.breeno.assistant.core

/**
 * Provider boundary for OPPO AndesGPT.
 *
 * AndesGPT access is intentionally not guessed: OPPO's public developer portal lists
 * an Andes model offering, but the usable SDK/API contract and authorization are
 * account-gated. A concrete implementation must be added only from an authorized
 * OPPO SDK/API package and its official documentation.
 */
data class AndesGptMessage(
    val role: Role,
    val content: String,
) {
    enum class Role { SYSTEM, USER, ASSISTANT }
}

data class AndesGptReply(
    val text: String,
    val model: String,
    val isOnDevice: Boolean,
)

sealed interface AndesGptStatus {
    data object Available : AndesGptStatus
    data object RequiresOppoAuthorization : AndesGptStatus
    data object UnsupportedDevice : AndesGptStatus
    data class Failed(val reason: String) : AndesGptStatus
}

interface AndesGptProvider {
    val id: String
    suspend fun status(): AndesGptStatus
    suspend fun generate(
        messages: List<AndesGptMessage>,
        maxOutputTokens: Int = 512,
    ): AndesGptReply
}

/**
 * Safe default until OPPO grants the app access to the official AndesGPT interface.
 * It never silently uploads conversation text to an undocumented endpoint.
 */
class UnavailableAndesGptProvider : AndesGptProvider {
    override val id: String = "oppo-andesgpt"

    override suspend fun status(): AndesGptStatus =
        AndesGptStatus.RequiresOppoAuthorization

    override suspend fun generate(
        messages: List<AndesGptMessage>,
        maxOutputTokens: Int,
    ): AndesGptReply {
        throw IllegalStateException(
            "Для AndesGPT требуется официальный доступ OPPO и документированный SDK/API."
        )
    }
}
