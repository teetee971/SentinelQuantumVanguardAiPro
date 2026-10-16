package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class MmsDownloadCleanupWorkerTest {
    @Test
    fun normalTimestampKeepsOriginalCleanupDeadline() {
        val nowMs = 1_000_000L
        val requestedAtMs = nowMs - 1_000L

        assertEquals(
            MmsDownloadCoordinator.DOWNLOAD_TTL_MS - 1_000L,
            MmsDownloadCleanupWorker.remainingDelayMs(requestedAtMs, nowMs)
        )
    }

    @Test
    fun expiredTimestampSchedulesImmediateCleanup() {
        val nowMs = MmsDownloadCoordinator.DOWNLOAD_TTL_MS + 10_000L

        assertEquals(
            0L,
            MmsDownloadCleanupWorker.remainingDelayMs(1_000L, nowMs)
        )
    }

    @Test
    fun futureTimestampCannotExtendCleanupBeyondOneTtlFromNow() {
        val nowMs = 1_000_000L
        val requestedAtMs = nowMs + (10L * MmsDownloadCoordinator.DOWNLOAD_TTL_MS)

        assertEquals(
            MmsDownloadCoordinator.DOWNLOAD_TTL_MS,
            MmsDownloadCleanupWorker.remainingDelayMs(requestedAtMs, nowMs)
        )
    }
}
