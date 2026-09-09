package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailHeaderAnalyzerTest {
    @Test fun detectsReceivedTimestampInversionAndMissingHeaders() {
        val headers = EmailHeaderAnalyzer.parseHeaders("""
            From: "Support" <support@example.net>
            Received: from mx.example.net (198.51.100.10) by mail.local; Tue, 8 Sep 2026 10:00:00 +0000
            Received: from relay.example.net (203.0.113.10) by mx.example.net; Tue, 8 Sep 2026 11:00:00 +0000
            Subject: Test
        """.trimIndent())!!

        val report = EmailHeaderAnalyzer.analyze(headers)

        assertEquals(2, report.hopCount)
        assertTrue(report.anomalies.any { it.code == "RECEIVED_TIMESTAMP_INVERSION" })
        assertTrue(report.anomalies.any { it.code == "MISSING_DATE" })
        assertTrue(report.anomalies.any { it.code == "MISSING_MESSAGE_ID" })
        assertTrue(report.anomalies.none { it.code == "MISSING_MIME_VERSION" })
        assertTrue(report.anomalies.any { it.code == "SUSPICIOUS_FROM_DISPLAY_NAME" })
    }

    @Test fun acceptsNormalPrivateToPublicRelayChain() {
        val headers = EmailHeaderAnalyzer.parseHeaders("""
            From: sender@example.net
            Date: Tue, 8 Sep 2026 11:00:00 +0000
            Message-Id: <a@example.net>
            MIME-Version: 1.0
            Received: from gateway.example.net (198.51.100.10) by mail.local; Tue, 8 Sep 2026 11:00:00 +0000
            Received: from workstation.local (10.0.0.4) by gateway.example.net; Tue, 8 Sep 2026 10:59:00 +0000
        """.trimIndent())!!

        val report = EmailHeaderAnalyzer.analyze(headers)

        assertTrue(report.anomalies.none { it.code == "RECEIVED_PRIVATE_PUBLIC_CHAIN" })
    }

    @Test fun requiresMimeVersionOnlyWhenMimeContentIsDeclared() {
        val plain = EmailHeaderAnalyzer.parseHeaders("From: a@example.org\nDate: Tue, 8 Sep 2026 11:00:00 +0000\nMessage-Id: <a@example.org>")!!
        val mime = plain + ("content-type" to listOf("multipart/mixed; boundary=x"))

        assertTrue(EmailHeaderAnalyzer.analyze(plain).anomalies.none { it.code == "MISSING_MIME_VERSION" })
        assertTrue(EmailHeaderAnalyzer.analyze(mime).anomalies.any { it.code == "MISSING_MIME_VERSION" })
    }
}
