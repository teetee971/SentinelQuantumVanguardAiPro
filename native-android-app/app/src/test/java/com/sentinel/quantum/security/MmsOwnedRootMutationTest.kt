package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsOwnedRootMutationTest {
    private val ownedOutbox = MmsOwnedRootMutation.Observed(
        id = 42L,
        creator = PACKAGE,
        messageBox = MmsOwnedRootMutation.BOX_OUTBOX,
        transactionId = TRANSACTION
    )
    private val expected = MmsOwnedRootMutation.OutgoingIdentity(42L, PACKAGE, TRANSACTION)

    @Test fun interruptedWriteBeforeInsertDropsJournalWithoutDeleting() {
        val decision = MmsOwnedRootMutation.authorizeOutgoingDelete(true, expected, observed = null)
        assertEquals(MmsOwnedRootMutation.Decision.Absent, decision)
        var deleteCalled = false
        val cleared = MmsOwnedRootMutation.applyDelete(decision, {
            deleteCalled = true
            1
        }, { ownedOutbox })
        assertTrue(cleared)
        assertFalse(deleteCalled)
    }

    @Test fun interruptedWriteAfterInsertDeletesOnlyTheOwnedOutbox() {
        val decision = MmsOwnedRootMutation.authorizeOutgoingDelete(true, expected, ownedOutbox)
        val mutate = decision as MmsOwnedRootMutation.Decision.Mutate
        assertTrue(MmsOwnedRootMutation.bindsOwnership(mutate.clauses))
        assertTrue(MmsOwnedRootMutation.matches(ownedOutbox, mutate.clauses))
        var row: MmsOwnedRootMutation.Observed? = ownedOutbox
        val removed = MmsOwnedRootMutation.applyDelete(decision, { clauses ->
            if (MmsOwnedRootMutation.matches(row, clauses)) {
                row = null
                1
            } else {
                0
            }
        }, { row })
        assertTrue(removed)
        assertEquals(null, row)
    }

    @Test fun resumeAfterCrashKeepsSubmittedAndUnknownRows() {
        assertEquals(
            MmsOwnedRootMutation.RepairAction.KEEP_ROW,
            MmsOwnedRootMutation.repairAction(
                MmsOwnedRootMutation.OutgoingPhase.READY,
                roleHeld = true
            )
        )
        assertEquals(
            MmsOwnedRootMutation.RepairAction.KEEP_ROW,
            MmsOwnedRootMutation.repairAction(
                MmsOwnedRootMutation.OutgoingPhase.SUBMISSION_UNKNOWN,
                roleHeld = true
            )
        )
        assertEquals(
            MmsOwnedRootMutation.RepairAction.KEEP_ROW,
            MmsOwnedRootMutation.repairAction(
                MmsOwnedRootMutation.OutgoingPhase.SUBMITTED,
                roleHeld = true
            )
        )
        val unknown = MmsOwnedRootMutation.repairAction(
            MmsOwnedRootMutation.OutgoingPhase.SUBMISSION_UNKNOWN,
            roleHeld = true
        )
        assertFalse(unknown == MmsOwnedRootMutation.RepairAction.DELETE_OWNED_INCOMPLETE)
        assertFalse(unknown == MmsOwnedRootMutation.RepairAction.FINALIZE)
    }

    @Test fun doubleFinalizeDoesNotFlipATerminalBox() {
        val first = MmsOwnedRootMutation.authorizeOutgoingFinalize(true, expected, true, ownedOutbox)
        assertTrue(first is MmsOwnedRootMutation.Decision.Mutate)
        val sent = ownedOutbox.copy(messageBox = MmsOwnedRootMutation.BOX_SENT)
        assertEquals(
            MmsOwnedRootMutation.Decision.AlreadyDone,
            MmsOwnedRootMutation.authorizeOutgoingFinalize(true, expected, true, sent)
        )
        val failed = ownedOutbox.copy(messageBox = MmsOwnedRootMutation.BOX_FAILED)
        assertEquals(
            MmsOwnedRootMutation.Decision.Refuse,
            MmsOwnedRootMutation.authorizeOutgoingFinalize(true, expected, true, failed)
        )
        var updates = 0
        val idempotent = MmsOwnedRootMutation.applyFinalize(
            MmsOwnedRootMutation.Decision.AlreadyDone,
            MmsOwnedRootMutation.BOX_SENT,
            {
                updates++
                1
            },
            { sent }
        )
        assertTrue(idempotent)
        assertEquals(0, updates)
    }

    @Test fun smsRoleRevocationRefusesDeleteAndFinalize() {
        val delete = MmsOwnedRootMutation.authorizeOutgoingDelete(false, expected, ownedOutbox)
        val finalize = MmsOwnedRootMutation.authorizeOutgoingFinalize(false, expected, true, ownedOutbox)
        assertEquals(MmsOwnedRootMutation.Decision.Refuse, delete)
        assertEquals(MmsOwnedRootMutation.Decision.Refuse, finalize)
        var called = false
        assertFalse(MmsOwnedRootMutation.applyDelete(delete, { called = true; 1 }, { null }))
        assertFalse(
            MmsOwnedRootMutation.applyFinalize(
                finalize,
                MmsOwnedRootMutation.BOX_SENT,
                { called = true; 1 },
                { ownedOutbox }
            )
        )
        assertFalse(called)
        assertEquals(
            MmsOwnedRootMutation.RepairAction.RETRY_LATER,
            MmsOwnedRootMutation.repairAction(
                MmsOwnedRootMutation.OutgoingPhase.ROOT_INSERTED,
                roleHeld = false
            )
        )
    }

    @Test fun concurrentIdentityChangeIsNotDeleted() {
        var row: MmsOwnedRootMutation.Observed? = ownedOutbox
        val decision = MmsOwnedRootMutation.authorizeOutgoingDelete(true, expected, row)
        row = row?.copy(creator = "com.other.messenger")
        val removed = MmsOwnedRootMutation.applyDelete(decision, { clauses ->
            if (MmsOwnedRootMutation.matches(row, clauses)) {
                row = null
                1
            } else {
                0
            }
        }, { row })
        assertFalse(removed)
        assertEquals("com.other.messenger", row?.creator)
        assertEquals(MmsOwnedRootMutation.BOX_OUTBOX, row?.messageBox)
    }

    @Test fun foreignOrMismatchedOutgoingRootIsRefused() {
        assertEquals(
            MmsOwnedRootMutation.Decision.Refuse,
            MmsOwnedRootMutation.authorizeOutgoingDelete(
                true,
                expected,
                ownedOutbox.copy(creator = "com.other.messenger")
            )
        )
        assertEquals(
            MmsOwnedRootMutation.Decision.Refuse,
            MmsOwnedRootMutation.authorizeOutgoingDelete(
                true,
                expected,
                ownedOutbox.copy(transactionId = "other-transaction")
            )
        )
        assertEquals(
            MmsOwnedRootMutation.Decision.Refuse,
            MmsOwnedRootMutation.authorizeOutgoingDelete(
                true,
                expected,
                ownedOutbox.copy(messageBox = MmsOwnedRootMutation.BOX_SENT)
            )
        )
    }

    @Test fun incomingDeleteRequiresOwnershipAndDoesNotNeedAnAddressRow() {
        val incoming = MmsOwnedRootMutation.IncomingIdentity(
            providerMessageId = 7L,
            packageName = PACKAGE,
            messageId = "mid",
            transactionId = null,
            dateSeconds = 100L,
            subscriptionId = 2
        )
        val row = MmsOwnedRootMutation.Observed(
            id = 7L,
            creator = PACKAGE,
            messageBox = MmsOwnedRootMutation.BOX_INBOX,
            transactionId = null,
            messageId = "mid",
            dateSeconds = 100L,
            subscriptionId = 2
        )
        val decision = MmsOwnedRootMutation.authorizeIncomingDelete(true, incoming, row)
        assertTrue(decision is MmsOwnedRootMutation.Decision.Mutate)
        assertEquals(
            MmsOwnedRootMutation.Decision.Refuse,
            MmsOwnedRootMutation.authorizeIncomingDelete(
                true,
                incoming,
                row.copy(creator = "com.other.messenger")
            )
        )
        assertEquals(
            MmsOwnedRootMutation.Decision.Refuse,
            MmsOwnedRootMutation.authorizeIncomingDelete(false, incoming, row)
        )
    }

    @Test fun emptySelectionCannotDelete() {
        val unbound = MmsOwnedRootMutation.Decision.Mutate(emptyList())
        var called = false
        assertFalse(MmsOwnedRootMutation.applyDelete(unbound, { called = true; 1 }, { null }))
        assertFalse(called)
    }

    private companion object {
        const val PACKAGE = "com.sentinel.quantum"
        const val TRANSACTION = "txn-001"
    }
}
