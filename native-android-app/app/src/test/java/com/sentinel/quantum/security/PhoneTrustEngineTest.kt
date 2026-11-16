package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneTrustEngineTest {
    @Test fun unknownIsNotConvertedIntoRisk() {
        val result = PhoneTrustEngine.assess(emptyList())
        assertEquals(SentinelConfidence.UNKNOWN, result.confidence)
        assertFalse(result.shouldConfirmBeforeCallback)
    }

    @Test fun twoBehavioralSignalsRequireCallbackConfirmation() {
        val result = PhoneTrustEngine.assess(listOf(
            PhoneEvidence("SHORT_RING", SentinelConfidence.INDICATIVE),
            PhoneEvidence("UNUSUAL_COUNTRY", SentinelConfidence.INDICATIVE)
        ))
        assertTrue(result.shouldConfirmBeforeCallback)
        assertEquals(SentinelConfidence.INDICATIVE, result.confidence)
    }

    @Test fun verifiedEvidenceRemainsExplicit() {
        val result = PhoneTrustEngine.assess(listOf(PhoneEvidence("SIGNED_IDENTITY", SentinelConfidence.VERIFIED)))
        assertEquals(SentinelConfidence.VERIFIED, result.confidence)
    }
}
