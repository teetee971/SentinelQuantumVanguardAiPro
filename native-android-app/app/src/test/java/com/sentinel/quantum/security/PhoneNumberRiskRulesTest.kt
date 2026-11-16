package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberRiskRulesTest {
    @Test fun frenchPremiumRatePrefixesAreRecognizedAcrossFormatting() {
        assertTrue(PhoneNumberRiskRules.isKnownPremiumRatePrefix("+33 8 99 12 34 56"))
        assertTrue(PhoneNumberRiskRules.isKnownPremiumRatePrefix("0033897123456"))
    }

    @Test fun ordinaryFrenchNumberIsNotMarkedPremium() {
        assertFalse(PhoneNumberRiskRules.isKnownPremiumRatePrefix("+33 6 12 34 56 78"))
    }

    @Test fun assistedRiskDoesNotInventWangiriEvidence() {
        assertEquals(
            FamilySafetyPolicy.Risk.NONE,
            PhoneNumberRiskRules.assistedRisk("+33612345678")
        )
        assertEquals(
            FamilySafetyPolicy.Risk.PREMIUM_RATE_CALLBACK,
            PhoneNumberRiskRules.assistedRisk("+33899123456")
        )
    }
}
