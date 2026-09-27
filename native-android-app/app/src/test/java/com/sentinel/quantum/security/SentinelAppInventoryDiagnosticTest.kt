package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class SentinelAppInventoryDiagnosticTest {
    @Test
    fun visiblePackagesAreNeverClaimedAsCompleteInventory() {
        val result = SentinelAppInventoryDiagnostic.evaluate(
            SentinelAppInventoryDiagnostic.Snapshot(
                visiblePackageCount = 42,
                scope = SentinelAppInventoryDiagnostic.Scope.VISIBLE_PACKAGES_ONLY,
                collectionSucceeded = true,
                observedAtEpochMillis = 1L
            )
        )
        assertEquals(SentinelDeviceDiagnostic.Status.NOT_ACCESSIBLE, result.status)
        assertEquals("42", result.observedValue)
    }

    @Test
    fun emptyVisibleSetStillDoesNotProveDeviceIsClean() {
        val result = SentinelAppInventoryDiagnostic.evaluate(
            SentinelAppInventoryDiagnostic.Snapshot(
                visiblePackageCount = 0,
                scope = SentinelAppInventoryDiagnostic.Scope.VISIBLE_PACKAGES_ONLY,
                collectionSucceeded = true,
                observedAtEpochMillis = 1L
            )
        )
        assertEquals(SentinelDeviceDiagnostic.Status.NOT_ACCESSIBLE, result.status)
    }

    @Test
    fun collectionFailureIsUnknown() {
        val result = SentinelAppInventoryDiagnostic.evaluate(
            SentinelAppInventoryDiagnostic.Snapshot(
                visiblePackageCount = 0,
                scope = SentinelAppInventoryDiagnostic.Scope.VISIBLE_PACKAGES_ONLY,
                collectionSucceeded = false,
                observedAtEpochMillis = 1L
            )
        )
        assertEquals(SentinelDeviceDiagnostic.Status.UNKNOWN, result.status)
    }

    @Test
    fun onlyExplicitVerifiedCompleteScopeCanBeOk() {
        val result = SentinelAppInventoryDiagnostic.evaluate(
            SentinelAppInventoryDiagnostic.Snapshot(
                visiblePackageCount = 100,
                scope = SentinelAppInventoryDiagnostic.Scope.COMPLETE_BY_VERIFIED_PRIVILEGE,
                collectionSucceeded = true,
                observedAtEpochMillis = 1L
            )
        )
        assertEquals(SentinelDeviceDiagnostic.Status.OK, result.status)
    }
}
