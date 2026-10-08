package com.sentinel.quantum

import android.app.AppOpsManager
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
}
