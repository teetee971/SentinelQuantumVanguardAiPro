package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MailShieldPipelineTest {
    private fun analyzer() = EmailSecurityAnalyzer({ _, _, _ -> }, now = { 1L })

    @Test fun rejectsMalformedInputWithoutAnyMailboxAction() {
        val result = MailShieldPipeline.analyze("not-an-email", analyzer())
        assertFalse(result.accepted)
        assertEquals(listOf(MailShieldPipeline.Stage.INGEST), result.completedStages)
        assertEquals(MailShieldPipeline.Disposition.REVIEW, result.disposition)
    }

    @Test fun classifiesDangerousHighRiskAttachmentAsQuarantineCandidateOnly() {
        val raw = """
            From: billing@example.net
            Reply-To: fraud@example.org
            Date: Tue, 8 Sep 2026 11:00:00 +0000
            Message-Id: <a@example.net>
            MIME-Version: 1.0
            Content-Type: multipart/mixed; boundary=x
            Authentication-Results: mx.local; dmarc=fail; spf=fail; dkim=fail

            Content-Disposition: attachment; filename="invoice.exe"
            urgent immédiat cliquez http://198.51.100.4/login
        """.trimIndent()
        val result = MailShieldPipeline.analyze(raw, analyzer())
        assertTrue(result.accepted)
        assertEquals(MailShieldPipeline.Disposition.QUARANTINE_CANDIDATE, result.disposition)
        assertTrue(result.analysis.attachments.any { it.dangerous })
        assertTrue(result.completedStages.contains(MailShieldPipeline.Stage.CLASSIFICATION))
    }

    @Test fun lowRiskMailRemainsNormal() {
        val raw = """
            From: person@example.net
            Date: Tue, 8 Sep 2026 11:00:00 +0000
            Message-Id: <a@example.net>

            Bonjour.
        """.trimIndent()
        val result = MailShieldPipeline.analyze(raw, analyzer())
        assertTrue(result.accepted)
        assertEquals(MailShieldPipeline.Disposition.NORMAL, result.disposition)
    }
}
