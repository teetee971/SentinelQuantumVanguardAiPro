package com.sentinel.quantum.ui.screens

import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationSafeReputationPersistenceTest {
    @Test
    fun invalidation_during_io_discards_stale_candidate_without_blocking_invalidation() = runBlocking {
        val ioStarted = CompletableDeferred<Unit>()
        val releaseIo = CompletableDeferred<Unit>()
        val persisted = Collections.synchronizedList(mutableListOf<String>())
        val discarded = Collections.synchronizedList(mutableListOf<String>())
        val coordinator = GenerationSafeReputationPersistence<String>(
            persist = { candidate, value ->
                persisted += "$candidate:$value"
                ioStarted.complete(Unit)
                releaseIo.await()
            },
            discard = { candidate -> discarded += candidate }
        )

        val generationA = coordinator.invalidate()
        val persistJob = launch {
            assertFalse(coordinator.persistIfCurrent(generationA, "A", "old"))
        }
        withTimeout(1_000) { ioStarted.await() }

        val generationB = coordinator.invalidate()
        assertTrue(generationB > generationA)
        releaseIo.complete(Unit)
        persistJob.join()

        assertEquals(listOf("A:old"), persisted)
        assertEquals(listOf("A"), discarded)
    }
}
