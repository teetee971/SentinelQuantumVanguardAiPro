package com.sentinel.quantum.ui.screens

import com.sentinel.quantum.security.SmsLinkAnalyzer
import com.sentinel.quantum.ui.design.SentinelStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SmsThreatPresentationTest {
    @Test
    fun rejectedAnalysisRemainsUnknown() {
        val analysis = SmsLinkAnalyzer.Analysis(
            accepted = false,
            reason = "EMPTY_MESSAGE",
            riskLevel = SmsLinkAnalyzer.RiskLevel.UNKNOWN,
            score = 0,
            findings = emptyList(),
            analyzedAt = 0L,
            linksInspected = 0
        )

        val presentation = SmsThreatPresentation.from(analysis)

        assertEquals(SentinelStatus.UNKNOWN, presentation.status)
        assertEquals("ANALYSE NON DISPONIBLE", presentation.label)
    }

    @Test
    fun lowRiskDoesNotClaimAbsoluteSafety() {
        val analysis = SmsLinkAnalyzer.Analysis(
            accepted = true,
            reason = null,
            riskLevel = SmsLinkAnalyzer.RiskLevel.LOW,
            score = 0,
            findings = emptyList(),
            analyzedAt = 1L,
            linksInspected = 0
        )

        val presentation = SmsThreatPresentation.from(analysis)

        assertEquals(SentinelStatus.UNKNOWN, presentation.status)
        assertEquals("AUCUN SIGNAL DÉTECTÉ", presentation.label)
    }

    @Test
    fun mediumAndHighRiskMapToWarningAndDanger() {
        val medium = SmsLinkAnalyzer.Analysis(
            accepted = true,
            reason = null,
            riskLevel = SmsLinkAnalyzer.RiskLevel.MEDIUM,
            score = 20,
            findings = listOf(SmsLinkAnalyzer.Finding("URL_SHORTENER", SmsLinkAnalyzer.Severity.MEDIUM, 20)),
            analyzedAt = 1L,
            linksInspected = 1
        )
        val high = SmsLinkAnalyzer.Analysis(
            accepted = true,
            reason = null,
            riskLevel = SmsLinkAnalyzer.RiskLevel.HIGH,
            score = 50,
            findings = listOf(SmsLinkAnalyzer.Finding("IP_LITERAL_LINK", SmsLinkAnalyzer.Severity.HIGH, 30)),
            analyzedAt = 1L,
            linksInspected = 1
        )

        assertEquals(SentinelStatus.WARN, SmsThreatPresentation.from(medium).status)
        assertEquals(SentinelStatus.BAD, SmsThreatPresentation.from(high).status)
    }
}
