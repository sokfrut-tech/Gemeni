package com.jarvis.breeno.assistant.core

/** Coarse thermal class used to decide whether an optional neural engine may run. */
enum class DeviceHeatState { COOL, WARM, HOT, CRITICAL }

data class RuntimeSignals(
    val availableMemoryMb: Int,
    val thermalHeadroom: Float,
    val thermalStatus: Int,
    val batterySaver: Boolean,
    val charging: Boolean,
    val foreground: Boolean,
)

data class RuntimePlan(
    val allowLocalLlm: Boolean,
    val allowLongReasoning: Boolean,
    val heatState: DeviceHeatState,
    val maxContextTokens: Int,
    val maxConcurrentNeuralEngines: Int,
    val unloadAfterIdleMillis: Long,
)

/**
 * Conservative admission controller. Values are guardrails, not a promise that
 * a model will fit: the model loader must still handle native allocation failure.
 */
class JarvisFoldedRuntimePolicy {
    fun plan(signals: RuntimeSignals): RuntimePlan {
        val headroom = signals.thermalHeadroom.coerceIn(0f, 1f)
        val heat = when {
            signals.thermalStatus >= 5 || headroom >= 0.95f -> DeviceHeatState.CRITICAL
            signals.thermalStatus >= 3 || headroom >= 0.80f -> DeviceHeatState.HOT
            signals.thermalStatus >= 2 || headroom >= 0.55f -> DeviceHeatState.WARM
            else -> DeviceHeatState.COOL
        }
        val memoryAllows = signals.availableMemoryMb >= 1_800
        val foregroundAllows = signals.foreground
        val energyAllows = !signals.batterySaver || signals.charging
        val thermalAllows = heat == DeviceHeatState.COOL || heat == DeviceHeatState.WARM
        val allowLlm = memoryAllows && foregroundAllows && energyAllows && thermalAllows
        val context = when {
            !allowLlm -> 0
            signals.availableMemoryMb >= 5_000 && heat == DeviceHeatState.COOL -> 4096
            signals.availableMemoryMb >= 3_000 && heat == DeviceHeatState.COOL -> 2048
            else -> 1024
        }
        return RuntimePlan(
            allowLocalLlm = allowLlm,
            allowLongReasoning = allowLlm && heat == DeviceHeatState.COOL &&
                signals.availableMemoryMb >= 5_000 && !signals.batterySaver,
            heatState = heat,
            maxContextTokens = context,
            maxConcurrentNeuralEngines = if (allowLlm) 1 else 0,
            unloadAfterIdleMillis = if (heat == DeviceHeatState.CRITICAL) 0L else 30_000L,
        )
    }
}

data class ConversationTurn(
    val role: String,
    val text: String,
) {
    init {
        require(role.isNotBlank())
        require(text.isNotBlank())
    }
}

fun interface ContextCompressor {
    suspend fun compress(olderTurns: List<ConversationTurn>, maxCharacters: Int): String
}

data class FoldedContext(
    val originalTurnCount: Int,
    val carryCapsule: String,
    val recentTurns: List<ConversationTurn>,
)

/** Folds older dialogue into a compact capsule while preserving the newest turns verbatim. */
class JarvisContextFolder(
    private val compressor: ContextCompressor,
) {
    suspend fun fold(
        turns: List<ConversationTurn>,
        keepRecentTurns: Int,
        maxCapsuleCharacters: Int = 2_000,
    ): FoldedContext {
        require(keepRecentTurns >= 0) { "keepRecentTurns must not be negative" }
        require(maxCapsuleCharacters > 0) { "maxCapsuleCharacters must be positive" }
        require(turns.isNotEmpty()) { "Cannot fold an empty conversation" }

        val splitAt = (turns.size - keepRecentTurns).coerceAtLeast(0)
        val older = turns.take(splitAt)
        val recent = turns.drop(splitAt).toList()
        if (older.isEmpty()) {
            return FoldedContext(
                originalTurnCount = turns.size,
                carryCapsule = "",
                recentTurns = recent,
            )
        }
        val capsule = compressor.compress(older, maxCapsuleCharacters)
            .trim()
            .take(maxCapsuleCharacters)
        require(capsule.isNotEmpty()) {
            "Context compression returned an empty carry capsule; refusing to lose conversation history"
        }
        return FoldedContext(
            originalTurnCount = turns.size,
            carryCapsule = capsule,
            recentTurns = recent,
        )
    }
}
