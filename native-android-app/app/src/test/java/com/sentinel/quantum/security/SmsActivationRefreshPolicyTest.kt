package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsActivationRefreshPolicyTest {
    @Test
    fun activationAndLifecycleEventsRefreshState() {
        val refreshEvents = listOf(
            SmsActivationRefreshPolicy.Event.INITIAL_LOAD,
            SmsActivationRefreshPolicy.Event.ACTIVITY_RESUMED,
            SmsActivationRefreshPolicy.Event.SMS_ROLE_RESULT,
            SmsActivationRefreshPolicy.Event.RUNTIME_PERMISSION_RESULT,
            SmsActivationRefreshPolicy.Event.USER_RETRY
        )

        refreshEvents.forEach { event ->
            assertTrue(event.name, SmsActivationRefreshPolicy.shouldRefresh(event))
        }
    }

    @Test
    fun unrelatedUiChangesDoNotTriggerTelephonyLookup() {
        assertFalse(
            SmsActivationRefreshPolicy.shouldRefresh(
                SmsActivationRefreshPolicy.Event.UNRELATED_UI_CHANGE
            )
        )
    }
}
