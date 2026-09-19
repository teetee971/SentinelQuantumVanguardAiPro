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

    @Test fun rejectsFutureTimestamp() {
        val now = 1_000_000L
        assertNull(PhoneDecisionJournal.sanitize(
            PhoneDecisionJournal.Entry(now + 1, PhoneDecisionJournal.Action.ALLOW, "RULE", SentinelConfidence.UNKNOWN, "LOCAL", true),
            now
        ))
    }
}
