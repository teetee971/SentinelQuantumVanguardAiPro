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
        val created = 1_000L
        assertEquals(created + 24L * 60L * 60L * 1000L, CallBlockMetadata.expiresAt(created, CallBlockMetadata.Duration.HOURS_24))
        assertEquals(created + 7L * 24L * 60L * 60L * 1000L, CallBlockMetadata.expiresAt(created, CallBlockMetadata.Duration.DAYS_7))
        assertEquals(created + 30L * 24L * 60L * 60L * 1000L, CallBlockMetadata.expiresAt(created, CallBlockMetadata.Duration.DAYS_30))
    }

    @Test fun entryBecomesInactiveExactlyAtExpiry() {
        val entry = CallBlockMetadata.Entry("fp", "reason", 100L, 200L, CallBlockMetadata.Origin.MANUAL)
        assertTrue(entry.isActive(199L))
        assertFalse(entry.isActive(200L))
    }

    @Test fun reasonIsWhitespaceNormalizedAndBounded() {
        val raw = "  reason   with\nwhitespace  " + "x".repeat(200)
        val sanitized = CallBlockMetadata.sanitizeReason(raw)
        assertFalse(sanitized.contains("  "))
        assertFalse(sanitized.contains("\n"))
        assertTrue(sanitized.length <= CallBlockMetadata.MAX_REASON_CHARS)
    }

    @Test fun expirySaturatesInsteadOfOverflowing() {
        assertEquals(
            Long.MAX_VALUE,
            CallBlockMetadata.expiresAt(Long.MAX_VALUE - 10L, CallBlockMetadata.Duration.DAYS_30)
        )
    }
}
