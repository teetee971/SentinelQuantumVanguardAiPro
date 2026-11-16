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

    @Test fun resolvesSharedFrenchCaribbeanCallingZoneWithoutInventingLocation() {
        val entry = PhoneCountryPrefixCatalog.resolveNumber("+590690316875")
        assertNotNull(entry)
        assertEquals("+590", entry!!.prefix)
        assertTrue(entry.label.contains("Guadeloupe"))
        assertTrue(entry.label.contains("Saint-Martin"))
    }

    @Test fun resolvesSpecificNanpTerritoryBeforeGenericPlusOne() {
        val puertoRico = PhoneCountryPrefixCatalog.resolveNumber("+17875551234")
        assertNotNull(puertoRico)
        assertEquals("+1787", puertoRico!!.prefix)
        assertEquals("Porto Rico", puertoRico.label)
    }

    @Test fun unknownPrefixHasNoInventedGeographicLabel() {
        assertEquals(null, PhoneCountryPrefixCatalog.find("+999"))
    }

    @Test fun searchUsesFrenchNamesAndCallingCodes() {
        val guadeloupe = PhoneCountryPrefixCatalog.search("Guadeloupe", 20)
        assertTrue(guadeloupe.any { it.prefix == "+590" })

        val france = PhoneCountryPrefixCatalog.search("+33", 20)
        assertTrue(france.any { it.label == "France" && it.prefix == "+33" })
    }

    @Test fun searchIsAccentInsensitive() {
        val reunion = PhoneCountryPrefixCatalog.search("reunion", 20)
        assertTrue(reunion.any { it.prefix == "+262" })
    }

    @Test fun frequentEntriesAreBackedByFullCatalog() {
        assertTrue(PhoneCountryPrefixCatalog.frequentEntries.isNotEmpty())
        PhoneCountryPrefixCatalog.frequentEntries.forEach { entry ->
            assertEquals(entry, PhoneCountryPrefixCatalog.find(entry.prefix))
        }
    }

    @Test fun blankSearchIsBounded() {
        assertEquals(5, PhoneCountryPrefixCatalog.search("", 5).size)
    }
}
