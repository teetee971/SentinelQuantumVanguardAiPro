package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhatsAppClickToChatPolicyTest {
    @Test fun buildsOfficialLinkForInternationalNumber() {
        assertEquals(
            "https://wa.me/33612345678",
            WhatsAppClickToChatPolicy.urlFor("+33 6 12 34 56 78")
        )
    }

    @Test fun refusesNationalOnlyOrInteractiveCodes() {
        assertNull(WhatsAppClickToChatPolicy.urlFor("06 12 34 56 78"))
        assertNull(WhatsAppClickToChatPolicy.urlFor("*123#"))
    }

    @Test fun refusesExtensionsAndMalformedInternationalNumbers() {
        assertNull(WhatsAppClickToChatPolicy.urlFor("+33 6 12 34 56 78 ext 2"))
        assertNull(WhatsAppClickToChatPolicy.urlFor("++33612345678"))
        assertNull(WhatsAppClickToChatPolicy.urlFor("+0123456789"))
    }
}
