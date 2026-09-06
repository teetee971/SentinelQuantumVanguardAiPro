package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailSecurityAnalyzerTest {
    private fun analyzer(logs: MutableList<String> = mutableListOf()) =
        EmailSecurityAnalyzer({ _, _, message -> logs += message }, { 1234L })

    @Test fun cleanObservedAuthenticationRemainsLowRisk() {
        val result = analyzer().analyze("""
            From: alerts@example.org
            Return-Path: bounce@example.org
            Authentication-Results: mx; spf=pass; dkim=pass; dmarc=pass
            Subject: Information

            Consultez https://example.org/status
        """.trimIndent())
        assertTrue(result.accepted)
        assertEquals(EmailSecurityAnalyzer.RiskLevel.LOW, result.riskLevel)
        assertTrue(result.observedAuthenticationResults)
    }

    @Test fun authenticationFailuresProduceHighRisk() {
        val result = analyzer().analyze("""
            From: direction@example.org
            Authentication-Results: mx; spf=fail; dkim=fail; dmarc=fail
            Subject: Message

            Texte
        """.trimIndent())
        assertEquals(EmailSecurityAnalyzer.RiskLevel.HIGH, result.riskLevel)
        assertTrue(result.findings.any { it.code == "DMARC_OBSERVED_FAIL" })
    }

    @Test fun mismatchesAndMisleadingLinkAreDetected() {
        val result = analyzer().analyze("""
            From: finance@example.org
            Reply-To: payment@attacker.example
            Subject: Urgent confidentiel

            Cliquez immédiatement sur http://trusted.example@192.0.2.10/pay
        """.trimIndent())
        assertEquals(EmailSecurityAnalyzer.RiskLevel.HIGH, result.riskLevel)
        assertTrue(result.findings.any { it.code == "REPLY_TO_DOMAIN_MISMATCH" })
        assertTrue(result.findings.any { it.code == "MISLEADING_LINK_USERINFO" })
        assertTrue(result.findings.any { it.code == "IP_LITERAL_LINK" })
    }

    @Test fun oversizedMessageIsRejected() {
        val result = analyzer().analyze("A".repeat(256 * 1024 + 1))
        assertFalse(result.accepted)
        assertEquals("MESSAGE_TOO_LARGE", result.reason)
    }

    @Test fun auditDoesNotCopyContents() {
        val logs = mutableListOf<String>()
        analyzer(logs).analyze("From: a@example.org\nSubject: secret-value\n\nprivate-body")
        assertTrue(logs.none { it.contains("secret-value") || it.contains("private-body") })
    }
}
