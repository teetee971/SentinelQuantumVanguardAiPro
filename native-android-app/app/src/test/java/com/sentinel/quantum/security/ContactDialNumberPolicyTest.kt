package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContactDialNumberPolicyTest {
    @Test fun visualSeparatorsAreRemovedForProviderNumbers() {
        assertEquals("+33612345678", ContactDialNumberPolicy.fromProvider("+33 6 12-34.56/78"))
        assertEquals("0612345678", ContactDialNumberPolicy.fromProvider("(06) 12 34 56 78"))
    }

    @Test fun providerServiceCodesRetainDialableCharacters() {
        assertEquals("*#06#", ContactDialNumberPolicy.fromProvider("*#06#"))
        assertEquals("#31#0612345678", ContactDialNumberPolicy.fromProvider("#31# 06 12 34 56 78"))
    }

    @Test fun malformedOrAmbiguousNumbersAreNeverGuessed() {
        listOf(null, "", "()", "+", "*#", "++33 6 12 34 56 78", "06+12345678",
            "06 12; ext 2", "06,12345678", "1-800-FLOWERS", "06 12@1234",
            "1".repeat(33), " ".repeat(129) + "0612345678"
        ).forEach { assertNull(ContactDialNumberPolicy.fromProvider(it)) }
    }

    @Test fun zeroAndInternationalPrefixesArePreserved() {
        assertEquals("0033612345678", ContactDialNumberPolicy.fromProvider("00 33 6 12 34 56 78"))
        assertEquals("0123456789", ContactDialNumberPolicy.fromProvider("01.23.45.67.89"))
    }
}
