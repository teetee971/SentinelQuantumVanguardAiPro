package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class CallFilterLogStoreTest {
    @Test fun maskNumberRedactsMiddleDigitsButKeepsPrefixAndSuffix() {
        assertEquals("+33•••••••78", CallFilterLogStore.maskNumber("+33612345678"))
    }

    @Test fun maskNumberHandlesShortNumbers() {
        assertEquals("••", CallFilterLogStore.maskNumber("12"))
    }

    @Test fun maskNumberHandlesMissingNumber() {
        assertEquals("INCONNU", CallFilterLogStore.maskNumber(null))
        assertEquals("INCONNU", CallFilterLogStore.maskNumber(""))
    }

    @Test fun maskNumberPreservesNonPlusPrefixedDigits() {
        assertEquals("01•••56", CallFilterLogStore.maskNumber("0123456"))
    }
}
