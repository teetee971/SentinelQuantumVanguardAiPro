package com.sentinel.quantum.ui.design

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneCoreUiStateTest {
    @Test fun unavailableWins() {
        assertEquals(
            SentinelState.UNAVAILABLE,
            PhoneCoreUiState.derive(false, 0, 13, available = false)
        )
    }

    @Test fun blockedWinsOverReadiness() {
        assertEquals(
            SentinelState.BLOCKED,
            PhoneCoreUiState.derive(true, 13, 13, explicitlyBlocked = true)
        )
    }

    @Test fun missingSoftwareNeverClaimsReady() {
        assertEquals(
            SentinelState.TO_CONFIGURE,
            PhoneCoreUiState.derive(false, 13, 13)
        )
    }

    @Test fun softwareReadyWithoutPhysicalProofIsReady() {
        assertEquals(
            SentinelState.READY,
            PhoneCoreUiState.derive(true, 0, 13)
        )
    }

    @Test fun partialPhysicalProofRemainsToTest() {
        assertEquals(
            SentinelState.TO_TEST,
            PhoneCoreUiState.derive(true, 7, 13)
        )
    }

    @Test fun onlyThirteenOfThirteenIsValidated() {
        assertEquals(
            SentinelState.VALIDATED,
            PhoneCoreUiState.derive(true, 13, 13)
        )
    }
    @Test fun twelveOfThirteenCannotClaimValidated() {
        assertEquals(
            SentinelState.TO_TEST,
            PhoneCoreUiState.derive(true, 12, 13)
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


    @Test fun completedProofWithUnavailableCarrierEnvironmentIsDegraded() {
        assertEquals(
            SentinelState.DEGRADED,
            PhoneCoreUiState.derive(true, 13, 13, operationalEnvironmentReady = false)
        )
    }

    @Test fun carrierEnvironmentDoesNotReplaceMissingSoftwareOrProof() {
        assertEquals(
            SentinelState.TO_CONFIGURE,
            PhoneCoreUiState.derive(false, 13, 13, operationalEnvironmentReady = true)
        )
        assertEquals(
            SentinelState.TO_TEST,
            PhoneCoreUiState.derive(true, 12, 13, operationalEnvironmentReady = true)
        )
    }

}

