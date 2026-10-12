package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsIngressIdentityTest {
    @Test fun identicalDeliveriesHaveTheSameStableIdentity() {
        val first = SmsIngressIdentity.from(
            address = " +15551234567 ",
            body = "same message",
            sentAtMillis = 1_700_000_000_000L,
            subscriptionId = 7
        )
        val replay = SmsIngressIdentity.from(
            address = "+15551234567",
            body = "same message",
            sentAtMillis = 1_700_000_000_000L,
            subscriptionId = 7
        )

        assertEquals(first, replay)
    }

    @Test fun identityChangesWhenProtocolIdentityChanges() {
        val original = SmsIngressIdentity.from("+15551234567", "same", 10L, 7)

        assertNotEquals(original, SmsIngressIdentity.from("+15551234567", "changed", 10L, 7))
        assertNotEquals(original, SmsIngressIdentity.from("+15551234567", "same", 11L, 7))
        assertNotEquals(original, SmsIngressIdentity.from("+15551234567", "same", 10L, 8))
    }

    @Test fun missingSentTimestampCannotBeUsedForProviderDeduplication() {
        assertNull(SmsIngressIdentity.from("+15551234567", "message", 0L, 7))
    }
}
