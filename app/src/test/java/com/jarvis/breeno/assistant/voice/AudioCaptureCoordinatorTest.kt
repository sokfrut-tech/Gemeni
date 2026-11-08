package com.jarvis.breeno.assistant.voice

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioCaptureCoordinatorTest {
    @Test
    fun onlyOneCaptureCanBeStartedAtATime() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = AudioCaptureCoordinator(
            scope = scope,
            frames = {
                flow {
                    started.complete(Unit)
                    awaitCancellation()
                }
            },
            onFrame = {},
        )
        try {
            assertTrue(coordinator.start())
            started.await()
            assertFalse(coordinator.start())
            assertEquals(AudioCaptureCoordinator.State.CAPTURING, coordinator.state.value)
        } finally {
            coordinator.close()
            scope.cancel()
        }
    }

    @Test
    fun stopWaitsUntilCaptureFlowCleanupCompletes() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cleaned = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = AudioCaptureCoordinator(
            scope = scope,
            frames = {
                flow {
                    started.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        cleaned.complete(Unit)
                    }
                }
            },
            onFrame = {},
        )
        try {
            assertTrue(coordinator.start())
            started.await()
            coordinator.stop()
            assertTrue(cleaned.isCompleted)
            assertEquals(AudioCaptureCoordinator.State.IDLE, coordinator.state.value)
        } finally {
            coordinator.close()
            scope.cancel()
        }
    }

    @Test
    fun callSuspensionReleasesCaptureAndNeverAutoResumes() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cleaned = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = AudioCaptureCoordinator(
            scope = scope,
            frames = {
                flow {
                    started.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        cleaned.complete(Unit)
                    }
                }
            },
            onFrame = {},
        )
        try {
            assertTrue(coordinator.start())
            started.await()
            coordinator.suspendForCall()
            assertTrue(cleaned.isCompleted)
            assertEquals(AudioCaptureCoordinator.State.SUSPENDED_FOR_CALL, coordinator.state.value)
            assertFalse(coordinator.resumeAfterInterruption(userOptInStillActive = false))
            assertEquals(AudioCaptureCoordinator.State.SUSPENDED_FOR_CALL, coordinator.state.value)
            assertTrue(coordinator.resumeAfterInterruption(userOptInStillActive = true))
            assertEquals(AudioCaptureCoordinator.State.IDLE, coordinator.state.value)
            // Returning to IDLE is only permission to re-prepare; capture is not restarted.
            assertTrue(cleaned.isCompleted)
        } finally {
            coordinator.close()
            scope.cancel()
        }
    }

    @Test
    fun competingSystemAudioAlsoRequiresExplicitResume() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cleaned = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = AudioCaptureCoordinator(
            scope = scope,
            frames = {
                flow {
                    started.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        cleaned.complete(Unit)
                    }
                }
            },
            onFrame = {},
        )
        try {
            assertTrue(coordinator.start())
            started.await()
            coordinator.suspendForSystemAudio()
            assertTrue(cleaned.isCompleted)
            assertEquals(AudioCaptureCoordinator.State.SUSPENDED_FOR_SYSTEM_AUDIO, coordinator.state.value)
            assertFalse(coordinator.resumeAfterInterruption(userOptInStillActive = false))
            assertTrue(coordinator.resumeAfterInterruption(userOptInStillActive = true))
            assertEquals(AudioCaptureCoordinator.State.IDLE, coordinator.state.value)
        } finally {
            coordinator.close()
            scope.cancel()
        }
    }
}
