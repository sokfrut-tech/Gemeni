package com.jarvis.breeno.assistant.core

/**
 * Device-aware runtime envelope. Thermal headroom uses Android's convention:
 * values approaching 1.0 mean severe throttling is near.
 */
enum class DeviceHeatState { COOL, WARM, HOT, CRITICAL }

data class RuntimeSignals(
    val availableMemoryMb: Int,
    val thermalHeadroom: Float?,
    val thermalStatus: Int?,
    val batterySaver: Boolean,
    val charging: Boolean,
    val foreground: Boolean,
)

data class FoldedRuntimePlan(
    val heatState: DeviceHeatState,
    val allowLocalLlm: Boolean,
    val maxContextTokens: Int,
    val maxOutputTokens: Int,
    val maxConcurrentNeuralEngines: Int,
    val allowLongReasoning: Boolean,
    val unloadAfterIdleMillis: Long,
    val reason: String,
)

/**
 * Conservative policy: use measured free memory and thermal signals, not advertised
 * RAM. Thresholds are initial safety defaults and must be tuned with OPPO device traces.
 */
class JarvisFoldedRuntimePolicy(
    private val minimumLlmMemoryMb: Int = 1_500,
) {
    fun plan(signals: RuntimeSignals): FoldedRuntimePlan {
        require(signals.availableMemoryMb >= 0)
        val heat = when {
            signals.thermalStatus != null && signals.thermalStatus >= 5 -> DeviceHeatState.CRITICAL
            signals.thermalStatus != null && signals.thermalStatus >= 4 -> DeviceHeatState.HOT
            signals.thermalStatus != null && signals.thermalStatus >= 2 -> DeviceHeatState.WARM
            signals.thermalHeadroom != null && signals.thermalHeadroom >= 0.92f -> DeviceHeatState.CRITICAL
            signals.thermalHeadroom != null && signals.thermalHeadroom >= 0.82f -> DeviceHeatState.HOT
            signals.thermalHeadroom != null && signals.thermalHeadroom >= 0.68f -> DeviceHeatState.WARM
            else -> DeviceHeatState.COOL
        }

        val powerRestricted = signals.batterySaver && !signals.charging
        val memoryAllows = signals.availableMemoryMb >= minimumLlmMemoryMb
        val allowLlm = memoryAllows && heat < DeviceHeatState.HOT && !powerRestricted
        val plan = when (heat) {
            DeviceHeatState.COOL -> FoldedRuntimePlan(
                heat, allowLlm, if (allowLlm) 2048 else 512,
                if (allowLlm) 160 else 64, if (allowLlm) 1 else 1,
                allowLlm && signals.availableMemoryMb >= 3_000,
                45_000, "Cool device; bounded local inference is permitted"
            )
            DeviceHeatState.WARM -> FoldedRuntimePlan(
                heat, allowLlm, if (allowLlm) 1024 else 384,
                if (allowLlm) 80 else 48, 1, false,
                20_000, "Warm device; short turns and sequential engines only"
            )
            DeviceHeatState.HOT -> FoldedRuntimePlan(
                heat, false, 256, 32, 1, false,
                5_000, "Hot device; suspend LLM and retain voice/actions only"
            )
            DeviceHeatState.CRITICAL -> FoldedRuntimePlan(
                heat, false, 128, 16, 1, false,
                0, "Critical heat; stop neural inference and release native resources"
            )
        }
        return if (powerRestricted) plan.copy(
            allowLocalLlm = false,
            maxContextTokens = minOf(plan.maxContextTokens, 256),
            maxOutputTokens = minOf(plan.maxOutputTokens, 32),
            allowLongReasoning = false,
            reason = "Battery saver active; " + plan.reason
        ) else plan
    }
}

/**
 * Context folding keeps recent dialogue verbatim and compresses older turns into
 * a short carry capsule. The summarizer itself is injected; this class never
 * silently discards content when the budget cannot be met.
 */
data class ConversationTurn(val role: String, val text: String, val importance: Int = 0)

data class FoldedContext(
    val carryCapsule: String,
    val recentTurns: List<ConversationTurn>,
    val originalTurnCount: Int,
)

fun interface ContextCompressor {
    suspend fun compress(olderTurns: List<ConversationTurn>, maxChars: Int): String
}

class JarvisContextFolder(
    private val compressor: ContextCompressor,
) {
    suspend fun fold(
        turns: List<ConversationTurn>,
        keepRecentTurns: Int = 4,
        maxCapsuleChars: Int = 1_200,
    ): FoldedContext {
        require(keepRecentTurns >= 1)
        require(maxCapsuleChars in 128..8_000)
        if (turns.size <= keepRecentTurns) {
            return FoldedContext("", turns, turns.size)
        }
        val older = turns.dropLast(keepRecentTurns)
        val capsule = compressor.compress(older, maxCapsuleChars)
        require(capsule.isNotBlank()) { "Context compressor returned an empty carry capsule" }
        require(capsule.length <= maxCapsuleChars) { "Carry capsule exceeds its budget" }
        return FoldedContext(capsule, turns.takeLast(keepRecentTurns), turns.size)
    }
}
