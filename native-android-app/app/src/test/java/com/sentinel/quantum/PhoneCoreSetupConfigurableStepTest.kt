package com.sentinel.quantum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PhoneCoreSetupConfigurableStepTest {
    private fun facts(
        core: Boolean = false,
        dialer: Boolean = false,
        dialerAvailable: Boolean = true,
        screening: Boolean = false,
        screeningAvailable: Boolean = true,
        callLog: Boolean = false,
        smsRole: Boolean = false,
        smsRoleAvailable: Boolean = true,
        smsPermissions: Boolean = false,
        mms: Boolean = false,
        notifications: Boolean = false
    ) = PhoneCoreSetupWizardStore.Facts(
        corePermissionsReady = core,
        dialerRoleHeld = dialer,
        dialerRoleAvailable = dialerAvailable,
        callScreeningRoleHeld = screening,
        callScreeningRoleAvailable = screeningAvailable,
        callLogPermissionGranted = callLog,
        smsRoleHeld = smsRole,
        smsRoleAvailable = smsRoleAvailable,
        smsRuntimePermissionsReady = smsPermissions,
        mmsPermissionsReady = mms,
        notificationChannelsReady = notifications
    )

    @Test fun unavailablePhoneRolesDoNotBlockIndependentSmsActivation() {
        val runtime = facts(dialerAvailable = false, screeningAvailable = false)
        assertEquals(
            PhoneCoreSetupWizardStore.Step.SMS_ROLE,
            PhoneCoreSetupWizardStore.nextConfigurableStep(runtime)
        )
        assertFalse(PhoneCoreSetupWizardStore.softwarePrerequisitesReady(runtime))
    }

    @Test fun unavailableDialerNeverUnlocksDialerDependentRuntimePermissions() {
        val runtime = facts(
            dialerAvailable = false,
            screening = true,
            smsRole = true,
            smsPermissions = true,
            mms = true,
            notifications = true
        )
        assertEquals(
            PhoneCoreSetupWizardStore.Step.DIALER_ROLE,
            PhoneCoreSetupWizardStore.nextConfigurableStep(runtime)
        )
        assertFalse(PhoneCoreSetupWizardStore.isStepActionable(PhoneCoreSetupWizardStore.Step.DIALER_ROLE, runtime))
    }

    @Test fun unavailableSmsRoleSkipsSmsAndMmsPermissionsButKeepsTruthBlocked() {
        val runtime = facts(
            core = true,
            dialer = true,
            screening = true,
            callLog = true,
            smsRoleAvailable = false,
            notifications = false
        )
        assertEquals(
            PhoneCoreSetupWizardStore.Step.NOTIFICATION_CHANNELS,
            PhoneCoreSetupWizardStore.nextConfigurableStep(runtime)
        )
        val afterNotifications = runtime.copy(notificationChannelsReady = true)
        assertEquals(
            PhoneCoreSetupWizardStore.Step.SMS_ROLE,
            PhoneCoreSetupWizardStore.nextConfigurableStep(afterNotifications)
        )
        assertFalse(PhoneCoreSetupWizardStore.softwarePrerequisitesReady(afterNotifications))
    }
}
