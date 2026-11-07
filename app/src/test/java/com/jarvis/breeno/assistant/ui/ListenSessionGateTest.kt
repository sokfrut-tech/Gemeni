package com.jarvis.breeno.assistant.ui

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenSessionGateTest {
    @Test
    fun rejectsOverlappingSessionsAndAllowsNextAfterRelease() {
        val gate = ListenSessionGate()

        assertTrue(gate.tryEnter())
        assertFalse(gate.tryEnter())

        gate.leave()

        assertTrue(gate.tryEnter())
        gate.leave()
    }

    @Test
    fun failedEntryDoesNotReleaseTheActiveSession() {
        val gate = ListenSessionGate()

        assertTrue(gate.tryEnter())
        assertFalse(gate.tryEnter())
        assertFalse(gate.tryEnter())

        gate.leave()
        assertTrue(gate.tryEnter())
        gate.leave()
    }

    @Test
    fun concurrentCallersCanAcquireOnlyOneSession() {
        val gate = ListenSessionGate()
        val contenders = 16
        val ready = CountDownLatch(contenders)
        val start = CountDownLatch(1)
        val releaseWinner = CountDownLatch(1)
        val finished = CountDownLatch(contenders)
        val successfulEntries = AtomicInteger(0)
        val workerFailure = AtomicReference<Throwable?>(null)
        val executor = Executors.newFixedThreadPool(contenders)

        try {
            repeat(contenders) {
                executor.execute {
                    ready.countDown()
                    try {
                        assertTrue(start.await(5, TimeUnit.SECONDS))
                        if (gate.tryEnter()) {
                            successfulEntries.incrementAndGet()
                            try {
                                assertTrue(releaseWinner.await(5, TimeUnit.SECONDS))
                            } finally {
                                gate.leave()
                            }
                        }
                    } catch (failure: Throwable) {
                        workerFailure.compareAndSet(null, failure)
                    } finally {
                        finished.countDown()
                    }
                }
            }

            assertTrue("Workers did not become ready", ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            assertTrue("Workers did not finish their acquisition attempt", finished.await(5, TimeUnit.SECONDS))
            assertEquals(1, successfulEntries.get())
            assertNull("Worker failed: ${workerFailure.get()}", workerFailure.get())
        } finally {
            releaseWinner.countDown()
            executor.shutdownNow()
            assertTrue("Executor did not terminate", executor.awaitTermination(5, TimeUnit.SECONDS))
        }

        assertTrue(gate.tryEnter())
        gate.leave()
    }
}
