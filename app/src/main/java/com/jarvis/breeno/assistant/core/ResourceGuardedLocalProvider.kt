package com.jarvis.breeno.assistant.core

fun interface RuntimeSignalsSource { fun snapshot(): RuntimeSignals }

interface ManagedLocalReasoningProvider : JarvisReasoningProvider { suspend fun unload() }

/** Rechecks live device budget before provider readiness and every inference. */
class ResourceGuardedLocalProvider(
    private val delegate: ManagedLocalReasoningProvider,
    private val signals: RuntimeSignalsSource,
    private val policy: JarvisFoldedRuntimePolicy = JarvisFoldedRuntimePolicy(),
) : ManagedLocalReasoningProvider by delegate {
    fun currentPlan(): RuntimePlan = policy.plan(signals.snapshot())

    override suspend fun availability(): ProviderAvailability {
        val plan = currentPlan()
        if (!plan.allowLocalLlm) {
            if (plan.unloadAfterIdleMillis == 0L) delegate.unload()
            return ProviderAvailability.Unavailable(
                "Device resource policy denied local inference (${plan.heatState})"
            )
        }
        return delegate.availability()
    }

    override suspend fun reason(turn: KernelTurn): ProviderTurnResult {
        val plan = currentPlan()
        check(plan.allowLocalLlm) { "Local inference denied by device resource policy: ${plan.heatState}" }
        return delegate.reason(turn.copy(budget = turn.budget.copy(
            maxOutputTokens = minOf(turn.budget.maxOutputTokens, (plan.maxContextTokens / 2).coerceAtLeast(32))
        )))
    }

    override suspend fun unload() = delegate.unload()
}
