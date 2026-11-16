package com.sentinel.quantum.intelligence

import com.sentinel.quantum.security.SmsDeliveryStatusBus
import com.sentinel.quantum.security.SmsLinkAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class SmsMmsEvidenceAdapterTest {
    @Test
    fun sms_delivery_event_maps_only_bounded_transport_metadata() {
        val event = SmsDeliveryStatusBus.Event(
            sendToken = 41,
            providerMessageId = 123L,
            partIndex = 0,
            partCount = 1,
            stage = SmsDeliveryStatusBus.Stage.DELIVERED,
            successful = true,
            providerWriteSucceeded = true
        )

        val evidence = SmsMmsEvidenceAdapter.fromSmsDelivery(
            event = event,
            direction = IntelligenceEvidence.Direction.OUTGOING
        )

        assertEquals(IntelligenceEvidence.Direction.OUTGOING, evidence.direction)
        assertEquals("DELIVERED_CONFIRMED", evidence.transportState)
        assertEquals(null, evidence.categoryCode)
        assertEquals(null, evidence.analysisOutcome)
        val rendered = evidence.toString()
        assertFalse(rendered.contains("123"))
        assertFalse(rendered.contains("41"))
    }

    @Test
    fun failed_sms_delivery_maps_failure_without_provider_identifiers() {
        val event = SmsDeliveryStatusBus.Event(
            sendToken = 987654,
            providerMessageId = 444L,
            partIndex = 1,
            partCount = 2,
            stage = SmsDeliveryStatusBus.Stage.SENT,
            successful = false,
            providerWriteSucceeded = false
        )

        val evidence = SmsMmsEvidenceAdapter.fromSmsDelivery(event, IntelligenceEvidence.Direction.OUTGOING)

        assertEquals("SENT_FAILED", evidence.transportState)
        assertFalse(evidence.toString().contains("987654"))
        assertFalse(evidence.toString().contains("444"))
    }

    @Test
    fun mms_adapter_accepts_only_bounded_states_and_codes() {
        val evidence = SmsMmsEvidenceAdapter.fromMms(
            direction = IntelligenceEvidence.Direction.INCOMING,
            transportState = SmsMmsEvidenceAdapter.MmsTransportState.LOCAL_QUARANTINE,
            categoryCode = "PHISHING",
            analysisOutcome = SmsMmsEvidenceAdapter.AnalysisOutcome.QUARANTINED
        )

        assertEquals("MMS_LOCAL_QUARANTINE", evidence.transportState)
        assertEquals("PHISHING", evidence.categoryCode)
        assertEquals("QUARANTINED", evidence.analysisOutcome)
    }

    @Test
    fun arbitrary_message_content_cannot_be_smuggled_as_category_code() {
        assertThrows(IllegalArgumentException::class.java) {
            SmsMmsEvidenceAdapter.fromMms(
                direction = IntelligenceEvidence.Direction.INCOMING,
                transportState = SmsMmsEvidenceAdapter.MmsTransportState.SAFE_PREVIEW_READY,
                categoryCode = "Votre code OTP est 123456",
                analysisOutcome = SmsMmsEvidenceAdapter.AnalysisOutcome.SAFE
            )
        }
    }

    @Test
    fun incoming_analysis_maps_risk_without_message_content_or_urls() {
        val analysis = SmsLinkAnalyzer.Analysis(
            accepted = true,
            reason = null,
            riskLevel = SmsLinkAnalyzer.RiskLevel.HIGH,
            score = 75,
            findings = listOf(
                SmsLinkAnalyzer.Finding(
                    code = "SUSPICIOUS_TLD",
                    severity = SmsLinkAnalyzer.Severity.MEDIUM,
                    score = 15
                )
            ),
            analyzedAt = 1234L,
            linksInspected = 1
        )

        val evidence = SmsMmsEvidenceAdapter.fromIncomingAnalysis(analysis)

        assertEquals(IntelligenceEvidence.Direction.INCOMING, evidence.direction)
        assertEquals("SMS_RECEIVED", evidence.transportState)
        assertEquals("SUSPICIOUS_TLD", evidence.categoryCode)
        assertEquals("RISK_HIGH", evidence.analysisOutcome)
        assertFalse(evidence.toString().contains("http"))
    }
}
