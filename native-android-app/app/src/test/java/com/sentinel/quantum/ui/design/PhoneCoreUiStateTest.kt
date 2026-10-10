package com.sentinel.quantum.ui.design

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneCoreUiStateTest {
    @Test fun unavailableWins() {
        assertEquals(
            SentinelState.LOCKED,
            PhoneCoreUiState.derive(false, 0, 14, available = false)
        )
    }

    @Test fun blockedWinsOverReadiness() {
        assertEquals(
            SentinelState.LOCKED,
            PhoneCoreUiState.derive(true, 14, 14, explicitlyBlocked = true)
        )
    }

    @Test fun missingSoftwareNeverClaimsReady() {
        assertEquals(
            SentinelState.LOCKED,
            PhoneCoreUiState.derive(false, 14, 14)
        )
    }

    @Test fun softwareReadyWithoutPhysicalProofIsLimited() {
        assertEquals(
            SentinelState.LIMITED,
            PhoneCoreUiState.derive(true, 0, 14)
        )
    }

    @Test fun partialPhysicalProofRemainsLimited() {
        assertEquals(
            SentinelState.LIMITED,
            PhoneCoreUiState.derive(true, 7, 14)
        )
    }

    @Test fun completePhysicalProofIsReady() {
        assertEquals(
            SentinelState.READY,
            PhoneCoreUiState.derive(true, 14, 14, physicalDeviceValidated = true)
        )
    }

    @Test fun completeLocalCertificateWithoutPhysicalProofRemainsLimited() {
        assertEquals(
            SentinelState.LIMITED,
            PhoneCoreUiState.derive(true, 14, 14, physicalDeviceValidated = false)
        )
    }
    @Test fun thirteenOfFourteenRemainsLimited() {
        assertEquals(
            SentinelState.LIMITED,
            PhoneCoreUiState.derive(true, 13, 14)
        )
    }
    @Test fun statusVocabularyIsStrict() {
        assertEquals("Prêt", PhoneCoreUiState.label(SentinelState.READY))
        assertEquals("Limité", PhoneCoreUiState.label(SentinelState.LIMITED))
        assertEquals("Bloqué", PhoneCoreUiState.label(SentinelState.LOCKED))
    }

    @Test fun phoneCoreHeadlinesFollowTruthState() {
        assertEquals("Phone Core prêt", PhoneCoreUiState.phoneCoreHeadline(SentinelState.READY))
        assertEquals("Phone Core limité", PhoneCoreUiState.phoneCoreHeadline(SentinelState.LIMITED))
        assertEquals("Phone Core bloqué", PhoneCoreUiState.phoneCoreHeadline(SentinelState.LOCKED))
    }


    @Test fun completedProofWithUnavailableCarrierEnvironmentIsLimited() {
        assertEquals(
            SentinelState.LIMITED,
            PhoneCoreUiState.derive(
                true,
                14,
                14,
                physicalDeviceValidated = true,
                operationalEnvironmentReady = false
            )
        )
    }

    @Test fun carrierEnvironmentDoesNotReplaceMissingSoftwareOrProof() {
        assertEquals(
            SentinelState.LOCKED,
            PhoneCoreUiState.derive(false, 14, 14, operationalEnvironmentReady = true)
        )
        assertEquals(
            SentinelState.LIMITED,
            PhoneCoreUiState.derive(true, 13, 14, operationalEnvironmentReady = true)
        )
    }

}
