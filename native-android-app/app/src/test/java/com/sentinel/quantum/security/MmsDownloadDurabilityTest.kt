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
        assertFalse(decoded.transportFailed)
        val again = MmsDownloadJournalMutation.decodeSchema1(
            fileName = fileName,
            raw = MmsDownloadJournalMutation.encode(decoded),
            fileNameAcceptable = true,
            subscriptionAcceptable = { it > 0 }
        )
        assertEquals(decoded, again)
    }

    @Test
    fun knownTransportFailureSurvivesRereadAndCannotAcquireADigest() {
        val failed = snapshot().copy(transportFailed = true)
        val restored = MmsDownloadJournalMutation.decodeSchema1(
            fileName = fileName,
            raw = MmsDownloadJournalMutation.encode(failed),
            fileNameAcceptable = true,
            subscriptionAcceptable = { it == 1 }
        )
        assertEquals(failed, restored)
        assertEquals(
            MmsDownloadJournalMutation.DigestCommit.TRANSPORT_FAILED,
            MmsDownloadJournalMutation.decideDigest(restored, fileName, 1, 10L, digestA)
        )
    }

    @Test
    fun anExistingVerifiedDigestTakesPrecedenceOverAFailureFlag() {
        val durable = snapshot(digestHex = digestA)
        val raw = MmsDownloadJournalMutation.encode(durable).dropLast(1) + ",\"transport_failed\":true}"
        val restored = MmsDownloadJournalMutation.decodeSchema1(
            fileName = fileName,
            raw = raw,
            fileNameAcceptable = true,
            subscriptionAcceptable = { it == 1 }
        )
        assertEquals(durable, restored)
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

    @Test
    fun unreadableJournalKeepsStagingBytesAndEndsOnlyWhenNothingIsThere() {
        assertEquals(
            MmsDownloadRecovery.Outcome.DIAGNOSE,
            MmsDownloadDurability.outcomeWhenJournalUnreadable(stagingBytesPresent = true)
        )
        assertEquals(
            MmsDownloadRecovery.Outcome.TERMINAL,
            MmsDownloadDurability.outcomeWhenJournalUnreadable(stagingBytesPresent = false)
        )
        assertTrue(MmsDownloadRecovery.cleanupMustKeepStaging(MmsDownloadRecovery.Outcome.DIAGNOSE))
    }

    @Test
    fun stabilityIgnoresWallClockAndRequiresTwoMatchingMonotonicObservations() {
        assertFalse(
            MmsStagingStability.ready(
                previousSize = null,
                previousModifiedAtMs = null,
                previousElapsedMs = null,
                size = 12L,
                modifiedAtMs = 50L,
                elapsedMs = 1_000_000L
            )
        )
        assertFalse(
            MmsStagingStability.ready(
                previousSize = 12L,
                previousModifiedAtMs = 50L,
                previousElapsedMs = 1_000L,
                size = 12L,
                modifiedAtMs = 50L,
                elapsedMs = 1_000L + MmsDownloadRecovery.STABLE_FILE_GRACE_MS - 1L
            )
        )
        assertFalse(
            MmsStagingStability.ready(
                previousSize = 12L,
                previousModifiedAtMs = 50L,
                previousElapsedMs = 5_000L,
                size = 13L,
                modifiedAtMs = 50L,
                elapsedMs = 5_000L + MmsDownloadRecovery.STABLE_FILE_GRACE_MS
            )
        )
        assertFalse(
            MmsStagingStability.ready(
                previousSize = 12L,
                previousModifiedAtMs = 50L,
                previousElapsedMs = 9_000L,
                size = 12L,
                modifiedAtMs = 50L,
                elapsedMs = 8_000L
            )
        )
        assertTrue(
            MmsStagingStability.ready(
                previousSize = 12L,
                previousModifiedAtMs = 50L,
                previousElapsedMs = 1_000L,
                size = 12L,
                modifiedAtMs = 50L,
                elapsedMs = 1_000L + MmsDownloadRecovery.STABLE_FILE_GRACE_MS
            )
        )
        val encoded = MmsStagingStability.encode(12L, 50L, 1_000L)
        assertEquals(Triple(12L, 50L, 1_000L), MmsStagingStability.decode(encoded))
        assertNull(MmsStagingStability.decode("12 50"))
    }

    @Test
    fun corruptOrDiagnosedJournalRowsDoNotConsumeAdmission() {
        assertFalse(
            MmsDownloadJournalAdmission.countsTowardCap(
                decodable = false,
                privateMissCount = 0,
                transportFailed = false
            )
        )
        assertFalse(
            MmsDownloadJournalAdmission.countsTowardCap(
                decodable = true,
                privateMissCount = MmsDownloadMissingCopyPolicy.OBSERVATION_LIMIT,
                transportFailed = false
            )
        )
        assertFalse(
            MmsDownloadJournalAdmission.countsTowardCap(
                decodable = true,
                privateMissCount = 0,
                transportFailed = true
            )
        )
        assertTrue(
            MmsDownloadJournalAdmission.countsTowardCap(
                decodable = true,
                privateMissCount = MmsDownloadMissingCopyPolicy.OBSERVATION_LIMIT - 1,
                transportFailed = false
            )
        )
    }

    @Test
    fun nonemptyStagingIsNotUnlinkedWithoutAPrivateCopyOrATransportFailure() {
        assertFalse(
            MmsStagingDeletion.allowed(
                stagingBytesPresent = true,
                transportFailed = false,
                privateCopyMatches = false
            )
        )
        assertTrue(
            MmsStagingDeletion.allowed(
                stagingBytesPresent = true,
                transportFailed = true,
                privateCopyMatches = false
            )
        )
        assertTrue(
            MmsStagingDeletion.allowed(
                stagingBytesPresent = true,
                transportFailed = false,
                privateCopyMatches = true
            )
        )
        assertTrue(
            MmsStagingDeletion.allowed(
                stagingBytesPresent = false,
                transportFailed = false,
                privateCopyMatches = false
            )
        )
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
