package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsTimelineMapperTest {
    @Test fun mapsOnlyBoundedMetadata() {
        val analysis = SmsLinkAnalyzer.Analysis(true, null, SmsLinkAnalyzer.RiskLevel.HIGH, 70,
            listOf(SmsLinkAnalyzer.Finding("SUSPICIOUS_TLD", SmsLinkAnalyzer.Severity.MEDIUM, 15)), 1234L, 1)
        val event = requireNotNull(SmsTimelineMapper.toEvent(analysis))
        assertEquals(PhonePrivateTimeline.Kind.SMS, event.kind)
        assertTrue(event.signal.orEmpty().contains("RISK_HIGH"))
        assertTrue(event.signal.orEmpty().contains("SUSPICIOUS_TLD"))
        assertFalse(event.toString().contains("https://"))
    }

    @Test fun rejectedAnalysisCreatesNoTimelineEvent() {
        val analysis = SmsLinkAnalyzer.Analysis(false, "EMPTY_MESSAGE", SmsLinkAnalyzer.RiskLevel.UNKNOWN, 0, emptyList(), 1234L, 0)
        assertNull(SmsTimelineMapper.toEvent(analysis))
    }
}
