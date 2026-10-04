package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CallerIdentityResolverTest {
    @Test
    fun `national number remains country unknown without regional canonicalization`() {
        val result = CallerIdentityResolver.resolve("06 12 34 56 78", "Non vérifié")
        assertEquals("0612345678", result.displayNumber)
        assertEquals("Pays indéterminé", result.countryName)
        assertEquals("ZZ", result.countryIsoCode)
        assertEquals("Type indéterminé", result.callType)
    }

    @Test
    fun `explicit French e164 identifies country and mobile type`() {
        val result = CallerIdentityResolver.resolve("+33 6 12 34 56 78", "Non vérifié")
        assertEquals("+33612345678", result.displayNumber)
        assertEquals("France", result.countryName)
        assertEquals("🇫🇷", result.countryFlag)
        assertEquals("Mobile", result.callType)
    }

    @Test
    fun `does not overclaim a country for shared plus 590 calling code`() {
        val result = CallerIdentityResolver.resolve("+590 690 12 34 56", "Validé")
        assertEquals("Guadeloupe / Saint-Barthélemy / Saint-Martin", result.countryName)
        assertEquals("GPBLMF", result.countryIsoCode)
    }

    @Test
    fun `national overseas prefixes are not assigned a country without observed region`() {
        val guadeloupeZone = CallerIdentityResolver.resolve("0590 12 34 56", "Non vérifié")
        assertEquals("0590123456", guadeloupeZone.displayNumber)
        assertEquals("ZZ", guadeloupeZone.countryIsoCode)
        assertEquals("0594123456", CallerIdentityResolver.normalize("0594 12 34 56"))
        assertEquals("0596123456", CallerIdentityResolver.normalize("0596 12 34 56"))
        assertEquals("0262123456", CallerIdentityResolver.normalize("0262 12 34 56"))
        assertEquals("0269123456", CallerIdentityResolver.normalize("0269 12 34 56"))
    }

    @Test
    fun `does not invent an identity`() {
        val result = CallerIdentityResolver.resolve("+442071838750", "Non vérifié")
        assertNull(result.displayName)
        assertNull(result.organisation)
        assertFalse(result.identityVerified)
    }

    @Test
    fun `classifies service type only when France is explicit`() {
        val national = CallerIdentityResolver.resolve("08 99 12 34 56", "Échec")
        assertEquals("Type indéterminé", national.callType)

        val explicitFrench = CallerIdentityResolver.resolve("+33 8 99 12 34 56", "Échec")
        assertEquals("Service / tarification à vérifier", explicitFrench.callType)
    }
}
