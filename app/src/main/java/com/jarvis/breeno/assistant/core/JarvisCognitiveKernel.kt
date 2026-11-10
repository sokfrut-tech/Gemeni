package com.jarvis.breeno.assistant.core

/**
 * Jarvis-owned reasoning boundary. Model providers propose answers and typed tools;
 * Android code remains the only authority that may execute a tool.
 */
enum class ReasoningTier { LOCAL_PRIMARY, LOCAL_COMPACT, AUTHORIZED_CLOUD }

data class KernelBudget(
    val maxOutputTokens: Int = 700,
    val maxToolProposals: Int = 3,
    val timeoutMillis: Long = 12_000,
) {
    init {
        require(maxOutputTokens in 32..4096)
        require(maxToolProposals in 0..8)
        require(timeoutMillis in 500..60_000)
    }
}

data class KernelTurn(
    val taskId: String,
    val userText: String,
    val locale: String = "ru-RU",
    val networkAllowed: Boolean = false,
    val allowedToolIds: Set<String> = emptySet(),
    val budget: KernelBudget = KernelBudget(),
)

data class ProposedToolCall(
    val toolId: String,
    val arguments: Map<String, String> = emptyMap(),
)

data class ProviderTurnResult(
    val answer: String,
    val proposedTools: List<ProposedToolCall> = emptyList(),
    val modelId: String,
)

sealed interface ProviderAvailability {
    data object Ready : ProviderAvailability
    data class Unavailable(val reason: String) : ProviderAvailability
}

interface JarvisReasoningProvider {
    val id: String
    val tier: ReasoningTier
    suspend fun availability(): ProviderAvailability
    suspend fun reason(turn: KernelTurn): ProviderTurnResult
}

data class KernelTurnResult(
    val answer: String,
    val acceptedToolProposals: List<ProposedToolCall>,
    val rejectedToolProposalCount: Int,
    val providerId: String,
    val modelId: String,
)

/**
 * Small, deterministic router. Local reasoning is preferred. Cloud reasoning is
 * considered only when the user explicitly allows network use for this turn.
 */
class JarvisCognitiveKernel(
    private val providers: List<JarvisReasoningProvider>,
) {
    suspend fun process(turn: KernelTurn): KernelTurnResult {
        require(turn.taskId.isNotBlank()) { "taskId must not be blank" }
        require(turn.userText.isNotBlank()) { "userText must not be blank" }
        require(turn.userText.length <= 16_000) { "userText exceeds the kernel limit" }

        val ordered = providers
            .filter { it.tier != ReasoningTier.AUTHORIZED_CLOUD || turn.networkAllowed }
            .sortedBy { when (it.tier) {
                ReasoningTier.LOCAL_PRIMARY -> 0
                ReasoningTier.LOCAL_COMPACT -> 1
                ReasoningTier.AUTHORIZED_CLOUD -> 2
            } }

        var lastUnavailable = "No reasoning provider is configured"
        for (provider in ordered) {
            when (val state = provider.availability()) {
                is ProviderAvailability.Unavailable -> lastUnavailable = "${provider.id}: ${state.reason}"
                ProviderAvailability.Ready -> {
                    val result = provider.reason(turn)
                    val accepted = result.proposedTools
                        .asSequence()
                        .filter { it.toolId in turn.allowedToolIds }
                        .distinctBy { it.toolId + it.arguments.toSortedMap().toString() }
                        .take(turn.budget.maxToolProposals)
                        .toList()
                    val rejected = result.proposedTools.size - accepted.size
                    return KernelTurnResult(
                        answer = result.answer.take(12_000),
                        acceptedToolProposals = accepted,
                        rejectedToolProposalCount = rejected,
                        providerId = provider.id,
                        modelId = result.modelId,
                    )
                }
            }
        }
        throw IllegalStateException("Jarvis reasoning unavailable: $lastUnavailable")
    }
}

/** Immutable description of an officially split model artifact. */
data class ModelShard(
    val index: Int,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
)

data class ModelPackDescriptor(
    val packId: String,
    val version: String,
    val runtime: String,
    val architecture: String,
    val quantization: String,
    val licenseId: String,
    val sourceRevision: String,
    val shards: List<ModelShard>,
) {
    fun validate() {
        require(packId.isNotBlank() && version.isNotBlank())
        require(runtime in setOf("llama.cpp", "sherpa-onnx"))
        require(architecture.isNotBlank() && quantization.isNotBlank())
        require(licenseId.isNotBlank() && sourceRevision.isNotBlank())
        require(shards.isNotEmpty())
        require(shards.map { it.index }.sorted() == shards.indices.toList()) {
            "Model shards must be complete and ordered from index 0"
        }
        require(shards.map { it.fileName }.distinct().size == shards.size) {
            "Model shard filenames must be unique"
        }
        shards.forEach {
            require(it.fileName.isNotBlank() && it.sizeBytes > 0)
            require(it.sha256.matches(Regex("^[a-fA-F0-9]{64}$"))) {
                "Invalid SHA-256 for ${it.fileName}"
            }
        }
    }
}
