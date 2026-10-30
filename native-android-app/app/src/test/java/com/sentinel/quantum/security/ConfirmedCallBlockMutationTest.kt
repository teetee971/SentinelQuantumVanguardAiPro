package com.sentinel.quantum.security

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ConfirmedCallBlockMutationTest {
    @Test fun cancellationDuringNonInterruptibleReadNeverCommitsLater() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var writes = 0
        val job = launch {
            ConfirmedCallBlockMutation.execute({ true }, { true }, {
                withContext(NonCancellable) { entered.complete(Unit); release.await() }
                false
            }, { writes++; true })
        }
        entered.await()
        job.cancel()
        release.complete(Unit)
        job.join()
        assertEquals(0, writes)
    }

    @Test fun simultaneousButtonsPreserveBothRules() = runBlocking {
        var stored = emptySet<String>()
        coroutineScope {
            listOf("first", "second").map { number -> async {
                ConfirmedCallBlockMutation.execute({ true }, { true }, { number in stored }, {
                    val previous = stored
                    yield()
                    stored = previous + number
                    true
                })
            } }.awaitAll()
        }
        assertEquals(setOf("first", "second"), stored)
    }

    @Test fun selectionLostDuringMembershipReadDoesNotCommit() = runBlocking {
        var current = true
        var writes = 0
        val result = ConfirmedCallBlockMutation.execute({ true }, { current },
            { current = false; false }, { writes++; true })
        assertNull(result)
        assertEquals(0, writes)
    }

    @Test fun existingRuleIsNotRewrittenOrExtended() = runBlocking {
        var writes = 0
        assertEquals(true, ConfirmedCallBlockMutation.execute({ true }, { true }, { true }, { writes++; true }))
        assertEquals(0, writes)
    }
}
