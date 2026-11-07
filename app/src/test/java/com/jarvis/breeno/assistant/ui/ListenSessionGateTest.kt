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
    fun defersResourceCleanupUntilActiveSessionLeaves() {
        val gate = ListenSessionGate()
        val cleanupCalls = AtomicInteger(0)

        assertTrue(gate.tryEnter())
        gate.closeWhenIdle { cleanupCalls.incrementAndGet() }

        assertTrue(gate.isClosing)
        assertFalse(gate.tryEnter())
        assertEquals(0, cleanupCalls.get())

        gate.leave()

        assertEquals(1, cleanupCalls.get())
        gate.closeWhenIdle { cleanupCalls.incrementAndGet() }
        assertEquals(1, cleanupCalls.get())
    }

    @Test
    fun closesResourcesImmediatelyWhenNoSessionIsActive() {
        val gate = ListenSessionGate()
        val cleanupCalls = AtomicInteger(0)

        gate.closeWhenIdle { cleanupCalls.incrementAndGet() }

        assertEquals(1, cleanupCalls.get())
        assertTrue(gate.isClosing)
        assertFalse(gate.tryEnter())
    }

    @Test
    fun concurrentCallersCanAcquireOnlyOneSession() {
        val gate = ListenSessionGate()
        val contenders = 16
        val ready = CountDownLatch(contenders)
        val start = CountDownLatch(1)
        val attempted = CountDownLatch(contenders)
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
                        val acquired = gate.tryEnter()
                        attempted.countDown()
                        if (acquired) {
                            successfulEntries.incrementAndGet()
                            try {
                                assertTrue(releaseWinner.await(10, TimeUnit.SECONDS))
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
            assertTrue("Workers did not attempt acquisition", attempted.await(5, TimeUnit.SECONDS))
            assertEquals(1, successfulEntries.get())
            releaseWinner.countDown()
            assertTrue("Workers did not finish", finished.await(5, TimeUnit.SECONDS))
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
