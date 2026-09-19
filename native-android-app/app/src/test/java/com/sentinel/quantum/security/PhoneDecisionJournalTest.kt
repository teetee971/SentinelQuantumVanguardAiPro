package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneDecisionJournalTest {
    @Test fun sanitizesFreeFormFields() {
        val now = 1_000_000L
        val result = PhoneDecisionJournal.sanitize(
            PhoneDecisionJournal.Entry(now, PhoneDecisionJournal.Action.WARN, "SHORT_RING<script>", SentinelConfidence.INDICATIVE, "local/rule", true),
            now
        )!!
        assertEquals("SHORT_RINGscript", result.ruleCode)
        assertEquals("localrule", result.provenance)
    }

    @Test fun toleratesSmallClockSkewButRejectsImplausibleFutureTimestamp() {
        val now = 1_000_000L
        val tolerated = PhoneDecisionJournal.sanitize(
            PhoneDecisionJournal.Entry(now + 60_000L, PhoneDecisionJournal.Action.ALLOW, "RULE", SentinelConfidence.UNKNOWN, "LOCAL", true),
            now
        )
        assertEquals(now + 60_000L, tolerated?.timestampMs)
        assertNull(PhoneDecisionJournal.sanitize(
            PhoneDecisionJournal.Entry(now + 5L * 60L * 1000L + 1L, PhoneDecisionJournal.Action.ALLOW, "RULE", SentinelConfidence.UNKNOWN, "LOCAL", true),
            now
        ))
    }
}
