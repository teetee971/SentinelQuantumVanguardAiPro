package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test

class E164CallingCodeDirectoryTest {
    @Test fun resolvesLongestNanpPrefixBeforeSharedZone() {
        val americanSamoa = E164CallingCodeDirectory.resolve("+16845551234")
        assertEquals("AS", americanSamoa?.isoCode)
        assertFalse(americanSamoa!!.shared)
        val unknownNanp = E164CallingCodeDirectory.resolve("+12025550123")
        assertEquals("NANP", unknownNanp?.isoCode)
        assertTrue(unknownNanp!!.shared)
    }

    @Test fun preservesFrenchOverseasCodes() {
        assertEquals("GPBLMF", E164CallingCodeDirectory.resolve("+590690123456")?.isoCode)
        assertEquals("GF", E164CallingCodeDirectory.resolve("+594694123456")?.isoCode)
        assertEquals("MQ", E164CallingCodeDirectory.resolve("+596696123456")?.isoCode)
    }

    @Test fun marksSharedAndGlobalCodesAsNonCountryTruth() {
        assertTrue(E164CallingCodeDirectory.resolve("+71234567890")!!.shared)
        assertTrue(E164CallingCodeDirectory.resolve("+881612345678")!!.shared)
        assertTrue(E164CallingCodeDirectory.resolve("+883510012345")!!.shared)
    }

    @Test fun containsBroadGlobalCoverage() {
        assertTrue(E164CallingCodeDirectory.all().size > 200)
        assertEquals("AF", E164CallingCodeDirectory.resolve("+93123456789")?.isoCode)
        assertEquals("ZW", E164CallingCodeDirectory.resolve("+263771234567")?.isoCode)
    }
}
