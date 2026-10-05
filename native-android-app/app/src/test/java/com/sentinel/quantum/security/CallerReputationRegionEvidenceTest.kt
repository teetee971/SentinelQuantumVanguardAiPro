package com.sentinel.quantum.security

import org.junit.Assert.fail
import org.junit.Test

class CallerReputationRegionEvidenceTest {
    @Test fun acceptsOnlyMatchingObservedRegion() {
        CallerReputationClient.requireRecipientRegionEvidence("GP") { "gp" }
    }

    @Test fun rejectsMissingObservedRegion() {
        assertSecurity("PHONE_CORE_RECIPIENT_REGION_UNKNOWN") {
            CallerReputationClient.requireRecipientRegionEvidence("GP") { null }
        }
    }

    @Test fun rejectsUiOrHardcodedRegionThatDisagreesWithTelephony() {
        assertSecurity("PHONE_CORE_RECIPIENT_REGION_MISMATCH") {
            CallerReputationClient.requireRecipientRegionEvidence("FR") { "GP" }
        }
    }

    private fun assertSecurity(expected: String, block: () -> Unit) {
        try {
            block()
            fail("SecurityException expected")
        } catch (error: SecurityException) {
            if (error.message != expected) {
                fail("Expected $expected but was ${error.message}")
            }
        }
    }
}
