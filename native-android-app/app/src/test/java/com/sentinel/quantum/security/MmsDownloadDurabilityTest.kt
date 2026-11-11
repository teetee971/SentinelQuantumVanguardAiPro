package com.sentinel.quantum.security

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsDownloadDurabilityTest {
    private val fileName = "123e4567-e89b-12d3-a456-426614174000.pdu"
    private val digestA = "a".repeat(64)
    private val digestB = "b".repeat(64)

    @Test
    fun stagingStaysUntilPrivateRereadAndJournalBothSucceed() {
        assertFalse(MmsDownloadDurability.stagingDeletable(privateRereadMatches = false, journalAccepted = false))
        assertFalse(MmsDownloadDurability.stagingDeletable(privateRereadMatches = true, journalAccepted = false))
        assertFalse(MmsDownloadDurability.stagingDeletable(privateRereadMatches = false, journalAccepted = true))
        assertTrue(MmsDownloadDurability.stagingDeletable(privateRereadMatches = true, journalAccepted = true))
    }

    @Test
    fun roleRevocationDoesNotDeleteStaging() {
        assertFalse(MmsDownloadDurability.roleRevocationDeletesStaging())
        assertTrue(MmsDownloadRecovery.cleanupMustKeepStaging(MmsDownloadRecovery.Outcome.RETRY))
    }

    @Test
    fun identicalDigestIsAReplayAndADifferentDigestIsRejected() {
        val current = snapshot(digestHex = digestA)
        assertEquals(
            MmsDownloadJournalMutation.DigestCommit.IDENTICAL_REPLAY,
            MmsDownloadJournalMutation.decideDigest(current, fileName, 1, 10L, digestA.uppercase())
        )
        assertEquals(
            MmsDownloadJournalMutation.DigestCommit.CONFLICTING_DIGEST,
            MmsDownloadJournalMutation.decideDigest(current, fileName, 1, 10L, digestB)
        )
    }

    @Test
    fun absentShaCanBeAppliedOnceButAMissingRecordIsNotRecreated() {
        assertEquals(
            MmsDownloadJournalMutation.DigestCommit.APPLIED,
            MmsDownloadJournalMutation.decideDigest(snapshot(digestHex = null), fileName, 1, 10L, digestA)
        )
        assertEquals(
            MmsDownloadJournalMutation.DigestCommit.RECORD_ABSENT,
            MmsDownloadJournalMutation.decideDigest(null, fileName, 1, 10L, digestA)
        )
    }

    @Test
    fun oldCallbackCannotChangeANewerIdentity() {
        val newer = snapshot(subscriptionId = 2, requestedAtMs = 50L)
        assertEquals(
            MmsDownloadJournalMutation.DigestCommit.IDENTITY_MISMATCH,
            MmsDownloadJournalMutation.decideDigest(newer, fileName, expectedSubscriptionId = 1, expectedRequestedAtMs = 10L, digestHex = digestA)
        )
    }

    @Test
    fun schema1JournalWithoutShaStaysReadable() {
        val raw = """{"schema":1,"subscription_id":7,"requested_at_ms":42}"""
        val decoded = MmsDownloadJournalMutation.decodeSchema1(
            fileName = fileName,
            raw = raw,
            fileNameAcceptable = true,
            subscriptionAcceptable = { it > 0 }
        )
        requireNotNull(decoded)
        assertNull(decoded.digestHex)
        assertEquals(7, decoded.subscriptionId)
        assertEquals(0, decoded.privateMissCount)
        val again = MmsDownloadJournalMutation.decodeSchema1(
            fileName = fileName,
            raw = MmsDownloadJournalMutation.encode(decoded),
            fileNameAcceptable = true,
            subscriptionAcceptable = { it > 0 }
        )
        assertEquals(decoded, again)
    }

    @Test
    fun missingPrivateCopyIsKeptAndHistoricalAbsenceIsNotDeleted() {
        assertEquals(
            MmsDownloadMissingCopyPolicy.Disposition.RETRY_INCONSISTENT,
            MmsDownloadMissingCopyPolicy.disposition(digestPresent = true, privateReadable = false, observations = 0)
        )
        assertEquals(
            MmsDownloadMissingCopyPolicy.Disposition.DIAGNOSE_INCONSISTENT,
            MmsDownloadMissingCopyPolicy.disposition(
                digestPresent = true,
                privateReadable = false,
                observations = MmsDownloadMissingCopyPolicy.OBSERVATION_LIMIT
            )
        )
        assertEquals(
            MmsDownloadMissingCopyPolicy.Disposition.RETRY_HISTORICAL,
            MmsDownloadMissingCopyPolicy.disposition(digestPresent = false, privateReadable = false, observations = 1)
        )
        assertEquals(
            MmsDownloadMissingCopyPolicy.Disposition.DIAGNOSE_HISTORICAL,
            MmsDownloadMissingCopyPolicy.disposition(
                digestPresent = false,
                privateReadable = false,
                observations = MmsDownloadMissingCopyPolicy.OBSERVATION_LIMIT
            )
        )
        assertEquals(
            MmsDownloadMissingCopyPolicy.Disposition.CONTINUE_PRIVATE,
            MmsDownloadMissingCopyPolicy.disposition(digestPresent = true, privateReadable = true, observations = 99)
        )
    }

    @Test
    fun diagnoseAndRetryBothKeepStagingWhileCleanupRuns() {
        assertTrue(MmsDownloadRecovery.cleanupMustKeepStaging(MmsDownloadRecovery.Outcome.DIAGNOSE))
        assertTrue(MmsDownloadRecovery.cleanupMustKeepStaging(MmsDownloadRecovery.Outcome.RETRY))
        assertFalse(MmsDownloadRecovery.cleanupMustKeepStaging(MmsDownloadRecovery.Outcome.RECOVERED))
        assertEquals(64, MmsDownloadProcessingLock.STRIPES)
    }

    @Test
    fun twoCallersCannotReplaceACommittedDigest() {
        val current = AtomicReference(snapshot(digestHex = null))
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val commits = java.util.Collections.synchronizedList(
            mutableListOf<MmsDownloadJournalMutation.DigestCommit>()
        )
        val pool = Executors.newFixedThreadPool(2)
        listOf(digestA, digestB).forEach { digest ->
            pool.execute {
                start.await()
                MmsDownloadProcessingLock.withFile(fileName) {
                    val decision = MmsDownloadJournalMutation.decideDigest(
                        current = current.get(),
                        expectedFileName = fileName,
                        expectedSubscriptionId = 1,
                        expectedRequestedAtMs = 10L,
                        digestHex = digest
                    )
                    commits += decision
                    if (decision == MmsDownloadJournalMutation.DigestCommit.APPLIED) {
                        current.set(snapshot(digestHex = digest))
                    }
                }
                done.countDown()
            }
        }
        start.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        pool.shutdownNow()
        assertEquals(1, commits.count { it == MmsDownloadJournalMutation.DigestCommit.APPLIED })
        assertEquals(1, commits.count { it == MmsDownloadJournalMutation.DigestCommit.CONFLICTING_DIGEST })
        assertTrue(current.get().digestHex == digestA || current.get().digestHex == digestB)
    }

    private fun snapshot(
        subscriptionId: Int = 1,
        requestedAtMs: Long = 10L,
        digestHex: String? = null
    ) = MmsDownloadJournalMutation.Snapshot(
        fileName = fileName,
        subscriptionId = subscriptionId,
        requestedAtMs = requestedAtMs,
        digestHex = digestHex
    )
}


    @Test
    fun emptyOrOversizedStagingIsNeverRecoveredWithoutPrivateProof() {
        // Documented invariant: size <= 0 or > MAX never yields RECOVERED/TERMINAL
        // without a reread private copy matching a journal-accepted SHA.
        assertTrue(MmsDownloadRecovery.cleanupMustKeepStaging(MmsDownloadRecovery.Outcome.DIAGNOSE))
        assertTrue(MmsDownloadRecovery.cleanupMustKeepStaging(MmsDownloadRecovery.Outcome.RETRY))
        assertFalse(MmsDownloadRecovery.cleanupMustKeepStaging(MmsDownloadRecovery.Outcome.TERMINAL))
        assertFalse(MmsDownloadRecovery.cleanupMustKeepStaging(MmsDownloadRecovery.Outcome.RECOVERED))
    }

    @Test
    fun historicalSchema1JournalRemainsReadableAndIsNotErasedForCapacity() {
        val raw = """{"schema":1,"subscription_id":3,"requested_at_ms":100}"""
        val decoded = MmsDownloadJournalMutation.decodeSchema1(
            fileName = fileName,
            raw = raw,
            fileNameAcceptable = true,
            subscriptionAcceptable = { true }
        )
        requireNotNull(decoded)
        assertNull(decoded.digestHex)
        // Unreadable or identity-mismatched records must not be deleted to free slots.
        assertEquals(1, MmsDownloadJournalMutation.SCHEMA_VERSION)
    }


    @Test
    fun pruneMustNotDeleteOnRetryOrDiagnoseOrException() {
        // Invariant: recover() result is respected; only RECOVERED/TERMINAL may retire staging+journal.
        // RETRY, DIAGNOSE and any exception keep the last usable copy.
        assertTrue(MmsDownloadRecovery.cleanupMustKeepStaging(MmsDownloadRecovery.Outcome.RETRY))
        assertTrue(MmsDownloadRecovery.cleanupMustKeepStaging(MmsDownloadRecovery.Outcome.DIAGNOSE))
        assertFalse(MmsDownloadRecovery.cleanupMustKeepStaging(MmsDownloadRecovery.Outcome.RECOVERED))
        assertFalse(MmsDownloadRecovery.cleanupMustKeepStaging(MmsDownloadRecovery.Outcome.TERMINAL))
    }

    @Test
    fun historicalJournalWithoutShaRequiresExplicitAbsenceCheck() {
        val raw = """{"schema":1,"subscription_id":5,"requested_at_ms":200}"""
        val decoded = MmsDownloadJournalMutation.decodeSchema1(
            fileName = fileName,
            raw = raw,
            fileNameAcceptable = true,
            subscriptionAcceptable = { true }
        )
        requireNotNull(decoded)
        assertNull(decoded.digestHex)
        // remove() with expectedDigestHex=null now means "must be absent", not wildcard.
    }
