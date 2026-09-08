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
            Date: Tue, 8 Sep 2026 11:00:00 +0000
            Message-Id: <clean@example.org>
            MIME-Version: 1.0
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

    @Test fun completeOfflineAnalysisFindsHeadersIocsAttachmentsAndLookalikes() {
        val result = analyzer().analyze("""
            From: "Support" <support@example.org>
            To: user@example.org
            Date: Tue, 8 Sep 2026 11:00:00 +0000
            Message-Id: <phish@example.org>
            MIME-Version: 1.0
            Content-Type: multipart/mixed; boundary="x"
            Received: from mx.example.org (198.51.100.1) by mail.local; Tue, 8 Sep 2026 10:00:00 +0000
            Received: from relay.example.org (203.0.113.1) by mx.example.org; Tue, 8 Sep 2026 11:00:00 +0000
            Subject: Urgent confidentiel

            Cliquez immédiatement sur https://g00gle.com/login et https://bit.ly/a
            Contact: fraude@example.net ou +33 6 12 34 56 78.
            Content-Disposition: attachment; filename="facture.exe"
        """.trimIndent())

        assertTrue(result.accepted)
        assertEquals(2, result.headerReport.hopCount)
        assertTrue(result.findings.any { it.code == "RECEIVED_CHAIN_SUSPICIOUS" })
        assertTrue(result.findings.any { it.code == "SHORTENED_URL" })
        assertTrue(result.findings.any { it.code == "LOOKALIKE_DOMAIN" })
        assertTrue(result.findings.any { it.code == "DANGEROUS_ATTACHMENT_TYPE" })
        assertTrue(result.iocReport.urls.contains("https://bit.ly/a"))
        assertTrue(result.iocReport.emailAddresses.contains("fraude@example.net"))
        assertTrue(result.iocReport.phoneNumbers.contains("+33 6 12 34 56 78"))
        assertTrue(result.attachments.any { it.extension == "exe" && it.dangerous })
        assertEquals(EmailSecurityAnalyzer.RiskLevel.HIGH, result.riskLevel)
    }

    @Test fun tooManyReceivedHopsAreRejected() {
        val received = (1..21).joinToString("\n") {
            "Received: from relay$it.example.org (203.0.113.$it) by mx.example.org; Tue, 8 Sep 2026 10:00:00 +0000"
        }
        val result = analyzer().analyze(
            "From: alerts@example.org\n" +
                "Date: Tue, 8 Sep 2026 11:00:00 +0000\n" +
                "Message-Id: <many@example.org>\n" +
                "MIME-Version: 1.0\n" +
                received + "\n" +
                "Subject: Test\n\nTexte"
        )

        assertFalse(result.accepted)
        assertEquals("TOO_MANY_RECEIVED_HOPS", result.reason)
    }

    @Test fun auditDoesNotCopyContents() {
        val logs = mutableListOf<String>()
        analyzer(logs).analyze("From: a@example.org\nSubject: secret-value\n\nprivate-body")
        assertTrue(logs.none { it.contains("secret-value") || it.contains("private-body") })
    }
}
