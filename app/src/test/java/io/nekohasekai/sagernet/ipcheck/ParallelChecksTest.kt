package io.nekohasekai.sagernet.ipcheck

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class ParallelChecksTest {
    @Test fun independentCompletionAndFailure() = runBlocking {
        val gates = List(3) { CompletableDeferred<Unit>() }
        val started = mutableListOf<Int>()
        val published = mutableListOf<Pair<Int, Result<Int>>>()
        val job = async {
            parallelChecks((0..2).toList(), { i ->
                started.add(i)
                gates[i].await()
                if (i == 1) error("individual failure")
                i * 10
            }) { i, result -> published.add(i to result) }
        }
        yield()
        assertEquals(listOf(0, 1, 2), started)
        gates[2].complete(Unit)
        yield()
        assertEquals(2, published.single().first)
        assertEquals(20, published.single().second.getOrNull())
        gates[1].complete(Unit)
        yield()
        assertTrue(published.last().second.isFailure)
        assertFalse(job.isCompleted)
        gates[0].complete(Unit)
        job.await()
        assertEquals(listOf(2, 1, 0), published.map { it.first })
        assertEquals(0, published.last().second.getOrNull())
    }

    @Test fun cancelledRunCannotPublishLateResults() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val published = mutableListOf<String>()
        val old = async {
            parallelChecks(listOf("old"), { gate.await(); it }) { _, result ->
                published.add(result.getOrThrow())
            }
        }
        yield()
        old.cancelAndJoin()
        parallelChecks(listOf("new"), { it }) { _, result -> published.add(result.getOrThrow()) }
        gate.complete(Unit)
        yield()
        assertEquals(listOf("new"), published)
    }

    @Test fun cancellationIsNeverReportedAsFailure() = runBlocking {
        var published = false
        try {
            parallelChecks(listOf(1), { throw CancellationException("cancel") }) { _, _: Result<Int> -> published = true }
        } catch (_: CancellationException) {
            // Cancellation propagates through coroutineScope.
        }
        assertFalse(published)
    }
}
