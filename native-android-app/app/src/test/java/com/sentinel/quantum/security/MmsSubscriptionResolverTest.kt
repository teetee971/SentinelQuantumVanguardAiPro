package com.sentinel.quantum.security

import android.telephony.SubscriptionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MmsSubscriptionResolverTest {
    @Test
    fun subscriptionNumericCoercionAcceptsIntAndLongWithoutTruncation() {
        assertEquals(2, MmsSubscriptionResolver.subscriptionIdFromNumber(2))
        assertEquals(7, MmsSubscriptionResolver.subscriptionIdFromNumber(7L))
        assertEquals(
            SubscriptionManager.INVALID_SUBSCRIPTION_ID,
            MmsSubscriptionResolver.subscriptionIdFromNumber(-1L)
        )
        assertEquals(
            SubscriptionManager.INVALID_SUBSCRIPTION_ID,
            MmsSubscriptionResolver.subscriptionIdFromNumber(Int.MAX_VALUE.toLong() + 1L)
        )
    }

    @Test
    fun slotNumericCoercionRejectsNegativeAndOverflowValues() {
        assertEquals(0, MmsSubscriptionResolver.slotIndexFromNumber(0))
        assertEquals(3, MmsSubscriptionResolver.slotIndexFromNumber(3L))
        assertNull(MmsSubscriptionResolver.slotIndexFromNumber(-1))
        assertNull(MmsSubscriptionResolver.slotIndexFromNumber(Int.MAX_VALUE.toLong() + 1L))
        assertNull(MmsSubscriptionResolver.slotIndexFromNumber(null))
    }
}
