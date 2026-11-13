package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkInspectionActivityStateTest {
    @Test fun successClearsLoading() {
        val done = ApkInspectionActivityState().started().completed()
        assertFalse(done.loading)
        assertNull(done.error)
    }

    @Test fun failedInspectionClearsLoadingAndShowsError() {
        val failed = ApkInspectionActivityState().started().failed("Fichier non lisible")
        assertFalse(failed.loading)
        assertEquals("Fichier non lisible", failed.error)
    }

    @Test fun cancellationClearsLoadingWithoutFakeFailure() {
        val cancelled = ApkInspectionActivityState().started().cancelled()
        assertFalse(cancelled.loading)
        assertNull(cancelled.error)
    }

    @Test fun restartingClearsPreviousError() {
        val retry = ApkInspectionActivityState().failed("ancienne erreur").started()
        assertTrue(retry.loading)
        assertNull(retry.error)
    }
}
