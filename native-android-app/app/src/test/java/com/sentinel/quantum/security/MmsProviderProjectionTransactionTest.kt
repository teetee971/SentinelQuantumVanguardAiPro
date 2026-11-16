package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsProviderProjectionTransactionTest {
    private class FakeOperations(
        private val failAt: String? = null,
        private val deleteSucceeds: Boolean = true,
        private val clearSucceeds: Boolean = true
    ) : MmsProviderProjectionTransaction.Operations {
        val calls = mutableListOf<String>()

        override fun beginJournal(): Boolean = call("journal")
        override fun insertRoot(): Long? = if (call("root")) 42L else null
        override fun recordRoot(providerMessageId: Long): Boolean {
            assertEquals(42L, providerMessageId)
            return call("recordRoot")
        }
        override fun insertAddress(providerMessageId: Long): Boolean {
            assertEquals(42L, providerMessageId)
            return call("address")
        }
        override fun insertParts(providerMessageId: Long): Boolean {
            assertEquals(42L, providerMessageId)
            return call("parts")
        }
        override fun markReady(providerMessageId: Long): Boolean {
            assertEquals(42L, providerMessageId)
            return call("ready")
        }
        override fun deleteRoot(providerMessageId: Long): Boolean {
            calls += "delete"
            return deleteSucceeds
        }
        override fun clearJournal(): Boolean {
            calls += "clear"
            return clearSucceeds
        }

        private fun call(name: String): Boolean {
            calls += name
            return failAt != name
        }
    }

    @Test fun completeProjectionBecomesReadyWithoutCleanup() {
        val ops = FakeOperations()
        val result = MmsProviderProjectionTransaction.execute(ops)
        assertEquals(MmsProviderProjectionTransaction.Result.Ready(42L), result)
        assertEquals(listOf("journal", "root", "recordRoot", "address", "parts", "ready"), ops.calls)
    }

    @Test fun noProviderMutationOccursWhenJournalCannotStart() {
        val ops = FakeOperations(failAt = "journal")
        val result = MmsProviderProjectionTransaction.execute(ops)
        assertEquals(
            MmsProviderProjectionTransaction.Result.Rejected(
                "MMS_PROVIDER_JOURNAL_UNAVAILABLE",
                cleanupConfirmed = true
            ),
            result
        )
        assertEquals(listOf("journal"), ops.calls)
    }

    @Test fun rootInsertFailureClearsPreMutationJournal() {
        val ops = FakeOperations(failAt = "root")
        val result = MmsProviderProjectionTransaction.execute(ops)
        assertEquals(
            MmsProviderProjectionTransaction.Result.Rejected(
                "MMS_PROVIDER_ROOT_INSERT_FAILED",
                cleanupConfirmed = true
            ),
            result
        )
        assertEquals(listOf("journal", "root", "clear"), ops.calls)
    }

    @Test fun everyFailureAfterRootAttemptsCompensatingDeleteBeforeJournalClear() {
        for (failure in listOf("recordRoot", "address", "parts", "ready")) {
            val ops = FakeOperations(failAt = failure)
            val result = MmsProviderProjectionTransaction.execute(ops)
            assertTrue("failure=$failure", result is MmsProviderProjectionTransaction.Result.Rejected)
            val deleteIndex = ops.calls.indexOf("delete")
            val clearIndex = ops.calls.indexOf("clear")
            assertTrue("failure=$failure calls=${ops.calls}", deleteIndex > 1)
            assertTrue("failure=$failure calls=${ops.calls}", clearIndex > deleteIndex)
        }
    }

    @Test fun journalIsRetainedWhenRootCleanupCannotBeConfirmed() {
        val ops = FakeOperations(failAt = "parts", deleteSucceeds = false)
        val result = MmsProviderProjectionTransaction.execute(ops)
            as MmsProviderProjectionTransaction.Result.Rejected
        assertFalse(result.cleanupConfirmed)
        assertTrue("journal must remain for recovery", "clear" !in ops.calls)
    }

    @Test fun cleanupIsNotClaimedWhenJournalRemovalFails() {
        val ops = FakeOperations(failAt = "address", clearSucceeds = false)
        val result = MmsProviderProjectionTransaction.execute(ops)
            as MmsProviderProjectionTransaction.Result.Rejected
        assertFalse(result.cleanupConfirmed)
        assertEquals(listOf("journal", "root", "recordRoot", "address", "delete", "clear"), ops.calls)
    }
}
