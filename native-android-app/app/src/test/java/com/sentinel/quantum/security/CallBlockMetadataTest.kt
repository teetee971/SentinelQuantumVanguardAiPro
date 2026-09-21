package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallBlockMetadataTest {
    @Test fun permanentBlockHasNoExpiry() {
        assertNull(CallBlockMetadata.expiresAt(1_000L, CallBlockMetadata.Duration.PERMANENT))
    }

    @Test fun temporaryBlockExpiresDeterministically() {
        val expiry = CallBlockMetadata.expiresAt(1_000L, CallBlockMetadata.Duration.HOURS_24)!!
        val entry = CallBlockMetadata.Entry("fingerprint", "Wangiri", 1_000L, expiry, CallBlockMetadata.Origin.MANUAL)
        assertTrue(entry.isActive(expiry - 1L))
        assertFalse(entry.isActive(expiry))
    }

    @Test fun reasonIsBoundedAndNormalized() {
        val value = CallBlockMetadata.sanitizeReason("  signalement   utilisateur  ")
        assertEquals("signalement utilisateur", value)
        assertTrue(CallBlockMetadata.sanitizeReason("x".repeat(500)).length <= CallBlockMetadata.MAX_REASON_CHARS)
    }

    @Test fun expiryOverflowFailsClosedAtMaxLong() {
        assertEquals(Long.MAX_VALUE, CallBlockMetadata.expiresAt(Long.MAX_VALUE - 1L, CallBlockMetadata.Duration.DAYS_30))
    }
}
