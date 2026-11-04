package com.jarvis.breeno.assistant.core.ai

import com.jarvis.breeno.assistant.core.JarvisCommand
import com.jarvis.breeno.assistant.core.JarvisCommandParser

/**
 * Provider-neutral reasoning boundary. Implementations receive plain text only:
 * never Android Context, PackageManager, filesystem handles or executable callbacks.
 */
data class JarvisModelRequest(
    val userText: String,
    val localeTag: String = "ru-RU"
)

sealed interface JarvisModelResult {
    data class Proposal(val json: String, val source: String) : JarvisModelResult
    data class NoAction(val reason: String) : JarvisModelResult
    data class Failure(val reason: String) : JarvisModelResult
}

interface JarvisModelProvider {
    val providerId: String
    val isLocalOnly: Boolean
    suspend fun propose(request: JarvisModelRequest): JarvisModelResult
}

/**
 * Deterministic offline provider. It uses the existing pure command parser and
 * never opens a socket, loads a remote model or reads credentials.
 */
class LocalOnlyJarvisModelProvider : JarvisModelProvider {
    override val providerId: String = "local-command-parser"
    override val isLocalOnly: Boolean = true

    override suspend fun propose(request: JarvisModelRequest): JarvisModelResult {
        val text = request.userText.trim()
        if (text.isEmpty()) return JarvisModelResult.NoAction("Пустая команда")
        val proposal = when (val command = JarvisCommandParser.parse(text)) {
            is JarvisCommand.OpenApp -> ToolProposal("open_app", mapOf("name" to command.name))
            is JarvisCommand.Search -> ToolProposal("web_search", mapOf("query" to command.query))
            is JarvisCommand.Navigate -> ToolProposal("navigate", mapOf("destination" to command.destination))
            is JarvisCommand.Dial -> ToolProposal("open_dialer", mapOf("number" to command.number))
            JarvisCommand.TellTime -> ToolProposal("tell_time", emptyMap())
            JarvisCommand.Empty -> return JarvisModelResult.NoAction("Пустая команда")
            is JarvisCommand.Unsupported -> return JarvisModelResult.NoAction("Команда пока не поддерживается")
        }
        return JarvisModelResult.Proposal(ToolProposalCodec.encode(proposal), providerId)
    }
}
