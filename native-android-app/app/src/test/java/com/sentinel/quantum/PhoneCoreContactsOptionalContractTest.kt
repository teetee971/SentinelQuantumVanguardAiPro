package com.sentinel.quantum

import com.sentinel.quantum.security.PhoneCoreDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneCoreContactsOptionalContractTest {
    @Test
    fun contactsDoNotBlockSoftwareReadiness() {
        val readiness = PhoneCoreDiagnostics.readiness(
            PhoneCoreDiagnostics.RuntimeFacts(
                dialerRoleHeld = true,
                callScreeningRoleHeld = true,
                smsRoleHeld = true,
                callPermissionGranted = true,
                readPhoneStatePermissionGranted = true,
                callLineAvailable = true,
                sendSmsPermissionGranted = true,
                readSmsPermissionGranted = true,
                receiveSmsPermissionGranted = true,
                notificationsReady = true,
                contactsPermissionGranted = false,
                callLogPermissionGranted = true,
                activeSimVerified = true,
                receiveMmsPermissionGranted = true,
                receiveWapPushPermissionGranted = true,
                mmsSafePreviewValidated = true,
                physicalDeviceValidated = false
            )
        )

        assertTrue(readiness.softwarePrerequisitesReady)
        assertFalse(readiness.fullyValidated)
        assertEquals(
            PhoneCoreDiagnostics.State.LOCKED,
            readiness.capabilities.first { it.id == "CONTACTS" }.state
        )
    }

    @Test
    fun essentialSetupSkipsContactsAndContinuesToNotificationPermission() {
        val next = PhoneCoreSetupWizardStore.firstMissingPermission(
            listOf(
                "android.permission.CALL_PHONE" to true,
                "android.permission.READ_PHONE_STATE" to true,
                "android.permission.READ_CONTACTS" to false,
                "android.permission.POST_NOTIFICATIONS" to false
            )
        )

        assertEquals("android.permission.POST_NOTIFICATIONS", next)
    }

    @Test
    fun contactsAloneNeverCreateAnEssentialSetupTarget() {
        val next = PhoneCoreSetupWizardStore.firstMissingPermission(
            listOf("android.permission.READ_CONTACTS" to false)
        )

        assertNull(next)
    }
}
