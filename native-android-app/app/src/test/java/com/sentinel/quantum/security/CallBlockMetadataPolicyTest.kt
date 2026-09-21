package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallBlockMetadataPolicyTest {
    @Test fun permanentBlockHasNoExpiry() {
        assertNull(CallBlockMetadata.expiresAt(1_000L, CallBlockMetadata.Duration.PERMANENT))
    }

    @Test fun temporaryDurationsExpireAtExpectedBoundary() {
        val now = 1_000L
        val day = CallBlockMetadata.expiresAt(now, CallBlockMetadata.Duration.HOURS_24)!!
        val week = CallBlockMetadata.expiresAt(now, CallBlockMetadata.Duration.DAYS_7)!!
        val month = CallBlockMetadata.expiresAt(now, CallBlockMetadata.Duration.DAYS_30)!!
        assertEquals(now + 24L * 60 * 60 * 1000, day)
        assertEquals(now + 7L * 24 * 60 * 60 * 1000, week)
        assertEquals(now + 30L * 24 * 60 * 60 * 1000, month)
    }

    @Test fun entryBecomesInactiveExactlyAtExpiry() {
        val entry = CallBlockMetadata.Entry("fingerprint", "Wangiri", 1_000L, 2_000L, CallBlockMetadata.Origin.MANUAL)
        assertTrue(entry.isActive(1_999L))
        assertFalse(entry.isActive(2_000L))
    }

    @Test fun reasonIsWhitespaceNormalizedAndBounded() {
        val raw = "  signalement   communautaire\n" + "x".repeat(200)
        val clean = CallBlockMetadata.sanitizeReason(raw)
        assertFalse(clean.contains("\n"))
        assertFalse(clean.contains("  "))
        assertTrue(clean.length <= 120)
    }

    @Test fun expirySaturatesInsteadOfOverflowing() {
        assertEquals(
            Long.MAX_VALUE,
            CallBlockMetadata.expiresAt(Long.MAX_VALUE - 10L, CallBlockMetadata.Duration.DAYS_30)
        )
    }
}
