package com.jarvis.breeno.assistant.core.ai

enum class FactoryTaskState {
    QUEUED, ASSIGNED, RUNNING, WAITING_FOR_USER, BLOCKED, REVIEW,
    VERIFYING, DONE, FAILED, CANCELLED
}

data class FactoryTask(
    val id: String,
    val title: String,
    val state: FactoryTaskState = FactoryTaskState.QUEUED,
    val attempts: Int = 0,
    val maxAttempts: Int = 2
)

/**
 * Small deterministic task-state core. Persistence and worker dispatch are
 * intentionally injected later; this class has no network or Android access.
 */
class JarvisFactoryCoordinator {
    fun transition(task: FactoryTask, next: FactoryTaskState): FactoryTask? {
        if (task.id.isBlank() || task.title.isBlank()) return null
        if (task.attempts > task.maxAttempts) return null
        val allowed = transitions[task.state].orEmpty()
        if (next !in allowed) return null
        return task.copy(state = next)
    }

    fun retry(task: FactoryTask): FactoryTask? {
        if (task.state != FactoryTaskState.FAILED || task.attempts >= task.maxAttempts) return null
        return task.copy(state = FactoryTaskState.QUEUED, attempts = task.attempts + 1)
    }

    companion object {
        private val transitions = mapOf(
            FactoryTaskState.QUEUED to setOf(FactoryTaskState.ASSIGNED, FactoryTaskState.CANCELLED),
            FactoryTaskState.ASSIGNED to setOf(FactoryTaskState.RUNNING, FactoryTaskState.BLOCKED, FactoryTaskState.CANCELLED),
            FactoryTaskState.RUNNING to setOf(FactoryTaskState.WAITING_FOR_USER, FactoryTaskState.BLOCKED, FactoryTaskState.REVIEW, FactoryTaskState.VERIFYING, FactoryTaskState.DONE, FactoryTaskState.FAILED, FactoryTaskState.CANCELLED),
            FactoryTaskState.WAITING_FOR_USER to setOf(FactoryTaskState.RUNNING, FactoryTaskState.CANCELLED, FactoryTaskState.BLOCKED),
            FactoryTaskState.REVIEW to setOf(FactoryTaskState.VERIFYING, FactoryTaskState.BLOCKED, FactoryTaskState.FAILED),
            FactoryTaskState.VERIFYING to setOf(FactoryTaskState.DONE, FactoryTaskState.FAILED, FactoryTaskState.BLOCKED),
            FactoryTaskState.FAILED to setOf(FactoryTaskState.QUEUED, FactoryTaskState.CANCELLED),
            FactoryTaskState.BLOCKED to setOf(FactoryTaskState.CANCELLED),
            FactoryTaskState.DONE to emptySet(),
            FactoryTaskState.CANCELLED to emptySet()
        )
    }
}
