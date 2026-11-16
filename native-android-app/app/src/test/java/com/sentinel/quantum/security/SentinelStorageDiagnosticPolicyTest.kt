package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class SentinelStorageDiagnosticPolicyTest {
    private val gib = 1024L * 1024 * 1024
    private fun snapshot(total: Long?, free: Long?) =
        SentinelStorageSnapshot(total, free, 1L)

    @Test
    fun healthyStorageIsOk() {
        assertEquals(
            SentinelDeviceDiagnostic.Status.OK,
            SentinelStorageDiagnosticPolicy.evaluate(snapshot(128 * gib, 32 * gib)).status
        )
    }

    @Test
    fun lowAbsoluteSpaceWarnsEvenWhenPercentageLooksAcceptable() {
        assertEquals(
            SentinelDeviceDiagnostic.Status.WARNING,
            SentinelStorageDiagnosticPolicy.evaluate(snapshot(32 * gib, 4 * gib)).status
        )
    }

    @Test
    fun criticallyLowAbsoluteSpaceIsCritical() {
        assertEquals(
            SentinelDeviceDiagnostic.Status.CRITICAL,
            SentinelStorageDiagnosticPolicy.evaluate(snapshot(128 * gib, gib)).status
        )
    }

    @Test
    fun criticallyLowPercentageIsCriticalOnLargeStorage() {
        assertEquals(
            SentinelDeviceDiagnostic.Status.CRITICAL,
            SentinelStorageDiagnosticPolicy.evaluate(snapshot(1024 * gib, 40 * gib)).status
        )
    }

    @Test
    fun impossibleMeasurementsAreUnknown() {
        listOf(
            snapshot(null, null),
            snapshot(0, 0),
            snapshot(10 * gib, -1),
            snapshot(10 * gib, 11 * gib)
        ).forEach {
            assertEquals(
                SentinelDeviceDiagnostic.Status.UNKNOWN,
                SentinelStorageDiagnosticPolicy.evaluate(it).status
            )
        }
    }
}
