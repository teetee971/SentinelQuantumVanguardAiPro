package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InternationalNumberResolverTest {
    private val resolver = InternationalNumberResolver()

    @Test
    fun e164AndInternational00InputsNormalizeWithoutARegionalGuess() {
        assertEquals(
            "+33612345678",
            resolver.resolve("+33 6 12 34 56 78").canonicalE164
        )
        assertEquals(
            "+33612345678",
            resolver.resolve("00 33 6 12 34 56 78").canonicalE164
        )
    }

    @Test
    fun nationalNumberRequiresExplicitCallingContext() {
        val resolution = resolver.resolve("06 12 34 56 78")

        assertEquals(
            InternationalNumberResolver.Status.NATIONAL_CONTEXT_REQUIRED,
            resolution.status
        )
        assertNull(resolution.canonicalE164)
    }

    @Test
    fun nationalNumberCanUseExplicitContextOnly() {
        val resolution = resolver.resolve(
            "06 12 34 56 78",
            InternationalNumberResolver.Context(defaultCallingCode = "+33")
        )

        assertEquals(InternationalNumberResolver.Status.NORMALIZED, resolution.status)
        assertEquals("+33612345678", resolution.canonicalE164)
    }

    @Test
    fun sharedCallingCodesRemainAmbiguous() {
        val northAmerican = resolver.resolve("+1 202 555 0123")
        val sharedSeven = resolver.resolve("+7 495 123 4567")
        val frenchIndianOcean = resolver.resolve("+262 262 123 456")

        assertEquals(InternationalNumberResolver.Status.AMBIGUOUS, northAmerican.status)
        assertEquals(InternationalNumberResolver.Status.AMBIGUOUS, sharedSeven.status)
        assertEquals(InternationalNumberResolver.Status.AMBIGUOUS, frenchIndianOcean.status)
        assertEquals(true, northAmerican.sharedCallingCode)
    }

    @Test
    fun specificNANPPrefixIsMorePreciseThanSharedOne() {
        val bahamas = resolver.resolve("+1 242 555 0123")

        assertEquals(InternationalNumberResolver.Status.NORMALIZED, bahamas.status)
        assertEquals("BS", bahamas.matchingEntries.single().isoCode)
    }

    @Test
    fun withheldMalformedAndOverlongNumbersDoNotProduceEvidence() {
        listOf(null, "", "anonymous", "private", "+", "+33<script>").forEach { raw ->
            val result = resolver.resolve(raw)
            assertEquals(InternationalNumberResolver.Status.UNAVAILABLE, result.status)
            assertNull(result.canonicalE164)
        }
        assertEquals(
            InternationalNumberResolver.Status.INVALID,
            resolver.resolve("+" + "1".repeat(16)).status
        )
    }
}
