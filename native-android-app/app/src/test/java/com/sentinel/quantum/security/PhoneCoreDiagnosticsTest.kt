package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneCoreDiagnosticsTest {
    private fun readyFacts(
        dialerRoleHeld: Boolean = true,
        callScreeningRoleHeld: Boolean = true,
        smsRoleHeld: Boolean = true,
        callPermissionGranted: Boolean = true,
        sendSmsPermissionGranted: Boolean = true,
        readSmsPermissionGranted: Boolean = true,
        receiveSmsPermissionGranted: Boolean = true,
        notificationsReady: Boolean = true,
        contactsPermissionGranted: Boolean = true,
        callLogPermissionGranted: Boolean = true,
        activeSimVerified: Boolean = true,
        receiveMmsPermissionGranted: Boolean = true,
        receiveWapPushPermissionGranted: Boolean = true,
        mmsSafePreviewValidated: Boolean = true,
        physicalDeviceValidated: Boolean = true
    ) = PhoneCoreDiagnostics.RuntimeFacts(
        dialerRoleHeld = dialerRoleHeld,
        callScreeningRoleHeld = callScreeningRoleHeld,
        smsRoleHeld = smsRoleHeld,
        callPermissionGranted = callPermissionGranted,
        sendSmsPermissionGranted = sendSmsPermissionGranted,
        readSmsPermissionGranted = readSmsPermissionGranted,
        receiveSmsPermissionGranted = receiveSmsPermissionGranted,
        notificationsReady = notificationsReady,
        contactsPermissionGranted = contactsPermissionGranted,
        callLogPermissionGranted = callLogPermissionGranted,
        activeSimVerified = activeSimVerified,
        receiveMmsPermissionGranted = receiveMmsPermissionGranted,
        receiveWapPushPermissionGranted = receiveWapPushPermissionGranted,
        mmsSafePreviewValidated = mmsSafePreviewValidated,
        physicalDeviceValidated = physicalDeviceValidated
    )

    @Test fun doesNotClaimReadyWithoutRuntimePrerequisites() {
        val r = PhoneCoreDiagnostics.readiness(readyFacts(
            dialerRoleHeld = false,
            callScreeningRoleHeld = false,
            smsRoleHeld = false,
            callPermissionGranted = false,
            sendSmsPermissionGranted = false,
            readSmsPermissionGranted = false,
            receiveSmsPermissionGranted = false,
            notificationsReady = false,
            contactsPermissionGranted = false,
            callLogPermissionGranted = false,
            activeSimVerified = false,
            mmsSafePreviewValidated = false,
            physicalDeviceValidated = false
        ))
        assertEquals(PhoneCoreDiagnostics.State.LOCKED, r.capabilities.first { it.id == "DIALER" }.state)
        assertEquals(PhoneCoreDiagnostics.State.LOCKED, r.capabilities.first { it.id == "MMS_ATTACHMENTS" }.state)
        assertEquals(PhoneCoreDiagnostics.State.LIMITED, r.capabilities.first { it.id == "PHYSICAL_DEVICE" }.state)
        assertFalse(r.softwarePrerequisitesReady)
        assertFalse(r.fullyValidated)
    }

    @Test fun softwareReadyDoesNotClaimPhysicalValidation() {
        val r = PhoneCoreDiagnostics.readiness(readyFacts(physicalDeviceValidated = false))
        assertTrue(r.softwarePrerequisitesReady)
        assertFalse(r.physicalDeviceValidated)
        assertFalse(r.fullyValidated)
        assertEquals(PhoneCoreDiagnostics.State.LIMITED, r.capabilities.first { it.id == "PHYSICAL_DEVICE" }.state)
    }

    @Test fun fullValidationRequiresSoftwareAndPhysicalDevice() {
        val r = PhoneCoreDiagnostics.readiness(readyFacts())
        assertTrue(r.softwarePrerequisitesReady)
        assertTrue(r.physicalDeviceValidated)
        assertTrue(r.fullyValidated)
        assertTrue(r.capabilities.all { it.state == PhoneCoreDiagnostics.State.READY })
    }

    @Test fun activeSimIsRequiredForSmsSendReadiness() {
        val r = PhoneCoreDiagnostics.readiness(readyFacts(activeSimVerified = false))
        assertEquals(PhoneCoreDiagnostics.State.LIMITED, r.capabilities.first { it.id == "SMS_SEND" }.state)
        assertFalse(r.softwarePrerequisitesReady)
        assertFalse(r.fullyValidated)
    }

    @Test fun receiveSmsPermissionIsRequiredForConversationReadiness() {
        val r = PhoneCoreDiagnostics.readiness(readyFacts(receiveSmsPermissionGranted = false))
        assertEquals(PhoneCoreDiagnostics.State.LIMITED, r.capabilities.first { it.id == "SMS_CONVERSATIONS" }.state)
        assertFalse(r.softwarePrerequisitesReady)
    }

    @Test fun receiveMmsPermissionIsRequiredForMmsReadiness() {
        val r = PhoneCoreDiagnostics.readiness(readyFacts(receiveMmsPermissionGranted = false))
        assertEquals(PhoneCoreDiagnostics.State.LOCKED, r.capabilities.first { it.id == "MMS_ATTACHMENTS" }.state)
        assertFalse(r.softwarePrerequisitesReady)
    }

    @Test fun receiveWapPushPermissionIsRequiredForMmsReadiness() {
        val r = PhoneCoreDiagnostics.readiness(readyFacts(receiveWapPushPermissionGranted = false))
        assertEquals(PhoneCoreDiagnostics.State.LOCKED, r.capabilities.first { it.id == "MMS_ATTACHMENTS" }.state)
        assertFalse(r.softwarePrerequisitesReady)
    }

    @Test fun notificationsAreFirstClassSoftwareReadiness() {
        val r = PhoneCoreDiagnostics.readiness(readyFacts(notificationsReady = false))
        assertEquals(PhoneCoreDiagnostics.State.LOCKED, r.capabilities.first { it.id == "NOTIFICATIONS" }.state)
        assertFalse(r.softwarePrerequisitesReady)
    }
}
