package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CallerIdentityResolverTest {
    @Test
    fun `normalizes French national number and identifies mobile`() {
        val result = CallerIdentityResolver.resolve("06 12 34 56 78", "Non vérifié")
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
    fun `normalizes overseas national prefixes before geographic resolution`() {
        val guadeloupeZone = CallerIdentityResolver.resolve("0590 12 34 56", "Non vérifié")
        assertEquals("+590123456", guadeloupeZone.displayNumber)
        assertEquals("GPBLMF", guadeloupeZone.countryIsoCode)
        assertEquals("+594123456", CallerIdentityResolver.normalize("0594 12 34 56"))
        assertEquals("+596123456", CallerIdentityResolver.normalize("0596 12 34 56"))
        assertEquals("+262123456", CallerIdentityResolver.normalize("0262 12 34 56"))
        assertEquals("+262269123456", CallerIdentityResolver.normalize("0269 12 34 56"))
    }

    @Test
    fun `does not invent an identity`() {
        val result = CallerIdentityResolver.resolve("+442071838750", "Non vérifié")
        assertNull(result.displayName)
        assertNull(result.organisation)
        assertFalse(result.identityVerified)
    }

    @Test
    fun `classifies French service number`() {
        val result = CallerIdentityResolver.resolve("08 99 12 34 56", "Échec")
        assertEquals("Service / tarification à vérifier", result.callType)
    }
}
