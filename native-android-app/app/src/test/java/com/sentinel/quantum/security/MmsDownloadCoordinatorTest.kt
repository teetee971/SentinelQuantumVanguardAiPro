package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsDownloadCoordinatorTest {
    @Test fun downloadedPduRetentionHasARealPerFileDeadline() {
        assertEquals(24L * 60L * 60L * 1000L, MmsDownloadCoordinator.DOWNLOAD_TTL_MS)
        assertTrue(MmsDownloadCoordinator.DOWNLOAD_TTL_MS > 0L)
        assertFalse(MmsDownloadCleanupWorker.WORK_TAG.isBlank())
    }

    @Test fun lostCallbackRecoveryRunsBeforeFinalCleanupDeadline() {
        assertTrue(MmsDownloadRecoveryWorker.RECOVERY_DELAY_MS > 0L)
        assertTrue(MmsDownloadRecovery.STABLE_FILE_GRACE_MS > 0L)
        assertTrue(
            MmsDownloadRecoveryWorker.RECOVERY_DELAY_MS >
                MmsDownloadRecovery.STABLE_FILE_GRACE_MS
        )
        assertTrue(
            MmsDownloadRecoveryWorker.RECOVERY_DELAY_MS <
                MmsDownloadCoordinator.DOWNLOAD_TTL_MS
        )
        assertFalse(MmsDownloadRecoveryWorker.WORK_TAG.isBlank())
    }

    @Test fun stagedDownloadNamesAreStrictlyUuidBound() {
        assertTrue(
            MmsDownloadCoordinator.isValidStagedFileName(
                "123e4567-e89b-12d3-a456-426614174000.pdu"
            )
        )
        assertFalse(MmsDownloadCoordinator.isValidStagedFileName("../escape.pdu"))
        assertFalse(MmsDownloadCoordinator.isValidStagedFileName("123e4567-e89b-12d3-a456-426614174000.tmp"))
        assertFalse(MmsDownloadCoordinator.isValidStagedFileName("shared.pdu"))
    }

    @Test fun recoveryJournalRejectsNewRecordsAtCapacityButAllowsExistingRefresh() {
        val capacity = MmsDownloadRecoveryJournal.MAX_RECORDS

        assertTrue(MmsDownloadRecoveryJournal.canAcceptRecord(existing = false, activeRecordCount = 0))
        assertTrue(
            MmsDownloadRecoveryJournal.canAcceptRecord(
                existing = false,
                activeRecordCount = capacity - 1
            )
        )
        assertFalse(
            MmsDownloadRecoveryJournal.canAcceptRecord(
                existing = false,
                activeRecordCount = capacity
            )
        )
        assertTrue(
            MmsDownloadRecoveryJournal.canAcceptRecord(
                existing = true,
                activeRecordCount = capacity
            )
        )
        assertFalse(
            MmsDownloadRecoveryJournal.canAcceptRecord(
                existing = true,
                activeRecordCount = -1
            )
        )
    }
}
