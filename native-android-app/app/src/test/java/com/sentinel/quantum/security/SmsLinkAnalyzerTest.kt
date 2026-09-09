package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsLinkAnalyzerTest {
    private fun analyzer(logs: MutableList<String> = mutableListOf()) =
        SmsLinkAnalyzer({ _, _, message -> logs += message }, { 1234L })

    @Test fun plainMessageWithoutLinksIsLowRisk() {
        val result = analyzer().analyze("Bonjour, on se voit demain ?")
        assertTrue(result.accepted)
        assertEquals(SmsLinkAnalyzer.RiskLevel.LOW, result.riskLevel)
        assertEquals(0, result.linksInspected)
    }

    @Test fun ipLiteralAndUserInfoLinkAreHighRisk() {
        val result = analyzer().analyze(
            "Votre colis est bloqué, cliquez immédiatement sur http://trusted.example@192.0.2.10/livraison"
        )
        assertEquals(SmsLinkAnalyzer.RiskLevel.HIGH, result.riskLevel)
        assertTrue(result.findings.any { it.code == "IP_LITERAL_LINK" })
        assertTrue(result.findings.any { it.code == "MISLEADING_LINK_USERINFO" })
        assertTrue(result.findings.any { it.code == "CLEARTEXT_LINK" })
        assertTrue(result.findings.any { it.code == "SOCIAL_ENGINEERING_LANGUAGE" })
    }

    @Test fun shortenerAndSuspiciousTldAreDetected() {
        val result = analyzer().analyze("Suivez votre colis: https://bit.ly/abc puis https://track.xyz/id")
        assertTrue(result.findings.any { it.code == "URL_SHORTENER" })
        assertTrue(result.findings.any { it.code == "SUSPICIOUS_TLD" })
        assertTrue(result.findings.any { it.code == "MULTIPLE_LINKS" })
    }

    @Test fun oversizedMessageIsRejected() {
        val result = analyzer().analyze("A".repeat(16 * 1024 + 1))
        assertFalse(result.accepted)
        assertEquals("MESSAGE_TOO_LARGE", result.reason)
    }

    @Test fun emptyMessageIsRejected() {
        val result = analyzer().analyze("   ")
        assertFalse(result.accepted)
        assertEquals("EMPTY_MESSAGE", result.reason)
    }

    @Test fun auditDoesNotCopyContents() {
        val logs = mutableListOf<String>()
        analyzer(logs).analyze("Code secret-value: https://example.org/x")
        assertTrue(logs.none { it.contains("secret-value") })
    }
}
