package com.sentinel.quantum

import android.app.AppOpsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsActivationAppOpsPolicyTest {
    @Test
    fun authorizationAppOpsIncludeEverySmsReadWriteDependency() {
        assertTrue(SmsActivationAppOpsPolicy.isRelevant(AppOpsManager.OPSTR_SEND_SMS))
        assertTrue(SmsActivationAppOpsPolicy.isRelevant(AppOpsManager.OPSTR_READ_SMS))
        assertTrue(SmsActivationAppOpsPolicy.isRelevant(AppOpsManager.OPSTR_RECEIVE_SMS))
        assertTrue(SmsActivationAppOpsPolicy.isRelevant(AppOpsManager.OPSTR_READ_PHONE_STATE))
        assertFalse(SmsActivationAppOpsPolicy.isRelevant("android:op/UNRELATED"))
    }

    @Test
    fun watcherRegistrationContinuesWhenOneVendorAppOpThrows() {
        val attempted = mutableListOf<String>()
        val registered = SmsActivationWatchRegistrationPolicy.register(
            operations = listOf("send", "vendor-broken", "read")
        ) { operation ->
            attempted += operation
            if (operation == "vendor-broken") throw IllegalArgumentException("unsupported app-op")
        }

        assertEquals(listOf("send", "vendor-broken", "read"), attempted)
        assertEquals(2, registered)
    }
}
