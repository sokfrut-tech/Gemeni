package com.jarvis.breeno.assistant.core.ai

import org.junit.Assert.*
import org.junit.Test

class JarvisFactoryCoordinatorTest {
    private val coordinator = JarvisFactoryCoordinator()

    @Test fun movesTaskThroughReviewAndVerification() {
        val queued = FactoryTask("task-1", "Проверить команду")
        val assigned = coordinator.transition(queued, FactoryTaskState.ASSIGNED)!!
        val running = coordinator.transition(assigned, FactoryTaskState.RUNNING)!!
        val review = coordinator.transition(running, FactoryTaskState.REVIEW)!!
        val verifying = coordinator.transition(review, FactoryTaskState.VERIFYING)!!
        val done = coordinator.transition(verifying, FactoryTaskState.DONE)!!
        assertEquals(FactoryTaskState.DONE, done.state)
    }

    @Test fun rejectsInvalidTransitionsAndBoundsRetries() {
        val queued = FactoryTask("task-2", "Тест")
        assertNull(coordinator.transition(queued, FactoryTaskState.DONE))
        val failed = queued.copy(state = FactoryTaskState.FAILED, attempts = 2)
        assertNull(coordinator.retry(failed))
    }

    @Test fun supportsUserApprovalWaitAndCancel() {
        val running = FactoryTask("task-3", "Подтверждение", FactoryTaskState.RUNNING)
        val waiting = coordinator.transition(running, FactoryTaskState.WAITING_FOR_USER)!!
        assertEquals(FactoryTaskState.RUNNING, coordinator.transition(waiting, FactoryTaskState.RUNNING)?.state)
        assertEquals(FactoryTaskState.CANCELLED, coordinator.transition(waiting, FactoryTaskState.CANCELLED)?.state)
    }
}
