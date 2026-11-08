package com.rassvet.lecturial.ui

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InWorkersTest {
    @Test
    fun `two sessions run side by side and the results keep their order`() = runBlocking {
        val running = AtomicInteger(0)
        val most = AtomicInteger(0)
        val sessions = AtomicInteger(0)
        val items = (1..7).toList()
        val started = System.nanoTime()

        val results = items.inWorkers(2, { sessions.incrementAndGet() }) { _, item ->
            most.accumulateAndGet(running.incrementAndGet(), ::maxOf)
            delay(50)
            running.decrementAndGet()
            item * 10
        }

        val millis = (System.nanoTime() - started) / 1_000_000
        assertEquals(items.map { it * 10 }, results)
        assertEquals("Never more than two at once", 2, most.get())
        assertEquals("One session per worker", 2, sessions.get())
        assertTrue("One at a time would take 350 ms, took $millis", millis < 300)
    }

    @Test
    fun `a failure passes the original exception on and stops the other worker`() = runBlocking {
        val failure = IllegalStateException("boom")
        val started = AtomicInteger(0)
        val finished = AtomicInteger(0)

        val thrown = try {
            (1..6).toList().inWorkers(2, { }) { _, item ->
                if (item == 2) throw failure
                started.incrementAndGet()
                delay(60_000)
                finished.incrementAndGet()
            }
            null
        } catch (e: Exception) {
            e
        }

        // Coroutines' stack-trace recovery may hand back a copy, so compare what it says.
        assertTrue(thrown is IllegalStateException)
        assertEquals(failure.message, thrown?.message)
        assertEquals("The other worker never gets past its first item", 1, started.get())
        assertEquals(0, finished.get())
    }

    @Test
    fun `cancelling the caller cancels both workers`() = runBlocking {
        val started = AtomicInteger(0)
        val cancelled = AtomicInteger(0)
        val finished = AtomicInteger(0)
        val job = launch(Dispatchers.Default) {
            (1..6).toList().inWorkers(2, { }) { _, _ ->
                started.incrementAndGet()
                try {
                    delay(60_000)
                } finally {
                    cancelled.incrementAndGet()
                }
                finished.incrementAndGet()
            }
        }
        while (started.get() < 2) delay(10)

        job.cancelAndJoin()

        assertEquals(2, cancelled.get())
        assertEquals(0, finished.get())
        assertEquals("No third item was started", 2, started.get())
    }

    @Test
    fun `more workers than items opens only as many sessions as items`() = runBlocking {
        val sessions = AtomicInteger(0)

        val results = listOf("a", "b", "c").inWorkers(5, { sessions.incrementAndGet() }) { _, item -> item.uppercase() }

        assertEquals(listOf("A", "B", "C"), results)
        assertEquals(3, sessions.get())
    }

    @Test
    fun `nothing to load opens no session`() = runBlocking {
        val sessions = AtomicInteger(0)

        val results = emptyList<String>().inWorkers(2, { sessions.incrementAndGet() }) { _, item -> item }

        assertEquals(emptyList<String>(), results)
        assertEquals(0, sessions.get())
    }
}
