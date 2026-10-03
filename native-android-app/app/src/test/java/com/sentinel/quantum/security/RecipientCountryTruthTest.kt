package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipientCountryTruthTest {
    @Test
    fun country_normalization_acceptsOnlyExplicitIsoAlpha2() {
        assertEquals("FR", CallerReputationClient.normalizeRecipientCountry(" fr "))
        assertEquals("CA", CallerReputationClient.normalizeRecipientCountry("ca"))
        assertNull(CallerReputationClient.normalizeRecipientCountry(""))
        assertNull(CallerReputationClient.normalizeRecipientCountry("F"))
        assertNull(CallerReputationClient.normalizeRecipientCountry("FRA"))
        assertNull(CallerReputationClient.normalizeRecipientCountry("12"))
    }
}
