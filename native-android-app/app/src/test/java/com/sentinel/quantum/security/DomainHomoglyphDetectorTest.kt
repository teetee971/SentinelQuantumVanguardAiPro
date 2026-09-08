package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainHomoglyphDetectorTest {
    @Test fun exactTrustedDomainIsNotSuspicious() {
        val risk = DomainHomoglyphDetector.lookalikeRisk("google.com")

        assertFalse(risk.suspicious)
        assertEquals(LookalikeLevel.LOW, risk.level)
    }

    @Test fun detectsSimpleHomoglyph() {
        val risk = DomainHomoglyphDetector.lookalikeRisk("g00gle.com")

        assertTrue(risk.suspicious)
        assertEquals(LookalikeLevel.HIGH, risk.level)
    }

    @Test fun detectsSuspiciousSubdomainAndTld() {
        val subdomain = DomainHomoglyphDetector.lookalikeRisk("anssi.gouv-login.fr")
        val tld = DomainHomoglyphDetector.lookalikeRisk("anssi-gouv.tk")

        assertTrue(subdomain.suspicious)
        assertTrue(subdomain.level == LookalikeLevel.MEDIUM || subdomain.level == LookalikeLevel.HIGH)
        assertTrue(tld.suspicious)
        assertEquals(LookalikeLevel.HIGH, tld.level)
    }
}
