package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualPrefixPersistencePolicyTest {
    @Test fun explicitInternationalPrefixIsCanonicalizedAndAccepted() {
        assertEquals("+590690", ManualPrefixPersistencePolicy.normalize("+590 690"))
        assertEquals("+590690", ManualPrefixPersistencePolicy.normalize("00590 690"))
        assertEquals("+336", ManualPrefixPersistencePolicy.normalize("+33 6"))
    }

    @Test fun nationalPrefixIsRejectedInsteadOfGuessingCountry() {
        assertNull(ManualPrefixPersistencePolicy.normalize("0690"))
        assertNull(ManualPrefixPersistencePolicy.normalize("06"))
        assertNull(ManualPrefixPersistencePolicy.normalize("01 42"))
    }
}
