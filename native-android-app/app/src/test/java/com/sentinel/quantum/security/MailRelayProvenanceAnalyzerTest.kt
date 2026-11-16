package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MailRelayProvenanceAnalyzerTest {
    @Test fun extractsPublicRelayAsIndicativeInfrastructure() {
        val report = MailRelayProvenanceAnalyzer.analyze(listOf("from mail.example (mail.example [8.8.8.8]) by mx.local"))
        assertEquals("mail.example", report.hops.single().fromHost)
        assertEquals("mx.local", report.hops.single().byHost)
        assertEquals(listOf("8.8.8.8"), report.hops.single().observablePublicIps)
        assertEquals(MailRelayProvenanceAnalyzer.Confidence.INDICATIVE, report.hops.single().confidence)
    }

    @Test fun filtersPrivateAndDocumentationIpv4() {
        val report = MailRelayProvenanceAnalyzer.analyze(listOf("from x [10.0.0.1] by y [192.0.2.10]"))
        assertTrue(report.hops.single().observablePublicIps.isEmpty())
    }

    @Test fun acceptsPublicIpv6AndRejectsDocumentationIpv6() {
        val report = MailRelayProvenanceAnalyzer.analyze(listOf("from x [2606:4700:4700::1111] by y [2001:db8::1]"))
        assertEquals(1, report.hops.single().observablePublicIps.size)
    }

    @Test fun boundsUntrustedChain() {
        val report = MailRelayProvenanceAnalyzer.analyze((0 until 25).map { "from h$it [8.8.8.8] by mx" })
        assertEquals(EmailHeaderAnalyzer.MAX_RECEIVED_HOPS, report.hops.size)
        assertTrue(report.truncated)
    }
}
