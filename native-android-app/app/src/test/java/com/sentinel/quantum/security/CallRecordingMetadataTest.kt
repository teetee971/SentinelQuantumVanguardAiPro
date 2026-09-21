package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test

class CallRecordingMetadataTest {
    @Test fun noteAndTagsAreBounded() {
        val note = CallRecordingMetadata.sanitizeNote("  note   privée  ")
        val tags = CallRecordingMetadata.sanitizeTags(listOf(" important ", "important", "travail"))
        assertEquals("note privée", note)
        assertEquals(setOf("important", "travail"), tags)
    }

    @Test fun retentionExpiresAtBoundary() {
        val start = 1_000L
        val expiry = CallRecordingRetention.expiresAt(start, CallRecordingRetention.Policy.HOURS_24)!!
        assertFalse(CallRecordingRetention.isExpired(start, CallRecordingRetention.Policy.HOURS_24, expiry - 1))
        assertTrue(CallRecordingRetention.isExpired(start, CallRecordingRetention.Policy.HOURS_24, expiry))
    }

    @Test fun keepNeverExpires() {
        assertNull(CallRecordingRetention.expiresAt(0, CallRecordingRetention.Policy.KEEP))
        assertFalse(CallRecordingRetention.isExpired(0, CallRecordingRetention.Policy.KEEP, Long.MAX_VALUE))
    }

    @Test fun expiryDoesNotOverflow() {
        assertEquals(
            Long.MAX_VALUE,
            CallRecordingRetention.expiresAt(Long.MAX_VALUE - 1, CallRecordingRetention.Policy.DAYS_30)
        )
    }
}
