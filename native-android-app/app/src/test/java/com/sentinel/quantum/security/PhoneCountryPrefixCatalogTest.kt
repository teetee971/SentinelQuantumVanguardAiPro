package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneCountryPrefixCatalogTest {
    @Test fun everyShortcutUsesACanonicalInternationalPrefix() {
        PhoneCountryPrefixCatalog.frequentEntries.forEach { entry ->
            assertTrue(entry.label.isNotBlank())
            assertEquals(entry.prefix, CallRuleEngine.normalizePrefix(entry.prefix))
        }
    }

    @Test fun frenchCaribbeanShortcutIsPresentWithoutClaimingUniqueLocation() {
        val entry = PhoneCountryPrefixCatalog.find("+590")
        assertNotNull(entry)
        assertTrue(entry!!.label.contains("Guadeloupe"))
        assertTrue(entry.label.contains("Saint-Martin"))
    }

    @Test fun unknownPrefixHasNoInventedGeographicLabel() {
        assertEquals(null, PhoneCountryPrefixCatalog.find("+999"))
    }
}
