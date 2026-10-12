package com.sentinel.quantum.security

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test

class MmsNotificationIdentityTest {
    @Test fun sameContentLocationAndSubscriptionHaveTheSameIdentity() {
        val first = MmsNotificationIdentity.from("https://carrier.example/mms/123", 7)
        val replay = MmsNotificationIdentity.from("https://carrier.example/mms/123", 7)

        assertNotNull(first)
        assertEquals(first, replay)
    }

    @Test fun subscriptionAndLocationArePartOfTheIdentity() {
        val original = MmsNotificationIdentity.from("https://carrier.example/mms/123", 7)

        assertNotEquals(original, MmsNotificationIdentity.from("https://carrier.example/mms/123", 8))
        assertNotEquals(original, MmsNotificationIdentity.from("https://carrier.example/mms/124", 7))
    }

    @Test fun invalidIdentityInputsAreRejected() {
        assertNull(MmsNotificationIdentity.from("", 7))
        assertNull(MmsNotificationIdentity.from("https://carrier.example/mms/123", -1))
    }
}
