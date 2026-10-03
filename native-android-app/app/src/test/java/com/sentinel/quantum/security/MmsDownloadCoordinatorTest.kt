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
}
