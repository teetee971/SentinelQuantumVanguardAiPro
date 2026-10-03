package com.sentinel.quantum

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneCoreCapabilityFactsTest {
    private fun facts(
        call: Boolean = true,
        phoneState: Boolean = true,
        contacts: Boolean = true,
        notifications: Boolean = true,
        dialer: Boolean = true,
        dialerAvailable: Boolean = true,
        screening: Boolean = true,
        screeningAvailable: Boolean = true,
        callLog: Boolean = true,
        smsRole: Boolean = true,
        smsAvailable: Boolean = true,
        smsPermissions: Boolean = true,
        mms: Boolean = true,
        channels: Boolean = true
    ) = PhoneCoreRuntimeFacts.CapabilityFacts(
        callPermissionGranted = call,
        phoneStatePermissionGranted = phoneState,
        contactsPermissionGranted = contacts,
        notificationPermissionGranted = notifications,
        dialerRoleHeld = dialer,
        dialerRoleAvailable = dialerAvailable,
        callScreeningRoleHeld = screening,
        callScreeningRoleAvailable = screeningAvailable,
        callLogPermissionGranted = callLog,
        smsRoleHeld = smsRole,
        smsRoleAvailable = smsAvailable,
        smsRuntimePermissionsReady = smsPermissions,
        mmsPermissionsReady = mms,
        notificationChannelsReady = channels
    )

    @Test
    fun callControl_doesNotDependOnContactsNotificationsOrHistory() {
        val facts = facts(contacts = false, notifications = false, callLog = false, channels = false)
        assertTrue(facts.callControlReady)
        assertFalse(facts.contactsReady)
        assertFalse(facts.callHistoryReady)
    }

    @Test
    fun callControl_requiresPhonePermissionsDialerAndScreening() {
        assertFalse(facts(call = false).callControlReady)
        assertFalse(facts(phoneState = false).callControlReady)
        assertFalse(facts(dialer = false).callControlReady)
        assertFalse(facts(screening = false).callControlReady)
        assertTrue(facts().callControlReady)
    }

    @Test
    fun messaging_isIndependentFromCallAndContactCapabilities() {
        assertTrue(facts(call = false, phoneState = false, contacts = false, screening = false).messagingReady)
        assertFalse(facts(smsRole = false).messagingReady)
        assertFalse(facts(smsPermissions = false).messagingReady)
        assertFalse(facts(mms = false).messagingReady)
    }
}
