package com.sentinel.quantum.ui.design

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneCoreUiStateTest {
    @Test fun unavailableWins() {
        assertEquals(
            SentinelState.UNAVAILABLE,
            PhoneCoreUiState.derive(false, 0, available = false)
        )
    }

    @Test fun blockedWinsOverReadiness() {
        assertEquals(
            SentinelState.BLOCKED,
            PhoneCoreUiState.derive(true, 13, explicitlyBlocked = true)
        )
    }

    @Test fun missingSoftwareNeverClaimsReady() {
        assertEquals(
            SentinelState.TO_CONFIGURE,
            PhoneCoreUiState.derive(false, 13)
        )
    }

    @Test fun softwareReadyWithoutPhysicalProofIsToTest() {
        assertEquals(
            SentinelState.TO_TEST,
            PhoneCoreUiState.derive(true, 0)
        )
    }

    @Test fun partialPhysicalProofRemainsToTest() {
        assertEquals(
            SentinelState.TO_TEST,
            PhoneCoreUiState.derive(true, 8)
        )
    }

    @Test fun onlyThirteenOfThirteenIsValidated() {
        assertEquals(
            SentinelState.VALIDATED,
            PhoneCoreUiState.derive(true, 13)
        )
    }
}
