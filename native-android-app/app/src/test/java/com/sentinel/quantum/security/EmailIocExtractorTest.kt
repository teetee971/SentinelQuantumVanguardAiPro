package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailIocExtractorTest {
    @Test fun extractsBoundedSanitizedIndicators() {
        val raw = """
            From: user@example.org
            To: me@example.com
            Subject: Alerte

            Voir https://bit.ly/a et www.example.net/path.
            Contact fraude@evil.test ou +33 6 12 34 56 78.
            IPs: 203.0.113.7 10.0.0.8 2001:db8::1
        """.trimIndent()

        val report = EmailIocExtractor.extract(raw)

        assertTrue(report.urls.contains("https://bit.ly/a"))
        assertTrue(report.urls.contains("www.example.net/path"))
        assertTrue(report.ipAddresses.contains("203.0.113.7"))
        assertTrue(report.ipAddresses.contains("2001:db8::1"))
        assertFalse(report.ipAddresses.contains("10.0.0.8"))
        assertTrue(report.emailAddresses.contains("fraude@evil.test"))
        assertFalse(report.emailAddresses.contains("user@example.org"))
        assertFalse(report.emailAddresses.contains("me@example.com"))
        assertTrue(report.phoneNumbers.contains("+33 6 12 34 56 78"))
        assertTrue(report.hasPotentialPrivateIps)
        assertTrue(report.hasShortenedUrls)
    }

    @Test fun limitsEachIocType() {
        val urls = (1..250).joinToString(" ") { "https://example$it.test" }
        val report = EmailIocExtractor.extract("Subject: x\n\n$urls")

        assertEquals(200, report.urls.size)
    }
}
