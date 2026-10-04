package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class GlobalPhoneIdentityPolicyTest {
    @Test fun acceptsExplicitInternationalAndCanonicalizesDoubleZero() {
        assertEquals("+590690123456", GlobalPhoneIdentityPolicy.canonicalE164OrNull("+590 690 12 34 56"))
        assertEquals("+33612345678", GlobalPhoneIdentityPolicy.canonicalE164OrNull("0033 6 12 34 56 78"))
    }

    @Test fun rejectsNationalSyntaxInsteadOfGuessingCountry() {
        assertNull(GlobalPhoneIdentityPolicy.canonicalE164OrNull("0690123456"))
        assertNull(GlobalPhoneIdentityPolicy.canonicalE164OrNull("0612345678"))
        assertThrowsIllegalArgument { GlobalPhoneIdentityPolicy.requireCanonicalE164("0690123456") }
    }

    @Test fun regionIsExplicitLocaleStableAndNeverDefaultsToFrance() {
        assertEquals("GP", GlobalPhoneIdentityPolicy.canonicalRegionIsoOrNull(" gp "))
        assertEquals("IN", GlobalPhoneIdentityPolicy.canonicalRegionIsoOrNull("in"))
        assertNull(GlobalPhoneIdentityPolicy.canonicalRegionIsoOrNull(""))
        assertNull(GlobalPhoneIdentityPolicy.canonicalRegionIsoOrNull("FRA"))
        assertThrowsIllegalArgument { GlobalPhoneIdentityPolicy.requireRegionIso("") }
    }

    private fun assertThrowsIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("IllegalArgumentException expected")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
