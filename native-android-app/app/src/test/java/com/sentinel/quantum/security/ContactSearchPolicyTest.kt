package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactSearchPolicyTest {
    @Test fun formattedPhoneMatchesCompactQuery() {
        assertTrue(
            ContactSearchPolicy.matches(
                "Alice",
                listOf("+33 6 12-34-56-78"),
                "+3361234"
            )
        )
    }

    @Test fun compactPhoneMatchesFormattedQuery() {
        assertTrue(
            ContactSearchPolicy.matches(
                "Alice",
                listOf("0612345678"),
                "06 12 34"
            )
        )
    }

    @Test fun nameSearchIsCaseInsensitive() {
        assertTrue(ContactSearchPolicy.matches("Élodie Martin", emptyList(), "martin"))
    }

    @Test fun unrelatedQueryDoesNotMatch() {
        assertFalse(ContactSearchPolicy.matches("Alice", listOf("0612345678"), "Bob"))
    }

    @Test fun blankQueryMatchesEveryVisibleContact() {
        assertTrue(ContactSearchPolicy.matches("Sans numéro", emptyList(), "   "))
    }
}
