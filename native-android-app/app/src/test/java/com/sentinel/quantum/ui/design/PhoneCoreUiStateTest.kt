package com.sentinel.quantum.ui.design

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneCoreUiStateTest {
    @Test fun unavailableWins() {
        assertEquals(
            SentinelState.UNAVAILABLE,
            PhoneCoreUiState.derive(false, 0, 14, available = false)
        )
    }

    @Test fun blockedWinsOverReadiness() {
        assertEquals(
            SentinelState.BLOCKED,
            PhoneCoreUiState.derive(true, 14, 14, explicitlyBlocked = true)
        )
    }

    @Test fun missingSoftwareNeverClaimsReady() {
        assertEquals(
            SentinelState.TO_CONFIGURE,
            PhoneCoreUiState.derive(false, 14, 14)
        )
    }

    @Test fun softwareReadyWithoutPhysicalProofIsReady() {
        assertEquals(
            SentinelState.READY,
            PhoneCoreUiState.derive(true, 0, 14)
        )
    }

    @Test fun partialPhysicalProofRemainsToTest() {
        assertEquals(
            SentinelState.TO_TEST,
            PhoneCoreUiState.derive(true, 8, 14)
        )
    }

    @Test fun onlyFourteenOfFourteenIsValidated() {
        assertEquals(
            SentinelState.VALIDATED,
            PhoneCoreUiState.derive(true, 14, 14)
        )
    }
    @Test fun thirteenOfFourteenCannotClaimValidated() {
        assertEquals(
            SentinelState.TO_TEST,
            PhoneCoreUiState.derive(true, 13, 14)
        )
    }
    @Test fun unknownHasDistinctNonMeasuredLabel() {
        assertEquals("Non mesuré", PhoneCoreUiState.label(SentinelState.UNKNOWN))
        assertEquals("Non disponible", PhoneCoreUiState.label(SentinelState.UNAVAILABLE))
    }

    @Test fun phoneCoreHeadlinesFollowTruthState() {
        assertEquals("Configuration Phone Core incomplète", PhoneCoreUiState.phoneCoreHeadline(SentinelState.TO_CONFIGURE))
        assertEquals("Phone Core prêt pour les tests", PhoneCoreUiState.phoneCoreHeadline(SentinelState.READY))
        assertEquals("Validation locale en cours", PhoneCoreUiState.phoneCoreHeadline(SentinelState.TO_TEST))
        assertEquals("Phone Core validé sur cet appareil", PhoneCoreUiState.phoneCoreHeadline(SentinelState.VALIDATED))
    }

}
