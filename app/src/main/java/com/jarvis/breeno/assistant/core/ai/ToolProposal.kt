package com.jarvis.breeno.assistant.core.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class ToolProposal(val tool: String, val arguments: Map<String, String>)

enum class ProposalRisk { SAFE, CONFIRM, BLOCK }

data class ValidatedToolProposal(
    val proposal: ToolProposal,
    val risk: ProposalRisk
)

/**
 * Strict allowlist parser for untrusted model output. Only a closed set of
 * string-valued arguments is accepted; no intents, URLs, paths or code.
 */
object ToolProposalCodec {
    private val schemas = mapOf(
        "open_app" to setOf("name"),
        "web_search" to setOf("query"),
        "navigate" to setOf("destination"),
        "open_dialer" to setOf("number"),
        "tell_time" to emptySet()
    )

    fun encode(proposal: ToolProposal): String {
        val root = JsonObject()
        root.addProperty("tool", proposal.tool)
        val args = JsonObject()
        proposal.arguments.toSortedMap().forEach { (key, value) -> args.addProperty(key, value) }
        root.add("arguments", args)
        return root.toString()
    }

    fun validate(json: String): ValidatedToolProposal? {
        if (json.length > 2048) return null

        return try {
            val root = JsonParser.parseString(json)
            if (!root.isJsonObject) return null
            val obj = root.asJsonObject
            if (obj.keySet() != setOf("tool", "arguments")) return null

            val toolElement = obj.get("tool") ?: return null
            if (!toolElement.isJsonPrimitive || !toolElement.asJsonPrimitive.isString) return null
            val argumentsElement = obj.get("arguments") ?: return null
            if (!argumentsElement.isJsonObject) return null

            val tool = toolElement.asString
            val schema = schemas[tool] ?: return null
            val argsObject = argumentsElement.asJsonObject
            if (argsObject.keySet() != schema) return null

            val args = linkedMapOf<String, String>()
            for ((key, value) in argsObject.entrySet()) {
                if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) return null
                val argumentText = value.asString
                if (argumentText.length > 240 || argumentText.any { it == '\u0000' }) return null
                if (key == "number" && argumentText.any {
                        !(it.isDigit() || it == '+' || it == ' ' || it == '-' || it == '(' || it == ')')
                    }) return null
                args[key] = argumentText
            }

            if (tool == "open_app" && args.getValue("name").isBlank()) return null
            if (tool == "web_search" && args.getValue("query").isBlank()) return null
            if (tool == "navigate" && args.getValue("destination").isBlank()) return null
            if (tool == "open_dialer") {
                val number = args.getValue("number")
                val digits = number.count(Char::isDigit)
                if (digits !in 1..20) return null
                if (number.count { it == '+' } > 1 || ('+' in number && !number.startsWith('+'))) return null
            }

            val proposal = ToolProposal(tool, args)
            val risk = when (tool) {
                "open_dialer" -> ProposalRisk.CONFIRM
                "open_app", "web_search", "navigate", "tell_time" -> ProposalRisk.SAFE
                else -> ProposalRisk.BLOCK
            }
            ValidatedToolProposal(proposal, risk)
        } catch (_: Exception) {
            null
        }
    }
}
